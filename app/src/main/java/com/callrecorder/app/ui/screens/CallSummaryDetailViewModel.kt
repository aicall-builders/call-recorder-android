package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.ManualCalendarEventEntity
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CallDetail
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 통화 상세 화면(시안 7)용 ViewModel.
 *
 * - detail: /calls/{id} 응답 (summary, extracted_info, stt_result 등)
 * - audioUrl: 음성 재생용 presigned URL (없으면 null - 재생 위젯은 disabled)
 *
 * detail 과 audioUrl 은 병렬로 로드한다.
 */
data class CallSummaryDetailUiState(
    val loading: Boolean = false,
    val detail: CallDetail? = null,
    val audioUrl: String? = null,
    val error: String? = null,
    val calendarMessage: String? = null,
    val calendarLoading: Boolean = false,
    val connectedCalendars: List<String> = emptyList(),
    val showCalendarPicker: Boolean = false,
    val internalCalendarRegistered: Boolean = false,
    val summarySaving: Boolean = false,
    val summaryMessage: String? = null,
    val originalCallId: String? = null,
    val originalSummary: String = "",
    val originalKeywordRows: List<Pair<String, String>> = emptyList(),
    val showMissingScheduleDialog: Boolean = false,
)

class CallSummaryDetailViewModel : ViewModel() {

    private val callRepo = CallRecorderApp.instance.container.callRepo
    private val calendarRepo = CallRecorderApp.instance.container.calendarRepo

    private val _state = MutableStateFlow(CallSummaryDetailUiState())
    val state: StateFlow<CallSummaryDetailUiState> = _state.asStateFlow()

    fun load(callId: String, initialCall: Call? = null) {
        viewModelScope.launch {
            _state.value = if (initialCall != null) {
                CallSummaryDetailUiState(
                    loading = false,
                    detail = CallDetail(call = initialCall, transcript = initialCall.sttResult),
                )
            } else {
                CallSummaryDetailUiState(loading = true)
            }

            val detailDeferred = async { callRepo.getDetail(callId) }
            val audioDeferred = async { callRepo.getAudioUrl(callId) }

            // 상세 정보가 도착하면 먼저 화면을 연다. 음성 URL은 뒤이어 갱신된다.
            val detailResult = detailDeferred.await()
            detailResult.fold(
                onSuccess = { detail ->
                    _state.value = _state.value.copy(
                        loading = false,
                        detail = detail,
                        error = null,
                        originalCallId = callId,
                        originalSummary = detail.call.summary.orEmpty(),
                        originalKeywordRows = detail.call.internalKeywordsMap().toList(),
                    )
                    refreshInternalCalendarRegistered(callId)
                },
                onFailure = { e ->
                    _state.value = if (initialCall != null) {
                        _state.value.copy(
                            loading = false,
                            error = null,
                            originalCallId = callId,
                            originalSummary = initialCall.summary.orEmpty(),
                            originalKeywordRows = initialCall.internalKeywordsMap().toList(),
                        )
                    } else {
                        _state.value.copy(loading = false, error = e.message)
                    }
                    if (initialCall != null) refreshInternalCalendarRegistered(callId)
                    return@launch
                },
            )

            audioDeferred.await().fold(
                onSuccess = { url -> _state.value = _state.value.copy(audioUrl = url) },
                onFailure = { _state.value = _state.value.copy(audioUrl = null) },
            )
        }
    }

    fun updateSummary(callId: String, summary: String) {
        val trimmed = summary.trim()
        if (trimmed.isBlank()) {
            _state.value = _state.value.copy(summaryMessage = "요약 내용을 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(summarySaving = true, summaryMessage = null)
            callRepo.updateSummary(callId, trimmed).fold(
                onSuccess = {
                    val current = _state.value.detail
                    _state.value = _state.value.copy(
                        summarySaving = false,
                        summaryMessage = "요약을 저장했어요.",
                        detail = current?.copy(call = current.call.copy(summary = trimmed)),
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        summarySaving = false,
                        summaryMessage = "요약 저장에 실패했어요. ${it.message.orEmpty()}".trim(),
                    )
                },
            )
        }
    }

    fun updateSummaryAndKeywords(
        callId: String,
        summary: String,
        keywordRows: List<Pair<String, String>>,
    ) {
        val trimmedSummary = summary.trim()
        val normalizedRows = keywordRows
            .map { (label, value) -> label.trim() to value.trim() }
            .filter { (label, value) -> label.isNotBlank() || value.isNotBlank() }

        if (trimmedSummary.isBlank() && normalizedRows.isEmpty()) {
            _state.value = _state.value.copy(summaryMessage = "요약 내용을 입력해 주세요.")
            return
        }

        val keywordObject = JsonObject(
            normalizedRows
                .filter { (label, _) -> label.isNotBlank() }
                .associate { (label, value) -> label to JsonPrimitive(value) }
        )

        viewModelScope.launch {
            _state.value = _state.value.copy(summarySaving = true, summaryMessage = null)
            callRepo.updateSummaryAndKeywords(
                callId = callId,
                summary = trimmedSummary.ifBlank { null },
                internalKeywords = keywordObject,
            ).fold(
                onSuccess = {
                    val current = _state.value.detail
                    _state.value = _state.value.copy(
                        summarySaving = false,
                        summaryMessage = "요약을 저장했어요.",
                        detail = current?.copy(
                            call = current.call.copy(
                                summary = trimmedSummary,
                                internalKeywordsRaw = keywordObject,
                            )
                        ),
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        summarySaving = false,
                        summaryMessage = "요약 저장에 실패했어요. ${it.message.orEmpty()}".trim(),
                    )
                },
            )
        }
    }

    fun clearSummaryMessage() {
        _state.value = _state.value.copy(summaryMessage = null)
    }

    fun dismissMissingScheduleDialog() {
        _state.value = _state.value.copy(showMissingScheduleDialog = false)
    }

    private fun refreshInternalCalendarRegistered(callId: String) {
        viewModelScope.launch {
            calendarRepo.hasLinkedCallEvent(callId).fold(
                onSuccess = { registered ->
                    _state.value = _state.value.copy(internalCalendarRegistered = registered)
                },
                onFailure = {
                    _state.value = _state.value.copy(internalCalendarRegistered = false)
                },
            )
        }
    }

    fun addToCalendar(callId: String, provider: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(calendarLoading = true, calendarMessage = null, showCalendarPicker = false)
            runCatching {
                withTimeoutOrNull(12_000) {
                    CallRecorderApp.instance.container.api.addCalendarEvent(
                        callId,
                        mapOf("provider" to provider)
                    )
                } ?: error("캘린더 등록 응답이 지연되고 있어요. 잠시 후 다시 시도해 주세요.")
            }
                .fold(
                onSuccess = { resp ->
                    _state.value = _state.value.copy(
                        calendarLoading = false,
                        calendarMessage = if (resp.success) "✅ 캘린더에 추가됐어요!" else "❌ 추가 실패"
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        calendarLoading = false,
                        calendarMessage = "❌ ${it.message}"
                    )
                }
            )
        }
    }

    fun addToInternalCalendar(callId: String) {
        if (_state.value.internalCalendarRegistered) return

        val detail = _state.value.detail
        val call = detail?.call
        if (call == null) {
            _state.value = _state.value.copy(calendarMessage = "통화 정보를 먼저 불러와 주세요.")
            return
        }

        val info = call.extractedInfoOrNull()
        val schedule = detail.resolveCalendarSchedule(info)
        val date = schedule?.date
        val time = schedule?.time ?: "00:00"
        if (date.isNullOrBlank()) {
            _state.value = _state.value.copy(
                calendarMessage = null,
                showMissingScheduleDialog = true,
            )
            return
        }

        val title = listOfNotNull(
            info?.customerName?.takeIf { it.isNotBlank() },
            call.callerName?.takeIf { it.isNotBlank() },
            call.callerNumber?.takeIf { it.isNotBlank() },
        ).firstOrNull() ?: "통화 일정"

        val description = buildString {
            val summary = call.summary?.takeIf { it.isNotBlank() }
            val notes = info?.specialNotes?.takeIf { it.isNotBlank() }
            if (summary != null) append(summary)
            if (notes != null) {
                if (isNotBlank()) append("\n")
                append(notes)
            }
            if (isBlank()) append("통화 분석에서 등록한 일정입니다.")
        }

        val chip = when {
            info?.categoryCode == "reservation" || call.category == "예약" -> "RESERVATION"
            info?.categoryCode == "inquiry" || call.category == "문의" -> "INQUIRY"
            else -> "OTHER"
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(calendarLoading = true, calendarMessage = null, showCalendarPicker = false)
            calendarRepo.saveManualEvent(
                ManualCalendarEventEntity(
                    id = "call-$callId",
                    title = title,
                    date = date,
                    time = time,
                    description = description,
                    chip = chip,
                    reminderEnabled = true,
                    updatedAt = System.currentTimeMillis(),
                )
            ).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        calendarLoading = false,
                        calendarMessage = "내부 캘린더에 등록됐어요.",
                        internalCalendarRegistered = true,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        calendarLoading = false,
                        calendarMessage = "내부 캘린더 등록에 실패했어요. ${it.message.orEmpty()}".trim(),
                    )
                },
            )
        }
    }

    private fun loadCalendars(callId: String? = null, openAfterLoad: Boolean = false) {
        if (_state.value.calendarLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(calendarLoading = true, calendarMessage = null)
            val result = withTimeoutOrNull(12_000) {
                CallRecorderApp.instance.container.calendarRepo.getConnections()
            }
            if (result == null) {
                _state.value = _state.value.copy(
                    calendarLoading = false,
                    showCalendarPicker = false,
                    calendarMessage = "캘린더 연결 조회가 지연되고 있어요. 잠시 후 다시 시도해 주세요.",
                )
                return@launch
            }
            result.fold(
                onSuccess = { connections ->
                    val providers = connections.map { it.provider }.filter { it.isNotBlank() }
                    if (providers.isEmpty()) {
                        _state.value = _state.value.copy(
                            calendarLoading = false,
                            connectedCalendars = emptyList(),
                            showCalendarPicker = false,
                            calendarMessage = "설정에서 외부 캘린더를 먼저 연동해 주세요.",
                        )
                        return@fold
                    }
                    if (callId != null && providers.size == 1) {
                        _state.value = _state.value.copy(
                            calendarLoading = false,
                            connectedCalendars = providers,
                            showCalendarPicker = false,
                        )
                        addToCalendar(callId, providers.first())
                        return@fold
                    }
                    _state.value = _state.value.copy(
                        calendarLoading = false,
                        connectedCalendars = providers,
                        showCalendarPicker = openAfterLoad,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        calendarLoading = false,
                        showCalendarPicker = false,
                        calendarMessage = "캘린더 연결 정보를 불러오지 못했어요.",
                    )
                }
            )
        }
    }

    fun toggleCalendarPicker(callId: String) {
        addToInternalCalendar(callId)
    }


}

private data class CalendarScheduleCandidate(
    val date: String,
    val time: String,
)

private fun CallDetail.resolveCalendarSchedule(info: com.callrecorder.app.data.model.ExtractedInfo?): CalendarScheduleCandidate? {
    val call = call
    val base = call.createdAt.toCalendarBase()
    val scheduleLabels = listOf(
        "방문", "일정", "날짜", "예약", "시간", "미팅", "상담",
        "visit", "schedule", "date", "reservation", "appointment", "meeting", "time",
    )
    val keywordTexts = call.internalKeywordsMap()
        .entries
        .filter { (label, value) -> value.isNotBlank() && scheduleLabels.any { label.contains(it) } }
        .flatMap { (label, value) -> listOf("$label $value", value) }

    val normalizedDate = info?.date?.takeIf { it.isNotBlank() }?.toCalendarDateOrNull(base)
    if (normalizedDate != null) {
        return CalendarScheduleCandidate(
            date = normalizedDate,
            time = info.time?.takeIf { it.isNotBlank() }?.toCalendarTimeOrNull() ?: "00:00",
        )
    }

    val texts = buildList {
        addAll(keywordTexts)
        info?.date?.takeIf { it.isNotBlank() }?.let { add(it) }
        info?.time?.takeIf { it.isNotBlank() }?.let { add(it) }
        transcript?.takeIf { it.isNotBlank() }?.let { add(it) }
        call.sttResult?.takeIf { it.isNotBlank() }?.let { add(it) }
        info?.specialNotes?.takeIf { it.isNotBlank() }?.let { add(it) }
        call.summary?.takeIf { it.isNotBlank() }?.let { add(it) }
    }

    texts.forEach { text ->
        text.toCalendarScheduleCandidate(base)?.let { return it }
    }

    return null
}

private fun String.toCalendarScheduleCandidate(base: Calendar): CalendarScheduleCandidate? {
    val date = toCalendarDateOrNull(base) ?: return null
    val time = toCalendarTimeOrNull() ?: "00:00"
    return CalendarScheduleCandidate(date = date, time = time)
}

private fun String.toCalendarDateOrNull(base: Calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault())): String? {
    val text = trim()
    absoluteDateOrNull(text, base)?.let { return it }
    relativeDateOrNull(text, base)?.let { return it }
    weekdayDateOrNull(text, base)?.let { return it }
    return null
}

private fun absoluteDateOrNull(text: String, base: Calendar): String? {
    val lower = text.lowercase(Locale.US)
    val fullMatchers = listOf(
        Regex("""(\d{4})\s*년\s*(\d{1,2})\s*월\s*(\d{1,2})\s*일"""),
        Regex("""(\d{4})[.\-/]\s*(\d{1,2})[.\-/]\s*(\d{1,2})"""),
    )
    for (regex in fullMatchers) {
        val match = regex.find(text) ?: continue
        val year = match.groupValues[1].toIntOrNull() ?: continue
        val month = match.groupValues[2].toIntOrNull() ?: continue
        val day = match.groupValues[3].toIntOrNull() ?: continue
        return makeDateOrNull(year, month, day)
    }

    val monthDayMatchers = listOf(
        Regex("""(?<!\d)(\d{1,2})\s*월\s*(\d{1,2})\s*일"""),
        Regex("""(?<!\d)(\d{1,2})[./]\s*(\d{1,2})(?!\d)"""),
    )
    for (regex in monthDayMatchers) {
        val match = regex.find(text) ?: continue
        val month = match.groupValues[1].toIntOrNull() ?: continue
        val day = match.groupValues[2].toIntOrNull() ?: continue
        val candidate = base.copyInDeviceZone().apply {
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        if (!isValidDate(candidate, month, day)) continue
        if (candidate.before(base.startOfDay())) candidate.add(Calendar.YEAR, 1)
        return candidate.toCalendarDateString()
    }

    englishMonthDayOrNull(lower, base)?.let { return it }

    return null
}

private fun relativeDateOrNull(text: String, base: Calendar): String? {
    val lower = text.lowercase(Locale.US)
    val days = when {
        text.contains("그글피") -> 3
        text.contains("모레") -> 2
        text.contains("내일") -> 1
        text.contains("오늘") -> 0
        Regex("""\bday\s+after\s+tomorrow\b""").containsMatchIn(lower) -> 2
        Regex("""\btomorrow\b""").containsMatchIn(lower) -> 1
        Regex("""\btoday\b""").containsMatchIn(lower) -> 0
        else -> null
    } ?: return null
    return base.copyInDeviceZone().apply { add(Calendar.DAY_OF_MONTH, days) }.toCalendarDateString()
}

private fun weekdayDateOrNull(text: String, base: Calendar): String? {
    val lower = text.lowercase(Locale.US)
    val koreanWeekdays = listOf(
        "일요일" to Calendar.SUNDAY,
        "월요일" to Calendar.MONDAY,
        "화요일" to Calendar.TUESDAY,
        "수요일" to Calendar.WEDNESDAY,
        "목요일" to Calendar.THURSDAY,
        "금요일" to Calendar.FRIDAY,
        "토요일" to Calendar.SATURDAY,
        "일욜" to Calendar.SUNDAY,
        "월욜" to Calendar.MONDAY,
        "화욜" to Calendar.TUESDAY,
        "수욜" to Calendar.WEDNESDAY,
        "목욜" to Calendar.THURSDAY,
        "금욜" to Calendar.FRIDAY,
        "토욜" to Calendar.SATURDAY,
        "일" to Calendar.SUNDAY,
        "월" to Calendar.MONDAY,
        "화" to Calendar.TUESDAY,
        "수" to Calendar.WEDNESDAY,
        "목" to Calendar.THURSDAY,
        "금" to Calendar.FRIDAY,
        "토" to Calendar.SATURDAY,
    )
    val englishWeekdays = listOf(
        "sunday" to Calendar.SUNDAY,
        "sun" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,
        "mon" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "tue" to Calendar.TUESDAY,
        "tues" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "wed" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "thu" to Calendar.THURSDAY,
        "thur" to Calendar.THURSDAY,
        "thurs" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "fri" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY,
        "sat" to Calendar.SATURDAY,
    )
    val koreanTarget = koreanWeekdays.firstOrNull { (label, _) ->
        Regex("""(?<![가-힣])${Regex.escape(label)}(?:요일|욜)?(?![가-힣])""").containsMatchIn(text)
    }?.second
    val englishTarget = englishWeekdays.firstOrNull { (label, _) ->
        Regex("""\b${Regex.escape(label)}(?:day)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
    }?.second
    val target = koreanTarget ?: englishTarget ?: return null

    val result = base.copyInDeviceZone()
    val current = result.get(Calendar.DAY_OF_WEEK)
    var diff = (target - current + 7) % 7
    if (text.contains("다음주") || text.contains("다음 주") || Regex("""\bnext\s+week\b""").containsMatchIn(lower)) {
        diff = daysUntilNextWeekdayFromMonday(result, target)
    } else if (Regex("""\bnext\b""").containsMatchIn(lower)) {
        diff = if (diff == 0) 7 else diff
    } else if (
        diff == 0 &&
        !text.contains("오늘") &&
        !text.contains("이번") &&
        !Regex("""\btoday\b|\bthis\b""").containsMatchIn(lower)
    ) {
        diff = 7
    }
    result.add(Calendar.DAY_OF_MONTH, diff)
    return result.toCalendarDateString()
}

private fun String.toCalendarTimeOrNull(): String? {
    val text = trim()
    val lower = text.lowercase(Locale.US)
    val ampm = Regex("""\b(\d{1,2})(?::\s*(\d{1,2}))?\s*(a\.?m\.?|p\.?m\.?)\b""").find(lower)
    if (ampm != null) {
        var hour = ampm.groupValues[1].toIntOrNull() ?: return null
        val minute = ampm.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        val marker = ampm.groupValues[3]
        if (hour !in 1..12 || minute !in 0..59) return null
        if (marker.startsWith("p") && hour != 12) hour += 12
        if (marker.startsWith("a") && hour == 12) hour = 0
        return "%02d:%02d".format(hour, minute)
    }

    val colon = Regex("""(\d{1,2})\s*:\s*(\d{1,2})""").find(text)
    if (colon != null) {
        val hour = colon.groupValues[1].toIntOrNull() ?: return null
        val minute = colon.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return "%02d:%02d".format(hour, minute)
    }

    val korean = Regex("""(\d{1,2})\s*시(?:\s*(\d{1,2})\s*분)?""").find(text) ?: return null
    val hour = korean.groupValues[1].toIntOrNull() ?: return null
    val minute = korean.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
    if (hour !in 0..23 || minute !in 0..59) return null
    return "%02d:%02d".format(hour, minute)
}

private fun englishMonthDayOrNull(text: String, base: Calendar): String? {
    val monthAlternatives = englishMonths.keys.joinToString("|") { Regex.escape(it) }
    val monthFirst = Regex("""\b($monthAlternatives)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""")
    val dayFirst = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($monthAlternatives)\.?(?:,?\s+(\d{4}))?\b""")

    monthFirst.find(text)?.let { match ->
        val month = englishMonths[match.groupValues[1].trimEnd('.')] ?: return@let
        val day = match.groupValues[2].toIntOrNull() ?: return@let
        return makeMonthDayDate(month, day, match.groupValues.getOrNull(3), base)
    }
    dayFirst.find(text)?.let { match ->
        val day = match.groupValues[1].toIntOrNull() ?: return@let
        val month = englishMonths[match.groupValues[2].trimEnd('.')] ?: return@let
        return makeMonthDayDate(month, day, match.groupValues.getOrNull(3), base)
    }

    return null
}

private val englishMonths = mapOf(
    "january" to 1,
    "jan" to 1,
    "february" to 2,
    "feb" to 2,
    "march" to 3,
    "mar" to 3,
    "april" to 4,
    "apr" to 4,
    "may" to 5,
    "june" to 6,
    "jun" to 6,
    "july" to 7,
    "jul" to 7,
    "august" to 8,
    "aug" to 8,
    "september" to 9,
    "sep" to 9,
    "sept" to 9,
    "october" to 10,
    "oct" to 10,
    "november" to 11,
    "nov" to 11,
    "december" to 12,
    "dec" to 12,
)

private fun makeMonthDayDate(month: Int, day: Int, yearText: String?, base: Calendar): String? {
    val explicitYear = yearText?.takeIf { it.isNotBlank() }?.toIntOrNull()
    if (explicitYear != null) return makeDateOrNull(explicitYear, month, day)

    val candidate = base.copyInDeviceZone().apply {
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
    }
    if (!isValidDate(candidate, month, day)) return null
    if (candidate.before(base.startOfDay())) candidate.add(Calendar.YEAR, 1)
    return candidate.toCalendarDateString()
}

private fun String?.toCalendarBase(): Calendar {
    val zone = TimeZone.getDefault()
    val locale = Locale.getDefault()
    val fallback = Calendar.getInstance(zone, locale)
    if (isNullOrBlank()) return fallback

    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (format in formats) {
        val parsed = runCatching {
            SimpleDateFormat(format, Locale.US).apply {
                timeZone = if (format.endsWith("'Z'")) TimeZone.getTimeZone("UTC") else zone
            }.parse(this)
        }.getOrNull() ?: continue
        return Calendar.getInstance(zone, locale).apply { time = parsed }
    }
    return fallback
}

private fun makeDateOrNull(year: Int, month: Int, day: Int): String? {
    if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
    val candidate = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, day)
    }
    if (!isValidDate(candidate, month, day)) return null
    return candidate.toCalendarDateString()
}

private fun isValidDate(calendar: Calendar, month: Int, day: Int): Boolean =
    calendar.get(Calendar.MONTH) == month - 1 && calendar.get(Calendar.DAY_OF_MONTH) == day

private fun Calendar.copyInDeviceZone(): Calendar =
    (clone() as Calendar).apply { timeZone = TimeZone.getDefault() }

private fun Calendar.startOfDay(): Calendar =
    copyInDeviceZone().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun Calendar.toCalendarDateString(): String =
    "%04d-%02d-%02d".format(
        get(Calendar.YEAR),
        get(Calendar.MONTH) + 1,
        get(Calendar.DAY_OF_MONTH),
    )

private fun daysUntilNextWeekdayFromMonday(base: Calendar, targetDayOfWeek: Int): Int {
    val daysUntilNextMonday = (Calendar.MONDAY - base.get(Calendar.DAY_OF_WEEK) + 7) % 7
        .let { if (it == 0) 7 else it }
    val targetOffsetFromMonday = (targetDayOfWeek - Calendar.MONDAY + 7) % 7
    return daysUntilNextMonday + targetOffsetFromMonday
}
