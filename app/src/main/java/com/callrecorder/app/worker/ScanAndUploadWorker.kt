package com.callrecorder.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.CallCategory
import com.callrecorder.app.data.local.CallClassifier
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 주기적 스캔 워커 - 15분마다 실행되어
 * 1) 새로 생긴 녹음 파일을 DB에 등록 (자동 카테고리 분류)
 * 2) BUSINESS/UNCLASSIFIED 상태의 PENDING/FAILED 녹음만 업로드
 *    (PERSONAL은 개인정보 보호를 위해 기본 제외)
 */
class ScanAndUploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ScanAndUploadWorker"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as CallRecorderApp
        val scanner = app.container.scanner
        val callRepo = app.container.callRepo
        val tokenStore = app.container.tokenStore

        // 로그인되어 있고 활성 가게가 있을 때만 동작
        if (tokenStore.getAccessToken().isNullOrBlank()) return Result.success()
        val storeId = app.container.storeRepo.ensureActiveStoreId().getOrNull()
            ?: return Result.success()

        // 자동 업로드 설정 (false=수동 승인). 신규 파일 등록 상태를 결정한다.
        val autoUpload = app.isAutoUploadEnabled()
        val newStatus = if (autoUpload) RecordingStatus.PENDING else RecordingStatus.AWAITING_APPROVAL

        // 1) 스캔
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val found = scanner.scan(sinceMillis = sevenDaysAgo)
        android.util.Log.i(TAG, "🔍 스캔 완료: ${found.size}개 파일 발견")

        // 2) DB 등록 (자동 카테고리 분류)
        found.forEach { f ->
            val category = CallClassifier.classify(
                counterpartNumber = f.counterpartNumber,
                fileName = f.file.name
            )

            // 🛡 안전장치: 파일명에 한글/이모지가 있으면 무조건 PERSONAL
            //    (분류 로직 버그가 있어도 개인정보 노출 차단)
            // 🚧 D-5 검증용 임시 비활성화 — 발표 후 복구 필요!
            // val hasNonAscii = f.file.name.any { it.code > 127 }
            // if (hasNonAscii && category != CallCategory.PERSONAL) {
            //     android.util.Log.w(TAG,
            //         "⚠️ 안전장치 발동: '${f.file.name}' → PERSONAL (한글/이모지 포함)")
            //     category = CallCategory.PERSONAL
            // }
            android.util.Log.i(TAG,
                "🚧 안전장치 OFF — '${f.file.name}' → category=$category")

            callRepo.registerLocal(
                RecordingEntity(
                    filePath = f.file.absolutePath,
                    fileName = f.file.name,
                    fileSize = f.file.length(),
                    durationSeconds = f.durationSeconds,
                    callStartedAtMillis = f.callStartedAtMillis,
                    counterpartNumber = f.counterpartNumber,
                    storeId = storeId,
                    status = newStatus,
                    category = category,
                )
            )
        }

        // 3) 업로드 (모든 카테고리)
        // 안드와 웹 통합 정책: 모든 통화를 업로드하되 caller_category 함께 전송
        // 개인정보 보호는 웹/안드 양쪽에서 마스킹으로 처리 (BUSINESS만 원본 표시)
        val allowedCategories = listOf(
            CallCategory.BUSINESS,
            CallCategory.PERSONAL,
            CallCategory.UNCLASSIFIED
        )
        val pending = callRepo.pendingUploadsByCategory(allowedCategories)
        android.util.Log.i(TAG, "📋 업로드 큐: ${pending.size}건")

        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)
        val maxConcurrent = 3

        // 동시 업로드 (3개 제한) — 백그라운드 큐 처리 속도 개선
        coroutineScope {
            val semaphore = Semaphore(maxConcurrent)
            pending.map { rec ->
                async {
                    semaphore.withPermit {
                        android.util.Log.i(TAG,
                            "⬆️ 업로드 시작: id=${rec.id}, category=${rec.category}, file=${rec.fileName}")
                        try {
                            val file = java.io.File(rec.filePath)
                            if (!file.exists()) {
                                android.util.Log.w(TAG,
                                    "⚠️ 파일 없음, 건너뜀: id=${rec.id}, path=${rec.filePath}")
                                callRepo.markAsFailed(rec.id, "파일이 폰에서 삭제됨")
                                failCount.incrementAndGet()
                                return@withPermit
                            }
                            val info = com.callrecorder.app.util.CallLogHelper.lookup(
                                applicationContext, rec.callStartedAtMillis, rec.durationSeconds,
                            )
                            val r = callRepo.uploadAndProcess(
                                rec,
                                resolvedNumber = info?.number,
                                resolvedName = info?.name,
                            )
                            if (r.isSuccess) {
                                successCount.incrementAndGet()
                                android.util.Log.i(TAG, "✅ 업로드 성공: id=${rec.id}")
                            } else {
                                failCount.incrementAndGet()
                                android.util.Log.w(TAG,
                                    "❌ 업로드 실패: id=${rec.id}, 에러=${r.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            failCount.incrementAndGet()
                            android.util.Log.e(TAG,
                                "💥 예외 발생: id=${rec.id}, 에러=${e.message}", e)
                        }
                    }
                }
            }.awaitAll()
        }

        android.util.Log.i(TAG,
            "🏁 워커 완료: 성공 ${successCount.get()}건, 실패 ${failCount.get()}건")

        // 무한 RETRY 루프 방지 — 항상 SUCCESS 반환
        // 실패한 건은 DB에 FAILED로 기록되어 다음 사이클(15분 후)에 재시도됨
        return Result.success()
    }
}
