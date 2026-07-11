package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.CallDetail
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
)

class CallSummaryDetailViewModel : ViewModel() {

    private val callRepo = CallRecorderApp.instance.container.callRepo

    private val _state = MutableStateFlow(CallSummaryDetailUiState())
    val state: StateFlow<CallSummaryDetailUiState> = _state.asStateFlow()

    fun load(callId: String) {
        viewModelScope.launch {
            _state.value = CallSummaryDetailUiState(loading = true)

            val detailDeferred = async { callRepo.getDetail(callId) }
            val audioDeferred = async { callRepo.getAudioUrl(callId) }

            // 상세 정보가 도착하면 먼저 화면을 연다. 음성 URL은 뒤이어 갱신된다.
            val detailResult = detailDeferred.await()
            detailResult.fold(
                onSuccess = { detail ->
                    _state.value = _state.value.copy(loading = false, detail = detail, error = null)
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
        val nextOpen = !_state.value.showCalendarPicker
        if (nextOpen && _state.value.connectedCalendars.isEmpty()) {
            loadCalendars(callId = callId, openAfterLoad = true)
            return
        }
        if (nextOpen && _state.value.connectedCalendars.size == 1) {
            addToCalendar(callId, _state.value.connectedCalendars.first())
            return
        }
        _state.value = _state.value.copy(
            showCalendarPicker = nextOpen,
            calendarMessage = null,
        )
    }


}
