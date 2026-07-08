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
    fun addToCalendar(callId: String, provider: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(calendarLoading = true, calendarMessage = null, showCalendarPicker = false)
            runCatching {
                CallRecorderApp.instance.container.api.addCalendarEvent(
                    callId,
                    mapOf("provider" to provider)
                )
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

    private fun loadCalendars(openAfterLoad: Boolean = false) {
        if (_state.value.calendarLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(calendarLoading = true)
            CallRecorderApp.instance.container.calendarRepo.getConnections().fold(
                onSuccess = { connections ->
                    _state.value = _state.value.copy(
                        calendarLoading = false,
                        connectedCalendars = connections.map { it.provider },
                        showCalendarPicker = openAfterLoad,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(calendarLoading = false)
                }
            )
        }
    }

    fun toggleCalendarPicker() {
        val nextOpen = !_state.value.showCalendarPicker
        if (nextOpen && _state.value.connectedCalendars.isEmpty()) {
            loadCalendars(openAfterLoad = true)
            return
        }
        _state.value = _state.value.copy(
            showCalendarPicker = nextOpen
        )
    }


}
