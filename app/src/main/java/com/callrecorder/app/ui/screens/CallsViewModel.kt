package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.CallCategory
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CallDetail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CallsViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val callRepo = container.callRepo
    private val dao = container.recordingDao

    private val _state = MutableStateFlow(CallsUiState())
    val state: StateFlow<CallsUiState> = _state.asStateFlow()

    /** 선택된 카테고리 탭 (기본: 미분류) */
    private val _selectedCategory = MutableStateFlow(CallCategory.UNCLASSIFIED)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    /** 검색어 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 검색어로 필터된 분석 완료 통화 (번호·요약·분류 매칭) */
    val filteredCalls: StateFlow<List<Call>> =
        combine(_state, _searchQuery) { st, q ->
            if (q.isBlank()) st.calls
            else {
                val query = q.trim().lowercase()
                st.calls.filter { c ->
                    (c.callerNumber?.lowercase()?.contains(query) == true) ||
                            (c.summary?.lowercase()?.contains(query) == true) ||
                            (c.category?.lowercase()?.contains(query) == true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 전체 로컬 녹음 (서버 업로드 진행 표시용) */
    val localRecordings: StateFlow<List<RecordingEntity>> =
        callRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 선택된 카테고리의 녹음 목록 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val categorizedRecordings: StateFlow<List<RecordingEntity>> =
        _selectedCategory.flatMapLatest { cat ->
            dao.observeByCategory(cat)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 카테고리별 카운트 (탭 배지용) */
    val unclassifiedCount: StateFlow<Int> =
        dao.observeCountByCategory(CallCategory.UNCLASSIFIED)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val businessCount: StateFlow<Int> =
        dao.observeCountByCategory(CallCategory.BUSINESS)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val personalCount: StateFlow<Int> =
        dao.observeCountByCategory(CallCategory.PERSONAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init { refresh() }

    /** 탭 전환 */
    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    /** 검색어 변경 */
    fun setSearch(q: String) {
        _searchQuery.value = q
    }

    /** 사용자가 통화를 수동으로 분류 변경 */
    fun classifyAs(recordingId: Long, category: String) {
        viewModelScope.launch {
            callRepo.updateCategory(recordingId, category)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val storeId = container.storeRepo.activeStoreId()
            callRepo.listCalls(storeId).fold(
                onSuccess = { _state.value = _state.value.copy(loading = false, calls = it, error = null) },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }
}

data class CallsUiState(
    val loading: Boolean = false,
    val calls: List<Call> = emptyList(),
    val error: String? = null,
)

class CallDetailViewModel : ViewModel() {
    private val callRepo = CallRecorderApp.instance.container.callRepo
    private val _state = MutableStateFlow(CallDetailUiState())
    val state: StateFlow<CallDetailUiState> = _state.asStateFlow()

    fun load(callId: String) {
        viewModelScope.launch {
            _state.value = CallDetailUiState(loading = true)
            callRepo.getDetail(callId).fold(
                onSuccess = { _state.value = CallDetailUiState(loading = false, detail = it) },
                onFailure = { _state.value = CallDetailUiState(loading = false, error = it.message) },
            )
        }
    }
}

data class CallDetailUiState(
    val loading: Boolean = false,
    val detail: CallDetail? = null,
    val error: String? = null,
)