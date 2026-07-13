package com.callrecorder.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

    data class CallerInfo(
        val number: String?,
        val name: String?,
        val startedAtMillis: Long,
        val durationSeconds: Int,
        val direction: String?,
    )

    /**
     * @param startedAtMillis 녹음(통화) 시작 추정 시각 (epoch ms)
     * @param durationSeconds 녹음 길이(초). 시작/종료 시각과 함께 매칭 점수에 사용
     * @param toleranceMillis 시각 허용 오차 (기본 5분)
     * @return 가장 가까운 통화기록의 번호/이름/정확한 시작시각/방향. 없으면 null
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
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
            )
            val estimatedEndAtMillis = if (durationSeconds > 0) {
                startedAtMillis + durationSeconds * 1000L
            } else {
                startedAtMillis
            }
            val from = min(startedAtMillis, estimatedEndAtMillis) - toleranceMillis
            val to = max(startedAtMillis, estimatedEndAtMillis) + toleranceMillis
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
                val durationIdx = c.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = c.getColumnIndex(CallLog.Calls.TYPE)

                var best: CallerInfo? = null
                var bestScore = Long.MAX_VALUE
                while (c.moveToNext()) {
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else continue
                    val callDurationSeconds = if (durationIdx >= 0) c.getInt(durationIdx) else 0
                    val callEndAtMillis = date + callDurationSeconds * 1000L
                    val durationDiff = if (durationSeconds > 0 && callDurationSeconds > 0) {
                        abs(callDurationSeconds - durationSeconds) * 1000L
                    } else {
                        0L
                    }
                    val startDiff = abs(date - startedAtMillis)
                    val endDiff = abs(callEndAtMillis - estimatedEndAtMillis)
                    val score = min(startDiff, endDiff) + durationDiff / 2L
                    if (score < bestScore) {
                        bestScore = score
                        val number = if (numIdx >= 0) c.getString(numIdx) else null
                        val name = if (nameIdx >= 0) c.getString(nameIdx) else null
                        val type = if (typeIdx >= 0) c.getInt(typeIdx) else 0
                        best = CallerInfo(
                            number = number?.takeIf { it.isNotBlank() },
                            name = name?.takeIf { it.isNotBlank() },
                            startedAtMillis = date,
                            durationSeconds = callDurationSeconds.takeIf { it > 0 } ?: durationSeconds,
                            direction = directionOf(type),
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

    private fun directionOf(type: Int): String? = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        else -> null
    }
}
