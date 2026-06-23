package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.CallDetail
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
        loadCalendars()
        viewModelScope.launch {
            _state.value = CallSummaryDetailUiState(loading = true)

            // 1) 통화 상세
            val detailResult = callRepo.getDetail(callId)
            detailResult.fold(
                onSuccess = { detail ->
                    _state.value = _state.value.copy(loading = false, detail = detail, error = null)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                    return@launch
                },
            )

            // 2) 음성 URL — 백엔드에 /calls/{id}/audio 엔드포인트 활성화됨
            callRepo.getAudioUrl(callId).fold(
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

    fun loadCalendars() {
        viewModelScope.launch {
            CallRecorderApp.instance.container.calendarRepo.getConnections().fold(
                onSuccess = { connections ->
                    _state.value = _state.value.copy(
                        connectedCalendars = connections.map { it.provider }
                    )
                },
                onFailure = {}
            )
        }
    }

    fun toggleCalendarPicker() {
        _state.value = _state.value.copy(
            showCalendarPicker = !_state.value.showCalendarPicker
        )
    }


}