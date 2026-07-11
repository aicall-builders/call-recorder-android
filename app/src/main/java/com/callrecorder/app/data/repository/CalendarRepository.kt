package com.callrecorder.app.data.repository

import com.callrecorder.app.data.api.ApiService
import com.callrecorder.app.data.local.ManualCalendarEventDao
import com.callrecorder.app.data.local.ManualCalendarEventEntity
import com.callrecorder.app.data.model.CalendarConnection
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.data.model.CalendarOAuthCodeRequest

class CalendarRepository(
    private val api: ApiService,
    private val manualCalendarEventDao: ManualCalendarEventDao,
) {

    suspend fun getConnections(): Result<List<CalendarConnection>> = runCatching {
        api.getCalendarConnections().connections
    }

    suspend fun getOAuthUrl(provider: String, redirectUri: String, state: String): Result<String> = runCatching {
        api.getCalendarOAuthUrl(provider, redirectUri, state).authUrl
    }

    suspend fun disconnect(provider: String): Result<Unit> = runCatching {
        api.disconnectCalendar(provider)
        Unit
    }

    /** 앱이 받은 OAuth code를 앱의 토큰으로 직접 완료 (웹 세션 의존 제거) */
    suspend fun completeOAuth(
        provider: String,
        code: String,
        redirectUri: String,
        state: String,
    ): Result<Unit> = runCatching {
        api.completeCalendarOAuth(
            CalendarOAuthCodeRequest(
                provider = provider,
                authorizationCode = code,
                redirectUri = redirectUri,
                state = state,
            )
        )
        Unit
    }

    /** 특정 하루 일정. date = "YYYY-MM-DD", null이면 오늘 */
    suspend fun getEvents(date: String? = null, limit: Int = 50): Result<List<CalendarEvent>> = runCatching {
        api.getCalendarEvents(date = date, limit = limit).events
    }

    /** 날짜 범위 일정 (월 전체 조회용). from~to = "YYYY-MM-DD" */
    suspend fun getEventsInRange(from: String, to: String, limit: Int = 100): Result<List<CalendarEvent>> = runCatching {
        api.getCalendarEventsRange(from = from, to = to, limit = limit).events
    }

    suspend fun getManualEventsInRange(from: String, to: String): Result<List<ManualCalendarEventEntity>> = runCatching {
        manualCalendarEventDao.getInRange(fromDate = from, toDate = to)
    }

    suspend fun saveManualEvent(event: ManualCalendarEventEntity): Result<Unit> = runCatching {
        manualCalendarEventDao.upsert(event)
    }

    suspend fun deleteManualEvent(eventId: String): Result<Unit> = runCatching {
        manualCalendarEventDao.deleteById(eventId)
    }
}
