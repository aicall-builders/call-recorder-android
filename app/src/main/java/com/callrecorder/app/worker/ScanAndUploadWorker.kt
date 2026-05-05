package com.callrecorder.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.CallCategory
import com.callrecorder.app.data.local.CallClassifier
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus

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
        val storeId = tokenStore.getActiveStore() ?: return Result.success()
        if (tokenStore.getAccessToken().isNullOrBlank()) return Result.success()

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
                    status = RecordingStatus.PENDING,
                    category = category,
                )
            )
        }

        // 3) 업로드 (사용자가 명시적으로 BUSINESS로 분류한 것만)
        // BUSINESS: 업무 통화로 명시 분류됨 → 업로드 OK
        // UNCLASSIFIED: 사용자 분류 대기 → 업로드 보류 (안전 우선)
        // PERSONAL: 개인 통화 → 자동 업로드 안 함
        val allowedCategories = listOf(
            CallCategory.BUSINESS
        )
        val pending = callRepo.pendingUploadsByCategory(allowedCategories)
        android.util.Log.i(TAG, "📋 업로드 큐: ${pending.size}건")

        var successCount = 0
        var failCount = 0

        pending.forEach { rec ->
            android.util.Log.i(TAG,
                "⬆️ 업로드 시작: id=${rec.id}, category=${rec.category}, file=${rec.fileName}")

            try {
                // 파일이 폰에서 삭제됐는지 확인 (가장 흔한 실패 원인)
                val file = java.io.File(rec.filePath)
                if (!file.exists()) {
                    android.util.Log.w(TAG,
                        "⚠️ 파일 없음, 건너뜀: id=${rec.id}, path=${rec.filePath}")
                    callRepo.markAsFailed(rec.id, "파일이 폰에서 삭제됨")
                    failCount++
                    return@forEach
                }

                val r = callRepo.uploadAndProcess(rec)
                if (r.isSuccess) {
                    successCount++
                    android.util.Log.i(TAG, "✅ 업로드 성공: id=${rec.id}")
                } else {
                    failCount++
                    android.util.Log.w(TAG,
                        "❌ 업로드 실패: id=${rec.id}, 에러=${r.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                failCount++
                android.util.Log.e(TAG,
                    "💥 예외 발생: id=${rec.id}, 에러=${e.message}", e)
            }
        }

        android.util.Log.i(TAG,
            "🏁 워커 완료: 성공 ${successCount}건, 실패 ${failCount}건")

        // 무한 RETRY 루프 방지 — 항상 SUCCESS 반환
        // 실패한 건은 DB에 FAILED로 기록되어 다음 사이클(15분 후)에 재시도됨
        return Result.success()
    }
}