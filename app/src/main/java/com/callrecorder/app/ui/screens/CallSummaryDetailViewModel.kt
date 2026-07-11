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
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
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
        val detail = _state.value.detail
        val call = detail?.call
        if (call == null) {
            _state.value = _state.value.copy(calendarMessage = "통화 정보를 먼저 불러와 주세요.")
            return
        }

        val info = call.extractedInfoOrNull()
        val schedule = call.resolveCalendarSchedule(info)
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

private fun Call.resolveCalendarSchedule(info: com.callrecorder.app.data.model.ExtractedInfo?): CalendarScheduleCandidate? {
    val keywordSchedule = internalKeywordsMap()
        .entries
        .firstOrNull { (label, value) ->
            value.isNotBlank() && listOf("방문", "일정", "날짜", "예약").any { label.contains(it) }
        }
        ?.value
        ?.toCalendarScheduleCandidate()

    if (keywordSchedule != null) return keywordSchedule

    val normalizedDate = info?.date?.takeIf { it.isNotBlank() }?.toCalendarDateOrNull()
    if (normalizedDate != null) {
        return CalendarScheduleCandidate(
            date = normalizedDate,
            time = info.time?.takeIf { it.isNotBlank() }?.toCalendarTimeOrNull() ?: "00:00",
        )
    }

    return null
}

private fun String.toCalendarScheduleCandidate(): CalendarScheduleCandidate? {
    val date = toCalendarDateOrNull() ?: return null
    val time = toCalendarTimeOrNull() ?: "00:00"
    return CalendarScheduleCandidate(date = date, time = time)
}

private fun String.toCalendarDateOrNull(): String? {
    val text = trim()
    val matchers = listOf(
        Regex("""(\d{4})\s*년\s*(\d{1,2})\s*월\s*(\d{1,2})\s*일"""),
        Regex("""(\d{4})[.\-/]\s*(\d{1,2})[.\-/]\s*(\d{1,2})"""),
    )
    for (regex in matchers) {
        val match = regex.find(text) ?: continue
        val year = match.groupValues[1].toIntOrNull() ?: continue
        val month = match.groupValues[2].toIntOrNull() ?: continue
        val day = match.groupValues[3].toIntOrNull() ?: continue
        if (month !in 1..12 || day !in 1..31) continue
        return "%04d-%02d-%02d".format(year, month, day)
    }
    return null
}

private fun String.toCalendarTimeOrNull(): String? {
    val text = trim()
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
