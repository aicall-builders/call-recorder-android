package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CustomerAnalysis
import com.callrecorder.app.data.model.CustomerProfile
import com.callrecorder.app.data.model.UpdateCustomerRequest
import com.callrecorder.app.data.model.extractedInfoOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 고객 등급 (통화 횟수 기준 자동) */
enum class CustomerGrade(val label: String) {
    VIP("VIP"),       // >= 7
    REGULAR("단골"),  // >= 4
    NORMAL("일반"),   // >= 2
    NEW("신규");      // = 1

    companion object {
        fun of(callCount: Int): CustomerGrade = when {
            callCount >= 7 -> VIP
            callCount >= 4 -> REGULAR
            callCount >= 2 -> NORMAL
            else -> NEW
        }
    }
}

data class CustomerUiItem(
    val phone: String,
    val name: String?,
    val callCount: Int,
    val lastCallAt: String?,
    val lastSummary: String?,
    val categories: List<String>,
    val calls: List<Call>,
    val grade: CustomerGrade,
) {
    val isVip: Boolean get() = grade == CustomerGrade.VIP
}

data class CustomerUiState(
    val loading: Boolean = false,
    val customers: List<CustomerUiItem> = emptyList(),
    val error: String? = null,
)

/** 통화별 메모/사진 (히스토리 타임라인용) */
data class CustomerCallNote(
    val memo: String = "",
    val photoUrls: List<String> = emptyList(),
)

/** 고객 상세(프로필+분석+메모/사진) 상태 */
data class CustomerDetailState(
    val loading: Boolean = false,
    val profile: CustomerProfile? = null,
    val analysis: CustomerAnalysis? = null,
    val notes: Map<String, CustomerCallNote> = emptyMap(),   // callId → 메모/사진
    val saving: Boolean = false,
    val saveMessage: String? = null,
)

class CustomerViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val callRepo = container.callRepo
    private val storeRepo = container.storeRepo
    private val notesRepo = container.notesRepo
    private val api = container.api

    private val _state = MutableStateFlow(CustomerUiState())
    val state: StateFlow<CustomerUiState> = _state.asStateFlow()

    private val _detail = MutableStateFlow(CustomerDetailState())
    val detail: StateFlow<CustomerDetailState> = _detail.asStateFlow()

    init { loadCustomers() }

    fun loadCustomers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val storeId = storeRepo.activeStoreId()
            callRepo.listCalls(storeId).fold(
                onSuccess = { allCalls ->
                    // 그룹핑 키: 번호가 있으면 번호(숫자만), 없으면 이름.
                    //  - caller_number엔 이제 진짜 번호만 들어옴(저장 로직 수정).
                    //  - 번호 없는 과거/이름통화는 이름으로 묶어 한 고객으로.
                    fun groupKey(c: Call): String? {
                        val num = c.callerNumber?.filter { ch -> ch.isDigit() }?.takeIf { it.length >= 7 }
                        if (num != null) return "num:$num"
                        val nm = c.callerName?.takeIf { it.isNotBlank() }
                            ?: c.extractedInfoOrNull()?.customerName?.takeIf { it.isNotBlank() }
                        if (nm != null) return "name:$nm"
                        return null
                    }

                    val grouped = allCalls
                        .filter { groupKey(it) != null }
                        .groupBy { groupKey(it)!! }

                    val customers = grouped.map { (_, calls) ->
                        val sorted = calls.sortedByDescending { it.createdAt }
                        val latest = sorted.first()
                        // 표시용 번호/이름: 그룹 내에서 실제 값이 있는 걸 채택
                        val phone = calls.mapNotNull { it.callerNumber }
                            .firstOrNull { it.filter { ch -> ch.isDigit() }.length >= 7 }
                            ?: latest.callerNumber ?: ""

                        CustomerUiItem(
                            phone = phone,
                            name = calls.mapNotNull { it.callerName?.takeIf { n -> n.isNotBlank() } }
                                .firstOrNull()
                                ?: calls.mapNotNull { it.extractedInfoOrNull()?.customerName }
                                    .firstOrNull { it.isNotBlank() },
                            callCount = calls.size,
                            lastCallAt = latest.createdAt,
                            lastSummary = latest.summary,
                            categories = calls.mapNotNull { it.category }.distinct(),
                            calls = sorted,
                            grade = CustomerGrade.of(calls.size),
                        )
                    }.sortedByDescending { it.callCount }

                    _state.value = CustomerUiState(loading = false, customers = customers)
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    fun refresh() = loadCustomers()

    /**
     * 고객 상세 진입: 프로필 + AI 분석 + 통화별 메모/사진을 한 번에 로드.
     * 메모/사진은 통화 건수만큼 병렬로 조회한다.
     */
    fun enterDetail(customer: CustomerUiItem) {
        val phone = customer.phone
        val callIds = customer.calls.map { it.id }
        viewModelScope.launch {
            _detail.value = CustomerDetailState(loading = true)

            // 1) 프로필 + 분석
            val resp = runCatching { api.getCustomer(phone) }.getOrNull()
            _detail.value = _detail.value.copy(
                loading = false,
                profile = resp?.profile,
                analysis = resp?.analysis,
            )

            // 2) 통화별 메모/사진 (병렬)
            if (callIds.isNotEmpty()) {
                val pairs = callIds.map { id ->
                    async { id to notesRepo.getNote(id).getOrNull() }
                }.awaitAll()
                val map = pairs.mapNotNull { (id, note) ->
                    note?.let {
                        id to CustomerCallNote(
                            memo = it.memo,
                            photoUrls = it.photos.mapNotNull { p -> p.url },
                        )
                    }
                }.toMap()
                _detail.value = _detail.value.copy(notes = map)
            }
        }
    }

    /** 프로필/분석만 재조회 (메모/사진은 유지). 편집 저장 후 갱신용. */
    fun loadDetail(phone: String) {
        viewModelScope.launch {
            val resp = runCatching { api.getCustomer(phone) }.getOrNull()
            _detail.value = _detail.value.copy(
                profile = resp?.profile,
                analysis = resp?.analysis,
            )
        }
    }

    /** 편집 필드 저장 */
    fun saveProfile(
        phone: String,
        email: String,
        tendency: String,
        medical: String,
        specialNotes: String,
        customFields: Map<String, String> = emptyMap(),
    ) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null)
            val req = UpdateCustomerRequest(
                email = email.ifBlank { null },
                tendency = tendency.ifBlank { null },
                medical = medical.ifBlank { null },
                specialNotes = specialNotes.ifBlank { null },
                customFields = customFields.ifEmpty { null },
            )
            runCatching { api.updateCustomer(phone, req) }.fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "저장됐어요")
                    loadDetail(phone)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "저장 실패: ${it.message}")
                },
            )
        }
    }

    fun clearSaveMessage() {
        _detail.value = _detail.value.copy(saveMessage = null)
    }
}