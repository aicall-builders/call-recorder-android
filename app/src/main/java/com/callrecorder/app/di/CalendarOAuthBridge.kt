package com.callrecorder.app.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 외부 캘린더 OAuth 딥링크 콜백을 Activity → Compose 화면으로 전달하는 단순 브릿지.
 * MainActivity가 callrecorder://oauth/{provider} 딥링크를 받아 submit()하면,
 * ExternalCalendarTab의 LaunchedEffect가 이를 수집해 앱 토큰으로 완료한다.
 */
data class CalendarOAuthResult(
    val provider: String,
    val code: String,
    val state: String,
)

class CalendarOAuthBridge {
    private val _pending = MutableStateFlow<CalendarOAuthResult?>(null)
    val pending: StateFlow<CalendarOAuthResult?> = _pending.asStateFlow()

    fun submit(result: CalendarOAuthResult) { _pending.value = result }
    fun consume() { _pending.value = null }
}
