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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

data class CalendarUiState(
    val loading: Boolean = false,
    val connections: List<CalendarConnection> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val manualEvents: List<ManualCalendarEventEntity> = emptyList(),
    val eventsLoading: Boolean = false,
    val error: String? = null,
)

private data class CalendarMonthCache(
    val events: List<CalendarEvent>,
    val manualEvents: List<ManualCalendarEventEntity>,
)

class CalendarViewModel : ViewModel() {
    private val container = CallRecorderApp.instance.container
    private val calendarRepo = container.calendarRepo

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()
    private var loadedMonthKey: String? = null
    private var loadingMonthKey: String? = null
    private val monthCache = mutableMapOf<String, CalendarMonthCache>()
    private var hasExternalCalendarConnection: Boolean? = null

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
                    hasExternalCalendarConnection = connections.isNotEmpty()
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
        val cached = monthCache[monthKey]
        if (!force && cached != null) {
            loadedMonthKey = monthKey
            _state.value = _state.value.copy(
                eventsLoading = false,
                events = cached.events,
                manualEvents = cached.manualEvents,
                error = null,
            )
            return
        }
        if (!force && (loadedMonthKey == monthKey || loadingMonthKey == monthKey)) return
        loadingMonthKey = monthKey
        viewModelScope.launch {
            _state.value = _state.value.copy(
                eventsLoading = true,
                events = emptyList(),
                manualEvents = emptyList(),
                error = null,
            )

            try {
                val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
                val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val from = "%04d-%02d-01".format(year, month)
                val to = "%04d-%02d-%02d".format(year, month, lastDay)

                val manualResult = calendarRepo.getManualEventsInRange(from = from, to = to)
                val manualEvents = manualResult.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    manualEvents = manualEvents,
                    error = manualResult.exceptionOrNull()?.message,
                )

                val hasExternalConnection = hasExternalCalendarConnection ?: run {
                    val connectionsResult = withTimeoutOrNull(2_000) {
                        calendarRepo.getConnections()
                    }
                    val connections = connectionsResult?.getOrDefault(emptyList()).orEmpty()
                    hasExternalCalendarConnection = connections.isNotEmpty()
                    _state.value = _state.value.copy(connections = connections)
                    connections.isNotEmpty()
                }

                if (!hasExternalConnection) {
                    loadedMonthKey = monthKey
                    monthCache[monthKey] = CalendarMonthCache(
                        events = emptyList(),
                        manualEvents = manualEvents,
                    )
                    _state.value = _state.value.copy(
                        eventsLoading = false,
                        events = emptyList(),
                        manualEvents = manualEvents,
                        error = manualResult.exceptionOrNull()?.message,
                    )
                    return@launch
                }

                val serverResult = withTimeoutOrNull(8_000) {
                    calendarRepo.getEventsInRange(from = from, to = to, limit = 200)
                }

                if (serverResult == null) {
                    loadedMonthKey = monthKey
                    monthCache[monthKey] = CalendarMonthCache(
                        events = emptyList(),
                        manualEvents = manualEvents,
                    )
                    _state.value = _state.value.copy(
                        eventsLoading = false,
                        events = emptyList(),
                        manualEvents = manualEvents,
                        error = "외부 캘린더 응답이 지연되고 있어요.",
                    )
                    return@launch
                }

                serverResult.fold(
                    onSuccess = { events ->
                        loadedMonthKey = monthKey
                        monthCache[monthKey] = CalendarMonthCache(
                            events = events,
                            manualEvents = manualEvents,
                        )
                        _state.value = _state.value.copy(
                            eventsLoading = false,
                            events = events,
                            manualEvents = manualEvents,
                            error = manualResult.exceptionOrNull()?.message,
                        )
                    },
                    onFailure = {
                        loadedMonthKey = monthKey
                        monthCache[monthKey] = CalendarMonthCache(
                            events = emptyList(),
                            manualEvents = manualEvents,
                        )
                        _state.value = _state.value.copy(
                            eventsLoading = false,
                            events = emptyList(),
                            manualEvents = manualEvents,
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
                onSuccess = {
                    hasExternalCalendarConnection = true
                    monthCache.clear()
                    loadedMonthKey = null
                    loadConnections()
                },
                onFailure = { _state.value = _state.value.copy(error = it.message) }
            )
        }
    }

    fun disconnect(provider: String) {
        viewModelScope.launch {
            calendarRepo.disconnect(provider).fold(
                onSuccess = {
                    hasExternalCalendarConnection = null
                    monthCache.clear()
                    loadedMonthKey = null
                    loadConnections()
                },
                onFailure = { _state.value = _state.value.copy(error = it.message) }
            )
        }
    }
}
