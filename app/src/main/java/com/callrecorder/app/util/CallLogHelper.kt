package com.callrecorder.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * 기기 통화기록(CallLog)에서 녹음(통화) 시각에 해당하는
 * 실제 발신/수신 번호와 연락처 이름을 찾아준다.
 *
 * - 녹음 파일명엔 "이름" 또는 "번호" 중 하나만 들어있어 둘 다 알 수 없다.
 * - 통화기록은 번호 + 저장된 연락처 이름(CACHED_NAME)을 함께 보관하므로
 *   녹음 시각으로 매칭하면 둘 다 정확히 얻을 수 있다.
 *
 * READ_CALL_LOG 권한이 없으면 null 을 반환(앱은 기존 동작 유지).
 */
object CallLogHelper {

    data class CallerInfo(val number: String?, val name: String?)

    /**
     * @param startedAtMillis 녹음(통화) 시작 추정 시각 (epoch ms)
     * @param durationSeconds 녹음 길이(초). 보조 정보, 매칭엔 미사용
     * @param toleranceMillis 시각 허용 오차 (기본 5분)
     * @return 가장 가까운 통화기록의 (번호, 이름). 없으면 null
     */
    fun lookup(
        context: Context,
        startedAtMillis: Long,
        durationSeconds: Int = 0,
        toleranceMillis: Long = 5 * 60 * 1000L,
    ): CallerInfo? {
        if (startedAtMillis <= 0L) return null

        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALL_LOG,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        return try {
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
            )
            val from = startedAtMillis - toleranceMillis
            val to = startedAtMillis + toleranceMillis
            val selection = "${CallLog.Calls.DATE} BETWEEN ? AND ?"
            val args = arrayOf(from.toString(), to.toString())

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                args,
                "${CallLog.Calls.DATE} ASC",
            )?.use { c ->
                val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = c.getColumnIndex(CallLog.Calls.DATE)

                var best: CallerInfo? = null
                var bestDiff = Long.MAX_VALUE
                while (c.moveToNext()) {
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else continue
                    val diff = abs(date - startedAtMillis)
                    if (diff < bestDiff) {
                        bestDiff = diff
                        val number = if (numIdx >= 0) c.getString(numIdx) else null
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                        best = CallerInfo(
                            number = number?.takeIf { it.isNotBlank() },
                            name = name?.takeIf { it.isNotBlank() },
                        )
                    }
                }
                best
            }
        } catch (e: Exception) {
            SafeLog.w("CallLogHelper", "통화기록 조회 실패", e)
            null
        }
    }
}