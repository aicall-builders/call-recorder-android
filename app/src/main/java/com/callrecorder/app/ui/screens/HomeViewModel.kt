package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.RecordingStatus
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.data.model.CallStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class UploadItem(
    val id: Long,
    val name: String,
    val phase: String,   // "대기중" / "업로드중" / "분석중"
)

data class HomeUiState(
    val loading: Boolean = false,
    val todayTotal: Int = 0,
    val todaySummarized: Int = 0,
    val todayScheduled: Int = 0,
    val recentCalls: List<Call> = emptyList(),
    val pendingApprovalCount: Int = 0,
    val schedules: List<CalendarEvent> = emptyList(),
    val error: String? = null,
    val autoSummaryEnabled: Boolean = true,
    val importantFilterEnabled: Boolean = false,
    val uploadingCount: Int = 0,
    val activeUploads: List<UploadItem> = emptyList(),
)

class HomeViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val callRepo = container.callRepo
    private val storeRepo = container.storeRepo
    private val recordingDao = container.recordingDao
    private val calendarRepo = container.calendarRepo

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // 홈 토글 설정 저장
    private val homePrefs by lazy {
        CallRecorderApp.instance.getSharedPreferences("home_settings", android.content.Context.MODE_PRIVATE)
    }

    // 중요 통화 카테고리 설정 읽기
    private val appPrefs by lazy {
        CallRecorderApp.instance.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    }

    private var _allCalls: List<Call> = emptyList()
    private var pollJob: Job? = null

    init {
        val autoSummary = homePrefs.getBoolean("auto_summary", true)
        val importantFilter = homePrefs.getBoolean("important_filter", false)
        _state.value = _state.value.copy(
            autoSummaryEnabled = autoSummary,
            importantFilterEnabled = importantFilter,
        )
        refresh()
        observeUploads()
    }

    /** 업로드 진행 개수를 실시간 관찰 -> 홈 배너 반영 */
    private fun observeUploads() {
        viewModelScope.launch {
            recordingDao.observeActiveUploads().collect { list ->
                val items = list.map {
                    UploadItem(
                        id = it.id,
                        name = it.fileName,
                        phase = when (it.status) {
                            RecordingStatus.PENDING -> "대기중"
                            RecordingStatus.UPLOADING -> "업로드중"
                            else -> "분석중"   // UPLOADED / PROCESSING
                        },
                    )
                }
                _state.value = _state.value.copy(
                    uploadingCount = items.size,
                    activeUploads = items,
                )
                // 진행 중 항목이 있으면 서버 분석이 끝날 때까지 주기적으로 확인해 자동 정리
                if (items.isNotEmpty()) startPollingForCompletion()
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
        homePrefs.edit().putBoolean("auto_summary", enabled).apply()
        _state.value = _state.value.copy(autoSummaryEnabled = enabled)
    }

    fun setImportantFilter(enabled: Boolean) {
        homePrefs.edit().putBoolean("important_filter", enabled).apply()
        _state.value = _state.value.copy(importantFilterEnabled = enabled)
        applyFilter()
    }

    // 설정 화면에서 카테고리 변경 시 홈 목록 갱신용
    fun refreshFilter() {
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

    fun deleteCall(callId: String) {
        viewModelScope.launch {
            callRepo.deleteCall(callId)
            // 로컬 목록에서도 즉시 제거
            _allCalls = _allCalls.filter { it.id != callId }
            applyFilter()
        }
    }

    /** 발신자 번호/이름 수정 → 서버 저장 후 목록 반영 */
    fun updateCaller(callId: String, number: String, name: String) {
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
                // 서버에서 재동기화
                refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(loading = true, error = null)
            val storeId = storeRepo.activeStoreId()

            val callsDeferred = async { callRepo.listCalls(storeId) }
            val schedulesDeferred = async { calendarRepo.getEvents() }

            val callsResult = callsDeferred.await()
            val schedulesResult = schedulesDeferred.await()

            callsResult.fold(
                onSuccess = { allCalls ->
                    val calls = allCalls.distinctBy { it.id }
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
                            it.status.equals(CallStatus.COMPLETED, true) ||
                                    it.status.equals(CallStatus.SUMMARIZED, true) ||
                                    it.status.equals(CallStatus.FAILED, true) ||
                                    it.status.equals(CallStatus.ERROR, true) ||
                                    !it.summary.isNullOrBlank()
                        }
                        .map { it.id }.toSet()
                    if (terminalIds.isNotEmpty()) {
                        var doneCount = 0
                        recordingDao.getServerProcessing().forEach { rec ->
                            val sid = rec.serverCallId ?: return@forEach
                            if (sid in terminalIds) {
                                recordingDao.markDoneByServerCallId(sid)
                                if (sid in summarizedIds) doneCount++   // 알림은 성공한 건만
                            }
                        }
                        if (doneCount > 0) {
                            CallRecorderApp.instance.notifyAnalysisDone(doneCount)
                        }
                    }
                    // 안전장치: 매칭 실패/서버 지연으로 2분 넘게 분석중인 건은 강제로 칩에서 내림
                    recordingDao.markStaleProcessingDone(
                        System.currentTimeMillis() - 2 * 60 * 1000L
                    )

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
                        schedules = schedulesResult.getOrDefault(emptyList()),
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    /** 업로드 진행 목록에서 한 건 제거 (큐에서 취소). 목록은 Flow로 자동 갱신됨. */
    fun deleteUpload(id: Long) {
        viewModelScope.launch {
            callRepo.cancelUpload(id).onFailure {
                _state.value = _state.value.copy(error = it.message ?: "서버 분석 취소에 실패했어요")
            }
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
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.callrecorder.app.worker.ScanAndUploadWorker>()
                .build()
            androidx.work.WorkManager.getInstance(
                CallRecorderApp.instance.applicationContext
            ).enqueue(workRequest)
            refresh()
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
}
