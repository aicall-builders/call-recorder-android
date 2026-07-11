package com.callrecorder.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.MainActivity
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus
import com.callrecorder.app.util.SafeLog
import com.callrecorder.app.worker.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 포그라운드 서비스 + ContentObserver
 *
 * 새 녹음 파일이 생기면(=MediaStore.Audio가 변경되면) 즉시 스캔 → DB 등록 → 업로드 트리거.
 * 디바운스: 녹음이 끝난 직후 파일 크기가 변할 수 있어 2초 지연 후 처리.
 */
class RecordingObserverService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingJob: Job? = null

    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            Log.d(TAG, "MediaStore changed: $uri")
            // 디바운스 - 마지막 변경 후 2초 후에 처리
            mainHandler.removeCallbacksAndMessages(TOKEN)
            mainHandler.postAtTime({ scanAndUpload() }, TOKEN, android.os.SystemClock.uptimeMillis() + 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        Log.i(TAG, "Observer registered")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(observer)
        pendingJob?.cancel()
        Log.i(TAG, "Observer unregistered")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scanAndUpload() {
        val app = applicationContext as CallRecorderApp
        pendingJob?.cancel()
        pendingJob = scope.launch {
            val storeId = app.container.storeRepo.ensureActiveStoreId().getOrNull() ?: return@launch
            if (app.container.tokenStore.getAccessToken().isNullOrBlank()) return@launch

            // 최근 1시간 내 파일만 검사 (방금 생긴 통화 녹음)
            val oneHourAgo = System.currentTimeMillis() - 60 * 60 * 1000L
            val found = app.container.scanner.scan(sinceMillis = oneHourAgo)
            if (found.isEmpty()) return@launch

            // 자동 업로드 설정 (false=수동 승인 후 업로드)
            val autoUpload = app.isAutoUploadEnabled()
            val newStatus = if (autoUpload) RecordingStatus.PENDING else RecordingStatus.AWAITING_APPROVAL

            var newCount = 0
            found.forEach { f ->
                val id = app.container.callRepo.registerLocal(
                    RecordingEntity(
                        filePath = f.file.absolutePath,
                        fileName = f.file.name,
                        fileSize = f.file.length(),
                        durationSeconds = f.durationSeconds,
                        callStartedAtMillis = f.callStartedAtMillis,
                        counterpartNumber = f.counterpartNumber,
                        storeId = storeId,
                        status = newStatus,
                    )
                )
                if (id > 0) newCount++
            }
            // 자동 모드일 때만 즉시 업로드 트리거. 수동 모드면 승인 화면에서 사용자가 올린다.
            if (autoUpload && newCount > 0) {
                UploadWorker.enqueueOneShot(applicationContext)
            }
            SafeLog.i(TAG, "Detected ${found.size} files (new=$newCount), auto=$autoUpload")
        }
    }

    private fun startForegroundCompat() {
        val intent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CallRecorderApp.CHANNEL_OBSERVER)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("FIANO")
            .setContentText("새로운 통화 녹음을 감지하고 있어요")
            .setOngoing(true)
            .setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    companion object {
        private const val TAG = "RecObserver"
        private const val NOTIF_ID = 4002
        private val TOKEN = Any()

        fun start(context: android.content.Context) {
            val i = Intent(context, RecordingObserverService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }
    }
}
