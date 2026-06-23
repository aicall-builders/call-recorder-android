package com.callrecorder.app.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.MainActivity
import com.callrecorder.app.R
import com.callrecorder.app.data.local.RecordingStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

/**
 * 등록된 PENDING/FAILED 녹음을 모두 업로드한다.
 * - WiFi 연결 시 자동 트리거
 * - 실패 시 지수 백오프 재시도
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val app = applicationContext as CallRecorderApp
        val repo = app.container.callRepo

        // 모든 카테고리 업로드 (마스킹 정책으로 프라이버시 보호)
        val pending = repo.pendingUploads()
        if (pending.isEmpty()) return@coroutineScope Result.success()

        val total = pending.size
        val done = AtomicInteger(0)
        setForeground(buildForegroundInfo("녹음 업로드 중... (0/$total)"))

        // 동시 업로드 (소형 통화 녹음 다수일 때 체감 속도 개선). 약한 망 고려해 3개로 제한.
        val semaphore = Semaphore(MAX_CONCURRENT)
        val results = pending.map { rec ->
            async {
                semaphore.withPermit {
                    val info = com.callrecorder.app.util.CallLogHelper.lookup(
                        applicationContext, rec.callStartedAtMillis, rec.durationSeconds,
                    )
                    val r = repo.uploadAndProcess(
                        rec,
                        resolvedNumber = info?.number,
                        resolvedName = info?.name,
                    )
                    val finished = done.incrementAndGet()
                    runCatching { setForeground(buildForegroundInfo("업로드 중 ($finished/$total)")) }
                    r.isSuccess
                }
            }
        }.awaitAll()

        if (results.all { it }) Result.success() else Result.retry()
    }

    private fun buildForegroundInfo(text: String): ForegroundInfo {
        val intent = PendingIntent.getActivity(
            applicationContext, 0,
            android.content.Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(applicationContext, CallRecorderApp.CHANNEL_UPLOAD)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("통화 비서")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(intent)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val NOTIF_ID = 4001
        private const val MAX_CONCURRENT = 3
        const val UNIQUE_NAME = "upload_recordings"

        /** 즉시 1회 업로드 시도 (감지 직후 호출) */
        fun enqueueOneShot(context: Context) {
            val req = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, req
            )
        }

        /** 15분 주기 정기 스캔 + 업로드 (앱이 닫혀 있어도 동작) */
        fun enqueuePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<ScanAndUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "periodic_scan", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }
}