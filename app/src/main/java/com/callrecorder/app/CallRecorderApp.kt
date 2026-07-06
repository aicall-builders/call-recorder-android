package com.callrecorder.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import com.callrecorder.app.di.AppContainer
import com.kakao.sdk.common.KakaoSdk

class CallRecorderApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 카카오 SDK 초기화
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        // 네이버 SDK 초기화
        com.navercorp.nid.NaverIdLoginSDK.initialize(
            this,
            BuildConfig.NAVER_CLIENT_ID,
            BuildConfig.NAVER_CLIENT_SECRET,
            "FIANO"
        )

        // DI 컨테이너 (수동 DI - Hilt 없이 가벼움 유지)
        container = AppContainer(this)

        createNotificationChannels()
    }

    /**
     * 자동 업로드(자동 분석) 설정. 기본값 false = 수동 승인 후 업로드.
     * true 면 녹음 감지 시 바로 업로드 큐(PENDING)로 등록된다.
     */
    fun isAutoUploadEnabled(): Boolean =
        getSharedPreferences("app_settings", MODE_PRIVATE).getBoolean("auto_analyze", false)

    /** 통화 분석 완료 시 상단(상태바) 로컬 알림. */
    fun notifyAnalysisDone(count: Int) {
        if (count <= 0) return
        val intent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (count == 1) "통화 1건 분석이 완료됐어요" else "통화 ${count}건 분석이 완료됐어요"
        val n = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FIANO 분석 완료")
            .setContentText(text)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).notify(5001, n)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OBSERVER,
                "FIANO 통화 감지",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "통화 녹음 파일을 감지합니다" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPLOAD,
                "FIANO 업로드",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "녹음 파일을 서버에 업로드합니다" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SUMMARY,
                "FIANO 요약 완료",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "통화 요약이 준비되었습니다" }
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    companion object {
        lateinit var instance: CallRecorderApp
            private set

        const val CHANNEL_OBSERVER = "channel_observer"
        const val CHANNEL_UPLOAD = "channel_upload"
        const val CHANNEL_SUMMARY = "channel_summary"
    }
}
