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
        val events = manualCalendarEventDao.getInRange(fromDate = from, toDate = to)
        events
            .filter { it.id.startsWith("call-") || it.id.startsWith("auto-call-") }
            .mapNotNull { it.linkedCallIdOrNull() }
            .distinct()
            .forEach { callId ->
                normalizeLinkedCallDuplicate(
                    ManualCalendarEventEntity(
                        id = "auto-call-$callId",
                        title = "",
                        date = "",
                        time = "",
                    )
                )
            }
        manualCalendarEventDao.getInRange(fromDate = from, toDate = to)
            .dedupeLinkedCallEvents()
    }

    suspend fun saveManualEvent(event: ManualCalendarEventEntity): Result<Unit> = runCatching {
        manualCalendarEventDao.upsert(event.normalizedLinkedCallEvent())
        normalizeLinkedCallDuplicate(event)
    }

    suspend fun deleteManualEvent(eventId: String): Result<Unit> = runCatching {
        manualCalendarEventDao.deleteById(eventId)
    }

    suspend fun deleteLinkedCallEvents(callId: String): Result<Unit> = runCatching {
        manualCalendarEventDao.deleteLinkedCallEvents(
            canonicalId = "auto-call-$callId",
            legacyId = "call-$callId",
        )
    }

    suspend fun hasLinkedCallEvent(callId: String): Result<Boolean> = runCatching {
        manualCalendarEventDao
            .findLinkedCallEvents(
                legacyId = "call-$callId",
                canonicalId = "auto-call-$callId",
            )
            .isNotEmpty()
    }

    private suspend fun normalizeLinkedCallDuplicate(event: ManualCalendarEventEntity) {
        val callId = event.linkedCallIdOrNull() ?: return
        val legacyId = "call-$callId"
        val canonicalId = "auto-call-$callId"
        val linkedEvents = manualCalendarEventDao.findLinkedCallEvents(legacyId, canonicalId)
        if (linkedEvents.size <= 1) return

        val merged = linkedEvents
            .reduce { acc, next -> acc.mergeLinkedCallEvent(next, canonicalId) }
            .copy(id = canonicalId)
        manualCalendarEventDao.upsert(merged)
        manualCalendarEventDao.deleteLegacyLinkedCallEvent(legacyId)
    }

    private fun List<ManualCalendarEventEntity>.dedupeLinkedCallEvents(): List<ManualCalendarEventEntity> {
        val mergedByCall = linkedMapOf<String, ManualCalendarEventEntity>()
        val passthrough = mutableListOf<ManualCalendarEventEntity>()

        forEach { event ->
            val callId = event.linkedCallIdOrNull()
            if (callId == null) {
                passthrough += event
            } else {
                val canonicalId = "auto-call-$callId"
                val normalized = event.normalizedLinkedCallEvent()
                mergedByCall[callId] = mergedByCall[callId]
                    ?.mergeLinkedCallEvent(normalized, canonicalId)
                    ?: normalized.copy(id = canonicalId)
            }
        }

        return (passthrough + mergedByCall.values)
            .sortedWith(compareBy<ManualCalendarEventEntity> { it.date }.thenBy { it.time }.thenBy { it.title })
    }

    private fun ManualCalendarEventEntity.normalizedLinkedCallEvent(): ManualCalendarEventEntity {
        val callId = linkedCallIdOrNull() ?: return this
        return copy(id = "auto-call-$callId")
    }

    private fun ManualCalendarEventEntity.mergeLinkedCallEvent(
        other: ManualCalendarEventEntity,
        canonicalId: String,
    ): ManualCalendarEventEntity {
        val primary = if (other.updatedAt >= updatedAt) other else this
        val secondary = if (primary === other) this else other
        return primary.copy(
            id = canonicalId,
            title = primary.title.ifBlank { secondary.title },
            date = primary.date.ifBlank { secondary.date },
            time = primary.time.ifBlank { secondary.time },
            description = primary.description.ifBlank { secondary.description },
            chip = primary.chip.ifBlank { secondary.chip },
            imageUris = mergeImageUris(primary.imageUris, secondary.imageUris),
            reminderEnabled = primary.reminderEnabled || secondary.reminderEnabled,
            createdAt = minOf(primary.createdAt, secondary.createdAt),
            updatedAt = maxOf(primary.updatedAt, secondary.updatedAt),
        )
    }

    private fun ManualCalendarEventEntity.linkedCallIdOrNull(): String? =
        when {
            id.startsWith("auto-call-") -> id.removePrefix("auto-call-").takeIf { it.isNotBlank() }
            id.startsWith("call-") -> id.removePrefix("call-").takeIf { it.isNotBlank() }
            else -> null
        }

    private fun mergeImageUris(first: String, second: String): String =
        (first.lineSequence() + second.lineSequence())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
}
