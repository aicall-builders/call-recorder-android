package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CustomerAnalysis
import com.callrecorder.app.data.model.CustomerHistoryItem
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
    VIP("VIP"),       // >= 20
    REGULAR("단골"),  // >= 10
    NORMAL("일반"),   // 2~9
    NEW("신규");      // <= 1

    companion object {
        fun of(callCount: Int): CustomerGrade = when {
            callCount >= 20 -> VIP
            callCount >= 10 -> REGULAR
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
    val isPinned: Boolean = false,
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
    val photos: List<CustomerRelatedPhoto> = emptyList(),
)

data class CustomerRelatedPhoto(
    val id: String,
    val callId: String,
    val url: String,
)

data class CustomerManualPhoto(
    val id: String,
    val memoId: String,
    val url: String,
)

/** 고객 상세(프로필+분석+메모/사진) 상태 */
data class CustomerDetailState(
    val loading: Boolean = false,
    val profile: CustomerProfile? = null,
    val analysis: CustomerAnalysis? = null,
    val notes: Map<String, CustomerCallNote> = emptyMap(),   // callId → 메모/사진
    val manualHistory: List<CustomerHistoryItem> = emptyList(),
    val manualPhotos: List<CustomerManualPhoto> = emptyList(),
    val saving: Boolean = false,
    val saveMessage: String? = null,
)

class CustomerViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val callRepo = container.callRepo
    private val storeRepo = container.storeRepo
    private val notesRepo = container.notesRepo
    private val customerRepo = container.customerRepo
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
            val serverCustomers = runCatching { api.listCustomers().customers }.getOrDefault(emptyList())
            val serverByPhone = serverCustomers.associateBy { it.phone.filter { ch -> ch.isDigit() } }
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

                    val callCustomers = grouped.map { (_, calls) ->
                        val sorted = calls.sortedByDescending { it.createdAt }
                        val latest = sorted.first()
                        // 표시용 번호/이름: 그룹 내에서 실제 값이 있는 걸 채택
                        val phone = calls.mapNotNull { it.callerNumber }
                            .firstOrNull { it.filter { ch -> ch.isDigit() }.length >= 7 }
                            ?: latest.callerNumber ?: ""

                        val normalizedPhone = phone.filter { ch -> ch.isDigit() }
                        val server = serverByPhone[normalizedPhone]
                        val count = maxOf(calls.size, server?.callCount ?: 0)
                        CustomerUiItem(
                            phone = phone,
                            name = server?.name
                                ?: calls.mapNotNull { it.callerName?.takeIf { n -> n.isNotBlank() } }
                                    .firstOrNull()
                                ?: calls.mapNotNull { it.extractedInfoOrNull()?.customerName }
                                    .firstOrNull { it.isNotBlank() },
                            callCount = count,
                            lastCallAt = latest.createdAt ?: server?.lastCallAt,
                            lastSummary = server?.latestSummary ?: latest.summary,
                            categories = calls.mapNotNull { it.category }.distinct(),
                            calls = sorted,
                            grade = CustomerGrade.of(count),
                            isPinned = server?.isPinned == true,
                        )
                    }

                    val existingPhones = callCustomers.map { it.phone.filter { ch -> ch.isDigit() } }.toSet()
                    val pinnedOnly = serverCustomers
                        .filter { it.isPinned && it.phone.filter { ch -> ch.isDigit() } !in existingPhones }
                        .map {
                            CustomerUiItem(
                                phone = it.phone,
                                name = it.name,
                                callCount = it.callCount,
                                lastCallAt = it.lastCallAt,
                                lastSummary = it.latestSummary,
                                categories = listOfNotNull(it.latestCategory),
                                calls = emptyList(),
                                grade = CustomerGrade.of(it.callCount),
                                isPinned = true,
                            )
                        }

                    val customers = (pinnedOnly + callCustomers)
                        .sortedWith(
                            compareByDescending<CustomerUiItem> { it.isPinned }
                                .thenByDescending { it.callCount }
                                .thenByDescending { it.lastCallAt ?: "" }
                        )

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
            val historyItems = runCatching { api.getCustomerHistory(phone).items }
                .getOrDefault(emptyList())
            _detail.value = _detail.value.copy(
                loading = false,
                profile = resp?.profile,
                analysis = resp?.analysis,
                manualHistory = historyItems.filter { it.type == "manual_memo" },
                manualPhotos = historyItems
                    .filter { it.type == "manual_memo" }
                    .flatMap { item ->
                        item.photos.mapNotNull { photo ->
                            val memoId = item.id ?: return@mapNotNull null
                            val photoId = photo.id ?: return@mapNotNull null
                            val url = photo.url ?: return@mapNotNull null
                            CustomerManualPhoto(
                                id = photoId,
                                memoId = memoId,
                                url = url,
                            )
                        }
                    },
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
                            photos = it.photos.map { p ->
                                CustomerRelatedPhoto(
                                    id = p.id,
                                    callId = id,
                                    url = p.url,
                                )
                            },
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

    fun setPinned(customer: CustomerUiItem, pinned: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val req = UpdateCustomerRequest(isPinned = pinned)
            runCatching { api.updateCustomer(customer.phone, req) }.onSuccess {
                loadCustomers()
                loadDetail(customer.phone)
                onSuccess()
            }
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

    fun uploadRelatedPhoto(customer: CustomerUiItem, callId: String, fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null)
            notesRepo.uploadPhoto(callId, fileName, bytes).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "이미지가 추가됐어요")
                    enterDetail(customer)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "이미지 추가 실패: ${it.message}")
                },
            )
        }
    }

    fun deleteRelatedPhoto(customer: CustomerUiItem, photo: CustomerRelatedPhoto) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null)
            notesRepo.deletePhoto(photo.callId, photo.id).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "이미지가 삭제됐어요")
                    enterDetail(customer)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "이미지 삭제 실패: ${it.message}")
                },
            )
        }
    }

    fun uploadAdditionalInfoPhoto(customer: CustomerUiItem, fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null)
            val currentItems = _detail.value.manualHistory
            val memoIdResult = currentItems.firstOrNull { !it.id.isNullOrBlank() }?.id
                ?.let { Result.success(it) }
                ?: customerRepo.createMemo(customer.phone, memo = "")
                    .map { it.id }

            memoIdResult.fold(
                onSuccess = { memoId ->
                    customerRepo.uploadMemoPhoto(
                        phone = customer.phone,
                        memoId = memoId,
                        fileName = fileName,
                        imageBytes = bytes,
                    ).fold(
                        onSuccess = {
                            _detail.value = _detail.value.copy(saving = false, saveMessage = "추가정보 이미지가 추가됐어요")
                            enterDetail(customer)
                        },
                        onFailure = {
                            _detail.value = _detail.value.copy(saving = false, saveMessage = "이미지 추가 실패: ${it.message}")
                        },
                    )
                },
                onFailure = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "메모 생성 실패: ${it.message}")
                },
            )
        }
    }

    fun uploadManualPhoto(customer: CustomerUiItem, fileName: String, bytes: ByteArray) {
        uploadAdditionalInfoPhoto(customer, fileName, bytes)
    }

    fun deleteManualPhoto(customer: CustomerUiItem, photo: CustomerManualPhoto) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null)
            customerRepo.deleteMemoPhoto(
                phone = customer.phone,
                memoId = photo.memoId,
                photoId = photo.id,
            ).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "이미지가 삭제됐어요")
                    enterDetail(customer)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(saving = false, saveMessage = "이미지 삭제 실패: ${it.message}")
                },
            )
        }
    }
}
