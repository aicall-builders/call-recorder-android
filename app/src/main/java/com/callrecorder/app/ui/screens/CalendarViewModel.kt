package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.ManualCalendarEventEntity
import com.callrecorder.app.data.model.CalendarConnection
import com.callrecorder.app.data.model.CalendarEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CalendarUiState(
    val loading: Boolean = false,
    val connections: List<CalendarConnection> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val manualEvents: List<ManualCalendarEventEntity> = emptyList(),
    val eventsLoading: Boolean = false,
    val error: String? = null,
)

class CalendarViewModel : ViewModel() {
    private val container = CallRecorderApp.instance.container
    private val calendarRepo = container.calendarRepo

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()
    private var loadedMonthKey: String? = null
    private var loadingMonthKey: String? = null

    init {
        // 현재 월 전체 일정 로드
        val now = Calendar.getInstance()
        loadMonthEvents(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
    }

    fun loadConnections() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            calendarRepo.getConnections().fold(
                onSuccess = { connections ->
                    _state.value = _state.value.copy(loading = false, connections = connections)
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                }
            )
        }
    }

    /** 해당 연/월의 1일~말일 범위 일정 로드 */
    fun loadMonthEvents(year: Int, month: Int, force: Boolean = false) {
        val monthKey = "%04d-%02d".format(year, month)
        if (!force && (loadedMonthKey == monthKey || loadingMonthKey == monthKey)) return
        loadingMonthKey = monthKey
        viewModelScope.launch {
            _state.value = _state.value.copy(eventsLoading = true)

            try {
                val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
                val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val from = "%04d-%02d-01".format(year, month)
                val to = "%04d-%02d-%02d".format(year, month, lastDay)

                val manualResult = calendarRepo.getManualEventsInRange(from = from, to = to)
                calendarRepo.getEventsInRange(from = from, to = to, limit = 200).fold(
                    onSuccess = { events ->
                        loadedMonthKey = monthKey
                        _state.value = _state.value.copy(
                            eventsLoading = false,
                            events = events,
                            manualEvents = manualResult.getOrDefault(emptyList()),
                            error = manualResult.exceptionOrNull()?.message,
                        )
                    },
                    onFailure = {
                        loadedMonthKey = monthKey
                        _state.value = _state.value.copy(
                            eventsLoading = false,
                            manualEvents = manualResult.getOrDefault(emptyList()),
                            error = it.message,
                        )
                    }
                )
            } finally {
                if (loadingMonthKey == monthKey) {
                    loadingMonthKey = null
                }
                if (_state.value.eventsLoading) {
                    _state.value = _state.value.copy(eventsLoading = false)
                }
            }
        }
    }

    fun refreshMonthEvents(year: Int, month: Int) {
        loadMonthEvents(year, month, force = true)
    }

    fun ensureConnectionsLoaded() {
        if (_state.value.connections.isNotEmpty() || _state.value.loading) return
        loadConnections()
    }

    fun refreshConnections() {
        loadConnections()
    }

    fun saveManualEvent(
        event: ManualCalendarEventEntity,
        year: Int,
        month: Int,
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            calendarRepo.saveManualEvent(event).fold(
                onSuccess = {
                    onSaved()
                    refreshMonthEvents(year, month)
                },
                onFailure = {
                    _state.value = _state.value.copy(error = it.message)
                },
            )
        }
    }

    fun getOAuthUrl(provider: String, redirectUri: String, state: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            calendarRepo.getOAuthUrl(provider, redirectUri, state).fold(
                onSuccess = { url -> onResult(url) },
                onFailure = { _state.value = _state.value.copy(error = it.message) }
            )
        }
    }

    fun completeOAuth(provider: String, code: String, redirectUri: String, state: String) {
        viewModelScope.launch {
            calendarRepo.completeOAuth(provider, code, redirectUri, state).fold(
                onSuccess = { loadConnections() },
                onFailure = { _state.value = _state.value.copy(error = it.message) }
            )
        }
    }

    fun disconnect(provider: String) {
        viewModelScope.launch {
            calendarRepo.disconnect(provider).fold(
                onSuccess = { loadConnections() },
                onFailure = { _state.value = _state.value.copy(error = it.message) }
            )
        }
    }
}
