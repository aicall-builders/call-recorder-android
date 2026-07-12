package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.ManualCalendarEventEntity
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.data.model.CallStatus
import com.callrecorder.app.data.model.CustomerListItem
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import com.callrecorder.app.worker.UploadWorker
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray

data class UploadItem(
    val id: Long,
    val name: String,
    val phase: String,   // "대기중" / "업로드중" / "분석중"
    val status: String,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
)

data class HomeUiState(
    val loading: Boolean = false,
    val todayTotal: Int = 0,
    val todaySummarized: Int = 0,
    val todayScheduled: Int = 0,
    val recentCalls: List<Call> = emptyList(),
    val pinnedCustomers: List<CustomerListItem> = emptyList(),
    val pendingApprovalCount: Int = 0,
    val schedules: List<CalendarEvent> = emptyList(),
    val error: String? = null,
    val autoSummaryEnabled: Boolean = false,
    val importantFilterEnabled: Boolean = false,
    val uploadingCount: Int = 0,
    val activeUploads: List<UploadItem> = emptyList(),
)

private data class CallsSnapshot(
    val calls: List<Call>,
    val listedStoreIds: Set<String>,
)

class HomeViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val callRepo = container.callRepo
    private val storeRepo = container.storeRepo
    private val recordingDao = container.recordingDao
    private val calendarRepo = container.calendarRepo
    private val api = container.api
    private val manualCustomerPrefs = CallRecorderApp.instance
        .getSharedPreferences("manual_customers", android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // 홈 히어로와 설정 화면이 같은 설정값을 공유한다.
    private val appPrefs by lazy {
        CallRecorderApp.instance.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    }

    private var _allCalls: List<Call> = emptyList()
    private var pollJob: Job? = null
    private val retriedUploadedProcessIds = mutableSetOf<String>()
    private val retriedServerProcessingIds = mutableSetOf<String>()
    private var hasExternalCalendarConnection: Boolean? = null
    private var pendingUploadKickStarted = false

    init {
        syncSettingsFromPrefs()
        refresh()
        observeUploads()
    }

    /** 업로드 진행 개수를 실시간 관찰 -> 홈 배너 반영 */
    private fun observeUploads() {
        viewModelScope.launch {
            recordingDao.observeActiveUploads().collect { list ->
                markLocallyStaleProcessingAsFailed(list)
                val items = list.map {
                    UploadItem(
                        id = it.id,
                        name = it.fileName,
                        phase = when (it.status) {
                            RecordingStatus.PENDING -> "대기중"
                            RecordingStatus.UPLOADING -> "업로드중"
                            RecordingStatus.FAILED -> "실패"
                            else -> "서버 분석 중"   // UPLOADED / PROCESSING
                        },
                        status = it.status,
                        errorMessage = it.errorMessage,
                        canRetry = it.status == RecordingStatus.FAILED,
                    )
                }
                val runningCount = list.count {
                    it.status == RecordingStatus.PENDING ||
                            it.status == RecordingStatus.UPLOADING ||
                            it.status == RecordingStatus.UPLOADED ||
                            it.status == RecordingStatus.PROCESSING
                }
                if (!pendingUploadKickStarted && list.any { it.status == RecordingStatus.PENDING }) {
                    pendingUploadKickStarted = true
                    UploadWorker.enqueueOneShot(CallRecorderApp.instance.applicationContext)
                }
                _state.value = _state.value.copy(
                    uploadingCount = runningCount,
                    activeUploads = items,
                )
                // 진행 중 항목이 있으면 서버 분석이 끝날 때까지 주기적으로 확인해 자동 정리
                if (runningCount > 0) startPollingForCompletion()
            }
        }
    }

    private fun markLocallyStaleProcessingAsFailed(list: List<RecordingEntity>) {
        val staleThreshold = System.currentTimeMillis() - 10 * 60 * 1000L
        val staleItems = list.filter {
            (it.status == RecordingStatus.UPLOADED || it.status == RecordingStatus.PROCESSING) &&
                    it.updatedAt < staleThreshold
        }
        if (staleItems.isEmpty()) return
        viewModelScope.launch {
            staleItems.forEach {
                recordingDao.setError(
                    it.id,
                    RecordingStatus.FAILED,
                    "서버 분석 상태 확인이 지연되고 있어요. 삭제 후 다시 업로드해 주세요.",
                )
            }
        }
    }

    /** 업로드/분석이 끝날 때까지 5초마다 새로고침해 진행 칩을 자동으로 내림 (최대 2분). */
    private fun startPollingForCompletion() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            var tries = 0
            while (isActive && _state.value.uploadingCount > 0 && tries < 24) {
                delay(5_000)
                refresh(silent = true)
                tries++
            }
        }
    }

    fun setAutoSummary(enabled: Boolean) {
        appPrefs.edit().putBoolean("auto_analyze", enabled).apply()
        _state.value = _state.value.copy(autoSummaryEnabled = enabled)
    }

    fun setImportantFilter(enabled: Boolean) {
        appPrefs.edit()
            .putStringSet(
                "important_categories",
                if (enabled) ALL_CALL_CATEGORIES.toSet() else emptySet(),
            )
            .apply()
        _state.value = _state.value.copy(importantFilterEnabled = enabled)
        applyFilter()
    }

    // 설정 화면에서 카테고리 변경 시 홈 목록 갱신용
    fun refreshFilter() {
        syncSettingsFromPrefs()
        applyFilter()
    }

    fun syncSettingsFromPrefs() {
        val categories = getImportantCategories()
        _state.value = _state.value.copy(
            autoSummaryEnabled = appPrefs.getBoolean("auto_analyze", false),
            importantFilterEnabled = categories.isNotEmpty(),
        )
        applyFilter()
    }

    private fun getImportantCategories(): Set<String> {
        return appPrefs.getStringSet(
            "important_categories",
            setOf("예약", "취소", "불만", "문의")
        ) ?: setOf("예약", "취소", "불만", "문의")
    }

    private fun applyFilter() {
        val filtered = if (_state.value.importantFilterEnabled) {
            val categories = getImportantCategories()
            _allCalls.filter { it.category in categories }
        } else {
            _allCalls
        }
        _state.value = _state.value.copy(recentCalls = filtered.take(20))
    }

    fun findLoadedCall(callId: String): Call? =
        _allCalls.firstOrNull { it.id == callId }
            ?: _state.value.recentCalls.firstOrNull { it.id == callId }

    fun deleteCall(callId: String) {
        viewModelScope.launch {
            callRepo.deleteCall(callId).onFailure {
                _state.value = _state.value.copy(error = it.message)
            }
            runCatching { recordingDao.deleteByServerCallId(callId) }
            calendarRepo.deleteLinkedCallEvents(callId)
            // 로컬 목록에서도 즉시 제거
            _allCalls = _allCalls.filter { it.id != callId }
            applyFilter()
        }
    }

    /** 발신자 번호/이름 수정 → 서버 저장 후 목록 반영 */
    fun updateCaller(callId: String, number: String, name: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val n = number.trim()
            val nm = name.trim()
            callRepo.updateCaller(callId, n, nm).onSuccess {
                // 즉시 로컬 반영
                _allCalls = _allCalls.map {
                    if (it.id == callId) it.copy(
                        callerNumber = n.ifBlank { null },
                        callerName = nm.ifBlank { null },
                    ) else it
                }
                applyFilter()
                onSuccess()
                // 서버에서 재동기화
                refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(loading = false, error = null)
            val storeId = storeRepo.activeStoreId()

            val callsDeferred = async {
                runCatching {
                    withTimeout(10_000L) {
                        loadCallsSnapshot(storeId)
                    }
                }
            }
            viewModelScope.launch {
                val schedulesResult = runCatching {
                    val from = todayDateString()
                    val to = dateStringAfter(days = 30)
                    val manualEvents = calendarRepo
                        .getManualEventsInRange(from = from, to = to)
                        .getOrDefault(emptyList())
                        .map { it.toHomeCalendarEvent() }
                    val hasExternalConnection = hasExternalCalendarConnection ?: run {
                        val connectionsResult = runCatching {
                            withTimeout(8_000L) {
                                calendarRepo.getConnections()
                            }
                        }.getOrNull()
                        connectionsResult?.fold(
                            onSuccess = { connections ->
                                hasExternalCalendarConnection = connections.isNotEmpty()
                                connections.isNotEmpty()
                            },
                            onFailure = {
                                null
                            },
                        ) ?: false
                    }
                    val serverEvents = if (hasExternalConnection) {
                        runCatching {
                            withTimeout(6_000L) {
                                calendarRepo
                                .getEventsInRange(from = from, to = to, limit = 100)
                                .getOrDefault(emptyList())
                            }
                        }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }

                    (serverEvents + manualEvents)
                        .filter { it.isUpcoming() }
                        .sortedWith(compareBy<CalendarEvent> { it.startAt.orEmpty() }.thenBy { it.time })
                }
                _state.value = _state.value.copy(
                    schedules = schedulesResult.getOrDefault(emptyList()),
                )
            }
            viewModelScope.launch {
                val customersResult = runCatching {
                    withTimeout(2_000L) {
                        api.listCustomers().customers
                    }
                }
                val serverCustomers = customersResult.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    pinnedCustomers = mergeManualPinnedCustomers(serverCustomers)
                        .filter { it.isPinned }
                        .sortedWith(compareByDescending<CustomerListItem> { it.callCount }.thenByDescending { it.lastCallAt ?: "" })
                        .take(3),
                )
            }

            val callsResult = callsDeferred.await()

            callsResult.fold(
                onSuccess = { snapshot ->
                    val calls = snapshot.calls.distinctBy { it.id }
                    val awaitingCount = recordingDao.countByStatus("AWAITING_APPROVAL")
                    _allCalls = calls

                    // 서버 상태에 맞춰 로컬 진행상태(UPLOADED/PROCESSING) 정리 → 진행 칩에서 사라짐
                    val summarizedIds = calls
                        .filter {
                            it.status.equals(CallStatus.COMPLETED, true) ||
                                    it.status.equals(CallStatus.SUMMARIZED, true) ||
                                    !it.summary.isNullOrBlank()
                        }
                        .map { it.id }.toSet()
                    // 완료(summarized)/실패(failed)/요약이 생긴 건은 "진행 끝"으로 보고 칩에서 내림
                    val terminalIds = calls
                        .filter {
                            it.status.equals(CallStatus.SUMMARIZED, true) ||
                                    it.status.equals(CallStatus.COMPLETED, true) ||
                                    it.status.equals(CallStatus.FAILED, true) ||
                                    it.status.equals(CallStatus.ERROR, true) ||
                                    !it.summary.isNullOrBlank()
                        }
                        .map { it.id }.toSet()
                    retryStuckAnalysisCalls(calls)
                    if (terminalIds.isNotEmpty()) {
                        var doneCount = 0
                        recordingDao.getServerProcessing().forEach { rec ->
                            val sid = rec.serverCallId ?: return@forEach
                            if (sid in terminalIds) {
                                recordingDao.markDoneByServerCallId(sid)
                                if (sid in summarizedIds) doneCount++   // 알림은 성공한 건만
                            }
                        }
                        markMissingProcessingAsFailed(calls, snapshot.listedStoreIds)
                        if (doneCount > 0) {
                            CallRecorderApp.instance.notifyAnalysisDone(doneCount)
                        }
                    } else {
                        markMissingProcessingAsFailed(calls, snapshot.listedStoreIds)
                    }
                    autoRegisterDetectedSchedules(calls)
                    val categories = getImportantCategories()
                    val filtered = if (_state.value.importantFilterEnabled) {
                        calls.filter { it.category in categories }
                    } else {
                        calls
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        todayTotal = countToday(calls),
                        todaySummarized = countTodaySummarized(calls),
                        todayScheduled = countTodayScheduled(calls),
                        recentCalls = filtered.take(20),
                        pendingApprovalCount = awaitingCount,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    private suspend fun loadCallsSnapshot(activeStoreId: String?): CallsSnapshot {
        val processingStores = recordingDao.getServerProcessing()
            .map { it.storeId }
            .filter { it.isNotBlank() }
        val storeIds = (listOfNotNull(activeStoreId) + processingStores)
            .distinct()

        if (storeIds.isEmpty()) {
            return CallsSnapshot(callRepo.listCalls(null).getOrThrow(), emptySet())
        }

        val calls = mutableListOf<Call>()
        val listedStoreIds = mutableSetOf<String>()
        storeIds.forEach { id ->
            callRepo.listCalls(id).onSuccess {
                calls += it
                listedStoreIds += id
            }
        }
        return CallsSnapshot(calls.distinctBy { it.id }, listedStoreIds)
    }

    private suspend fun markMissingProcessingAsFailed(
        calls: List<Call>,
        listedStoreIds: Set<String>,
    ) {
        val visibleServerIds = calls.map { it.id }.toSet()
        val staleThreshold = System.currentTimeMillis() - 10 * 60 * 1000L
        recordingDao.getServerProcessing().forEach { rec ->
            val sid = rec.serverCallId ?: return@forEach
            if (
                rec.storeId in listedStoreIds &&
                sid !in visibleServerIds &&
                rec.updatedAt < staleThreshold
            ) {
                recordingDao.setError(
                    rec.id,
                    RecordingStatus.FAILED,
                    "서버에서 분석 상태를 찾을 수 없어요. 다시 업로드해 주세요.",
                )
            }
        }
    }

    private suspend fun autoRegisterDetectedSchedules(calls: List<Call>) {
        calls
            .filter { call ->
                call.status.equals(CallStatus.COMPLETED, true) ||
                        call.status.equals(CallStatus.SUMMARIZED, true) ||
                        !call.summary.isNullOrBlank()
            }
            .forEach { call ->
                val schedule = call.detectScheduleCandidate() ?: return@forEach
                calendarRepo.saveManualEvent(
                    ManualCalendarEventEntity(
                        id = "auto-call-${call.id}",
                        title = call.detectScheduleTitle(),
                        date = schedule.date,
                        time = schedule.time,
                        description = call.summary?.takeIf { it.isNotBlank() }
                            ?: "통화에서 자동 추출된 일정입니다.",
                        chip = call.detectScheduleChip(),
                        reminderEnabled = true,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
    }

    private fun mergeManualPinnedCustomers(serverCustomers: List<CustomerListItem>): List<CustomerListItem> {
        val manualCustomers = loadManualCustomers()
        val manualByKey = manualCustomers.associateBy { it.phone.normalizedPhoneKey() }
        val mergedServer = serverCustomers.map { server ->
            val local = manualByKey[server.phone.normalizedPhoneKey()]
            if (local == null) {
                server
            } else {
                server.copy(
                    name = server.name?.takeIf { it.isNotBlank() } ?: local.name,
                    callCount = maxOf(server.callCount, local.callCount),
                    lastCallAt = server.lastCallAt ?: local.lastCallAt,
                    latestSummary = server.latestSummary ?: local.latestSummary,
                    isPinned = server.isPinned || local.isPinned,
                )
            }
        }
        val serverKeys = mergedServer.map { it.phone.normalizedPhoneKey() }.toSet()
        return mergedServer + manualCustomers.filter { it.phone.normalizedPhoneKey() !in serverKeys }
    }

    private fun loadManualCustomers(): List<CustomerListItem> {
        val raw = manualCustomerPrefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val phone = item.optString("phone").takeIf { it.isNotBlank() } ?: continue
                    add(
                        CustomerListItem(
                            phone = phone,
                            name = item.optString("name").takeIf { it.isNotBlank() },
                            callCount = item.optInt("callCount", 0),
                            lastCallAt = item.optString("lastCallAt").takeIf { it.isNotBlank() },
                            latestSummary = item.optString("lastSummary").takeIf { it.isNotBlank() },
                            isPinned = item.optBoolean("isPinned", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun String.normalizedPhoneKey(): String = filter { it.isDigit() }

    private suspend fun retryStuckAnalysisCalls(calls: List<Call>) {
        val now = System.currentTimeMillis()
        val uploadedIds = calls
            .filter { it.status.equals(CallStatus.UPLOADED, true) && it.summary.isNullOrBlank() }
            .map { it.id }
            .toSet()
        val staleProcessingIds = calls
            .filter {
                it.status.equals(CallStatus.PROCESSING, true) &&
                        it.summary.isNullOrBlank() &&
                        it.sttResult.isNullOrBlank() &&
                        isOlderThan(it.createdAt, now, 2 * 60 * 1000L)
            }
            .map { it.id }
            .toSet()

        uploadedIds
            .filter { retriedUploadedProcessIds.add(it) }
            .forEach { callRepo.triggerProcess(it) }
        staleProcessingIds
            .filter { retriedServerProcessingIds.add(it) }
            .forEach { callRepo.triggerProcess(it) }

        val staleThreshold = System.currentTimeMillis() - 30_000L
        recordingDao.getServerProcessing().forEach { rec ->
            val sid = rec.serverCallId ?: return@forEach
            if ((sid in uploadedIds || sid in staleProcessingIds) && rec.updatedAt < staleThreshold) {
                callRepo.triggerProcess(sid).onSuccess {
                    recordingDao.updateStatus(rec.id, RecordingStatus.PROCESSING)
                }
            }
        }
    }

    private fun isOlderThan(createdAt: String?, now: Long, durationMillis: Long): Boolean {
        val created = createdAt?.let { parseServerDate(it) } ?: return true
        return now - created.time > durationMillis
    }

    /** 업로드 진행 목록에서 한 건 제거 (큐에서 취소). 목록은 Flow로 자동 갱신됨. */
    fun deleteUpload(id: Long) {
        viewModelScope.launch {
            callRepo.cancelUpload(id).onFailure {
                _state.value = _state.value.copy(error = it.message ?: "서버 분석 취소에 실패했어요")
            }
        }
    }

    /** 실패한 업로드를 사용자가 직접 다시 시도. */
    fun retryUpload(id: Long) {
        viewModelScope.launch {
            val storeId = storeRepo.ensureActiveStoreId().getOrNull()
            if (!storeId.isNullOrBlank()) {
                recordingDao.updateStoreId(id, storeId)
            }
            recordingDao.updateStatus(id, RecordingStatus.PENDING)
            pendingUploadKickStarted = false
            UploadWorker.enqueueOneShot(CallRecorderApp.instance.applicationContext)
        }
    }

    /** 업로드 진행 목록 전체 일괄 제거. */
    fun deleteAllUploads() {
        viewModelScope.launch {
            callRepo.cancelAllUploads().onFailure {
                _state.value = _state.value.copy(error = it.message ?: "서버 분석 취소에 실패했어요")
            }
        }
    }

    fun approveAll() {
        viewModelScope.launch {
            recordingDao.approveAll()
            com.callrecorder.app.worker.UploadWorker.enqueueOneShot(
                CallRecorderApp.instance.applicationContext
            )
            refresh()
        }
    }

    private data class AutoScheduleCandidate(
        val date: String,
        val time: String,
    )

    private fun Call.detectScheduleCandidate(): AutoScheduleCandidate? {
        val info = extractedInfoOrNull()
        val base = createdAt.toAutoScheduleBase()
        val infoDate = info?.date?.takeIf { it.isNotBlank() }?.toAutoScheduleDateOrNull(base)
        if (infoDate != null) {
            return AutoScheduleCandidate(
                date = infoDate,
                time = info.time?.takeIf { it.isNotBlank() }?.toAutoScheduleTimeOrNull() ?: "00:00",
            )
        }

        val scheduleLabels = listOf(
            "방문", "일정", "날짜", "예약", "시간", "미팅", "상담",
            "visit", "schedule", "date", "reservation", "appointment", "meeting", "time",
        )
        val texts = buildList {
            internalKeywordsMap()
                .filter { (label, value) -> value.isNotBlank() && scheduleLabels.any { label.contains(it, ignoreCase = true) } }
                .forEach { (label, value) ->
                    add("$label $value")
                    add(value)
                }
            info?.date?.takeIf { it.isNotBlank() }?.let { add(it) }
            info?.time?.takeIf { it.isNotBlank() }?.let { add(it) }
            sttResult?.takeIf { it.isNotBlank() }?.let { add(it) }
            info?.specialNotes?.takeIf { it.isNotBlank() }?.let { add(it) }
            summary?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        texts.forEach { text ->
            val date = text.toAutoScheduleDateOrNull(base) ?: return@forEach
            val time = text.toAutoScheduleTimeOrNull() ?: "00:00"
            return AutoScheduleCandidate(date = date, time = time)
        }
        return null
    }

    private fun Call.detectScheduleTitle(): String =
        callerName?.takeIf { it.isNotBlank() }
            ?: callerNumber?.takeIf { it.isNotBlank() }
            ?: extractedInfoOrNull()?.customerName?.takeIf { it.isNotBlank() }
            ?: "통화 일정"

    private fun Call.detectScheduleChip(): String {
        val info = extractedInfoOrNull()
        val raw = buildString {
            append(category.orEmpty()).append(' ')
            append(info?.categoryCode.orEmpty()).append(' ')
            append(info?.specialNotes.orEmpty()).append(' ')
            append(summary.orEmpty()).append(' ')
            append(sttResult.orEmpty()).append(' ')
            internalKeywordsMap().forEach { (label, value) ->
                append(label).append(' ').append(value).append(' ')
            }
        }.lowercase()

        return when {
            raw.contains("예약") ||
                    raw.contains("reservation") ||
                    raw.contains("appointment") ||
                    raw.contains("방문") ||
                    raw.contains("미팅") ||
                    raw.contains("상담") ||
                    raw.contains("일정") -> "RESERVATION"
            raw.contains("문의") ||
                    raw.contains("inquiry") ||
                    raw.contains("question") -> "INQUIRY"
            else -> "OTHER"
        }
    }

    private fun countToday(calls: List<Call>): Int =
        calls.count { isToday(it.createdAt) }

    private fun countTodaySummarized(calls: List<Call>): Int =
        calls.count {
            isToday(it.createdAt) &&
                    (
                            it.status.equals(CallStatus.COMPLETED, true) ||
                                    it.status.equals(CallStatus.SUMMARIZED, true) ||
                                    !it.summary.isNullOrBlank()
                            )
        }

    private fun countTodayScheduled(calls: List<Call>): Int =
        calls.count { isToday(it.createdAt) && it.category == "예약" }

    private fun isToday(createdAt: String?): Boolean {
        if (createdAt.isNullOrBlank()) return false
        val date = parseServerDate(createdAt) ?: return false
        val cal = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    }

    private fun parseServerDate(s: String): Date? {
        val fmts = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        )
        for (fmt in fmts) {
            try {
                return SimpleDateFormat(fmt, Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(s) ?: continue
            } catch (_: Exception) {}
        }
        return null
    }

    private fun todayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun dateStringAfter(days: Int): String {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, days)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun CalendarEvent.isUpcoming(): Boolean {
        val start = parseScheduleStart(this) ?: return true
        return start.time >= System.currentTimeMillis()
    }

    private fun parseScheduleStart(event: CalendarEvent): Date? {
        event.startAt?.takeIf { it.isNotBlank() }?.let { raw ->
            parseScheduleDate(raw)?.let { return it }
        }

        val datePart = event.startAt
            ?.substringBefore("T")
            ?.substringBefore(" ")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val timePart = event.time.takeIf { it.isNotBlank() } ?: "00:00"
        return parseScheduleDate("$datePart ${timePart.withSeconds()}")
    }

    private fun parseScheduleDate(raw: String): Date? {
        val normalized = raw.trim()
        val patterns = listOf(
            "yyyy-MM-dd" to null,
            "yyyy-MM-dd HH:mm:ss" to null,
            "yyyy-MM-dd HH:mm" to null,
            "yyyy-MM-dd'T'HH:mm:ss" to null,
            "yyyy-MM-dd'T'HH:mm" to null,
            "yyyy-MM-dd'T'HH:mm:ss'Z'" to TimeZone.getTimeZone("UTC"),
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" to TimeZone.getTimeZone("UTC"),
        )
        for ((pattern, zone) in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US)
                    .apply { zone?.let { timeZone = it } }
                    .parse(normalized) ?: continue
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun ManualCalendarEventEntity.toHomeCalendarEvent(): CalendarEvent {
        val normalizedTime = time.ifBlank { "00:00" }
        val startAt = "$date ${normalizedTime.withSeconds()}"
        return CalendarEvent(
            id = "manual-$id",
            provider = "manual",
            title = title,
            time = normalizedTime,
            category = chip,
            description = description.ifBlank { "수동 등록 일정" },
            startAt = startAt,
            reminderEnabled = reminderEnabled,
        )
    }

    private fun String.toAutoScheduleDateOrNull(base: Calendar = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault())): String? {
        val text = trim()
        autoAbsoluteDateOrNull(text, base)?.let { return it }
        autoRelativeDateOrNull(text, base)?.let { return it }
        autoWeekdayDateOrNull(text, base)?.let { return it }
        return null
    }

    private fun autoAbsoluteDateOrNull(text: String, base: Calendar): String? {
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
            return autoMakeDateOrNull(year, month, day)
        }

        val monthDayMatchers = listOf(
            Regex("""(?<!\d)(\d{1,2})\s*월\s*(\d{1,2})\s*일"""),
            Regex("""(?<!\d)(\d{1,2})[./]\s*(\d{1,2})(?!\d)"""),
        )
        for (regex in monthDayMatchers) {
            val match = regex.find(text) ?: continue
            val month = match.groupValues[1].toIntOrNull() ?: continue
            val day = match.groupValues[2].toIntOrNull() ?: continue
            return autoMonthDayDate(month, day, null, base)
        }
        autoEnglishMonthDayOrNull(lower, base)?.let { return it }
        return null
    }

    private fun autoRelativeDateOrNull(text: String, base: Calendar): String? {
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
        return base.autoCopyInDeviceZone().apply { add(Calendar.DAY_OF_MONTH, days) }.autoDateString()
    }

    private fun autoWeekdayDateOrNull(text: String, base: Calendar): String? {
        val lower = text.lowercase(Locale.US)
        val koreanWeekdays = listOf(
            "일요일" to Calendar.SUNDAY, "월요일" to Calendar.MONDAY, "화요일" to Calendar.TUESDAY,
            "수요일" to Calendar.WEDNESDAY, "목요일" to Calendar.THURSDAY, "금요일" to Calendar.FRIDAY,
            "토요일" to Calendar.SATURDAY, "일욜" to Calendar.SUNDAY, "월욜" to Calendar.MONDAY,
            "화욜" to Calendar.TUESDAY, "수욜" to Calendar.WEDNESDAY, "목욜" to Calendar.THURSDAY,
            "금욜" to Calendar.FRIDAY, "토욜" to Calendar.SATURDAY, "일" to Calendar.SUNDAY,
            "월" to Calendar.MONDAY, "화" to Calendar.TUESDAY, "수" to Calendar.WEDNESDAY,
            "목" to Calendar.THURSDAY, "금" to Calendar.FRIDAY, "토" to Calendar.SATURDAY,
        )
        val englishWeekdays = listOf(
            "sunday" to Calendar.SUNDAY, "sun" to Calendar.SUNDAY,
            "monday" to Calendar.MONDAY, "mon" to Calendar.MONDAY,
            "tuesday" to Calendar.TUESDAY, "tue" to Calendar.TUESDAY, "tues" to Calendar.TUESDAY,
            "wednesday" to Calendar.WEDNESDAY, "wed" to Calendar.WEDNESDAY,
            "thursday" to Calendar.THURSDAY, "thu" to Calendar.THURSDAY, "thur" to Calendar.THURSDAY, "thurs" to Calendar.THURSDAY,
            "friday" to Calendar.FRIDAY, "fri" to Calendar.FRIDAY,
            "saturday" to Calendar.SATURDAY, "sat" to Calendar.SATURDAY,
        )
        val target = koreanWeekdays.firstOrNull { (label, _) ->
            Regex("""(?<![가-힣])${Regex.escape(label)}(?:요일|욜)?(?![가-힣])""").containsMatchIn(text)
        }?.second ?: englishWeekdays.firstOrNull { (label, _) ->
            Regex("""\b${Regex.escape(label)}(?:day)?\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
        }?.second ?: return null

        val result = base.autoCopyInDeviceZone()
        val current = result.get(Calendar.DAY_OF_WEEK)
        var diff = (target - current + 7) % 7
        if (text.contains("다음주") || text.contains("다음 주") || Regex("""\bnext\s+week\b""").containsMatchIn(lower)) {
            diff = autoDaysUntilNextWeekdayFromMonday(result, target)
        } else if (Regex("""\bnext\b""").containsMatchIn(lower)) {
            diff = if (diff == 0) 7 else diff
        } else if (diff == 0 && !text.contains("오늘") && !text.contains("이번") && !Regex("""\btoday\b|\bthis\b""").containsMatchIn(lower)) {
            diff = 7
        }
        return result.apply { add(Calendar.DAY_OF_MONTH, diff) }.autoDateString()
    }

    private fun String.toAutoScheduleTimeOrNull(): String? {
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
        Regex("""(\d{1,2})\s*:\s*(\d{1,2})""").find(text)?.let {
            val hour = it.groupValues[1].toIntOrNull() ?: return null
            val minute = it.groupValues[2].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return "%02d:%02d".format(hour, minute)
        }
        val korean = Regex("""(\d{1,2})\s*시(?:\s*(\d{1,2})\s*분)?""").find(text) ?: return null
        val hour = korean.groupValues[1].toIntOrNull() ?: return null
        val minute = korean.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return "%02d:%02d".format(hour, minute)
    }

    private fun String?.toAutoScheduleBase(): Calendar {
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

    private val autoEnglishMonths = mapOf(
        "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
        "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9, "october" to 10,
        "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
    )

    private fun autoEnglishMonthDayOrNull(text: String, base: Calendar): String? {
        val monthAlternatives = autoEnglishMonths.keys.joinToString("|") { Regex.escape(it) }
        Regex("""\b($monthAlternatives)\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""").find(text)?.let {
            val month = autoEnglishMonths[it.groupValues[1].trimEnd('.')] ?: return@let
            val day = it.groupValues[2].toIntOrNull() ?: return@let
            return autoMonthDayDate(month, day, it.groupValues.getOrNull(3), base)
        }
        Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($monthAlternatives)\.?(?:,?\s+(\d{4}))?\b""").find(text)?.let {
            val day = it.groupValues[1].toIntOrNull() ?: return@let
            val month = autoEnglishMonths[it.groupValues[2].trimEnd('.')] ?: return@let
            return autoMonthDayDate(month, day, it.groupValues.getOrNull(3), base)
        }
        return null
    }

    private fun autoMonthDayDate(month: Int, day: Int, yearText: String?, base: Calendar): String? {
        val explicitYear = yearText?.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (explicitYear != null) return autoMakeDateOrNull(explicitYear, month, day)
        val candidate = base.autoCopyInDeviceZone().apply {
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        if (!autoIsValidDate(candidate, month, day)) return null
        if (candidate.before(base.autoStartOfDay())) candidate.add(Calendar.YEAR, 1)
        return candidate.autoDateString()
    }

    private fun autoMakeDateOrNull(year: Int, month: Int, day: Int): String? {
        if (year !in 2000..2100 || month !in 1..12 || day !in 1..31) return null
        val candidate = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, day)
        }
        if (!autoIsValidDate(candidate, month, day)) return null
        return candidate.autoDateString()
    }

    private fun autoIsValidDate(calendar: Calendar, month: Int, day: Int): Boolean =
        calendar.get(Calendar.MONTH) == month - 1 && calendar.get(Calendar.DAY_OF_MONTH) == day

    private fun Calendar.autoCopyInDeviceZone(): Calendar =
        (clone() as Calendar).apply { timeZone = TimeZone.getDefault() }

    private fun Calendar.autoStartOfDay(): Calendar =
        autoCopyInDeviceZone().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun Calendar.autoDateString(): String =
        "%04d-%02d-%02d".format(get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH))

    private fun autoDaysUntilNextWeekdayFromMonday(base: Calendar, targetDayOfWeek: Int): Int {
        val daysUntilNextMonday = (Calendar.MONDAY - base.get(Calendar.DAY_OF_WEEK) + 7) % 7
            .let { if (it == 0) 7 else it }
        val targetOffsetFromMonday = (targetDayOfWeek - Calendar.MONDAY + 7) % 7
        return daysUntilNextMonday + targetOffsetFromMonday
    }

    private fun String.withSeconds(): String =
        if (count { it == ':' } >= 2) this else "$this:00"
}
