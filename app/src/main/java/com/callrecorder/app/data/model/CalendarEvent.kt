package com.callrecorder.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CalendarEvent(
    val id: String,
    val provider: String,
    val title: String,
    val time: String,                                    // "14:00"
    @SerialName("end_time") val endTime: String = "",
    val description: String = "",
    @SerialName("event_url") val eventUrl: String? = null,
    @SerialName("start_at") val startAt: String? = null, // "2026-06-11 14:00:00"
    @SerialName("end_at") val endAt: String? = null,
) {
    /** start_at 에서 "일(day)"만 추출. 파싱 실패 시 null */
    val dayOfMonth: Int?
        get() {
            val s = startAt ?: return null
            // "2026-06-11 14:00:00" 또는 "2026-06-11T14:00:00" 형태
            val datePart = s.substringBefore("T").substringBefore(" ")  // "2026-06-11"
            val parts = datePart.split("-")
            return parts.getOrNull(2)?.toIntOrNull()
        }

    /** start_at 에서 "연도" 추출 */
    val year: Int?
        get() {
            val datePart = (startAt ?: return null).substringBefore("T").substringBefore(" ")
            return datePart.split("-").getOrNull(0)?.toIntOrNull()
        }

    /** start_at 에서 "월" 추출 */
    val month: Int?
        get() {
            val datePart = (startAt ?: return null).substringBefore("T").substringBefore(" ")
            return datePart.split("-").getOrNull(1)?.toIntOrNull()
        }
}

@Serializable
data class CalendarEventsResponse(
    val date: String,
    val events: List<CalendarEvent>,
    val count: Int,
)