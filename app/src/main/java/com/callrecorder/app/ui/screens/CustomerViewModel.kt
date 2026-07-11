package com.callrecorder.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CustomerAnalysis
import com.callrecorder.app.data.model.CustomerHistoryItem
import com.callrecorder.app.data.model.CustomerListItem
import com.callrecorder.app.data.model.CustomerProfile
import com.callrecorder.app.data.model.UpdateCustomerRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject as KxJsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject

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

data class CustomerContactInput(
    val name: String?,
    val phone: String,
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
    val saveMessageSection: CustomerDetailMessageSection? = null,
)

enum class CustomerDetailMessageSection {
    PROFILE,
    RELATED_IMAGES,
    ADDITIONAL_INFO,
}

class CustomerViewModel : ViewModel() {

    companion object {
        private const val KEY_MANUAL_CUSTOMERS = "items"
        private const val KEY_DELETED_CUSTOMERS = "deleted_items"
    }

    private val app = CallRecorderApp.instance
    private val container = CallRecorderApp.instance.container
    private val notesRepo = container.notesRepo
    private val customerRepo = container.customerRepo
    private val api = container.api
    private val manualCustomerPrefs = app.getSharedPreferences("manual_customers", Context.MODE_PRIVATE)
    private val profileCachePrefs = app.getSharedPreferences("customer_profile_cache", Context.MODE_PRIVATE)
    private val localImagePrefs = app.getSharedPreferences("customer_local_images", Context.MODE_PRIVATE)
    private val localMemoPrefs = app.getSharedPreferences("customer_local_memos", Context.MODE_PRIVATE)
    private val deletedMemoPhotoPrefs = app.getSharedPreferences("customer_deleted_memo_photos", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(CustomerUiState())
    val state: StateFlow<CustomerUiState> = _state.asStateFlow()

    private val _detail = MutableStateFlow(CustomerDetailState())
    val detail: StateFlow<CustomerDetailState> = _detail.asStateFlow()

    private var manuallyAddedCustomers: List<CustomerUiItem> = loadManualCustomers()
    private var deletedCustomerKeys: Set<String> = loadDeletedCustomerKeys()

    init { loadCustomers() }

    fun loadCustomers() {
        viewModelScope.launch {
            _state.value = CustomerUiState(
                loading = false,
                customers = mergeManualCustomers(emptyList()),
                error = null,
            )
            runCatching {
                withTimeout(3_000L) {
                    customerRepo.listCustomers().getOrThrow()
                }
            }.fold(
                onSuccess = { customers ->
                    _state.value = CustomerUiState(
                        loading = false,
                        customers = mergeManualCustomers(customers.toCustomerUiItems()),
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    private fun List<CustomerListItem>.toCustomerUiItems(): List<CustomerUiItem> =
        map { customer ->
            CustomerUiItem(
                phone = customer.phone,
                name = customer.name,
                callCount = customer.callCount,
                lastCallAt = customer.lastCallAt,
                lastSummary = customer.latestSummary,
                categories = listOfNotNull(customer.latestCategory),
                calls = emptyList(),
                grade = CustomerGrade.of(customer.callCount),
                isPinned = customer.isPinned,
            )
        }
            .sortedWith(
                compareByDescending<CustomerUiItem> { it.isPinned }
                    .thenByDescending { it.callCount }
                    .thenByDescending { it.lastCallAt ?: "" }
            )

    private fun mergeManualCustomers(serverCustomers: List<CustomerUiItem>): List<CustomerUiItem> {
        val manualByKey = manuallyAddedCustomers.associateBy { it.normalizedPhoneKey() }
        val visibleServerCustomers = serverCustomers.filter { it.normalizedPhoneKey() !in deletedCustomerKeys }
        val visibleManualCustomers = manuallyAddedCustomers.filter { it.normalizedPhoneKey() !in deletedCustomerKeys }
        val mergedServerCustomers = visibleServerCustomers.map { server ->
            val local = manualByKey[server.normalizedPhoneKey()]
            if (local == null) {
                server
            } else {
                server.copy(
                    name = server.name?.takeIf { it.isNotBlank() } ?: local.name,
                    callCount = maxOf(server.callCount, local.callCount),
                    lastCallAt = server.lastCallAt ?: local.lastCallAt,
                    lastSummary = server.lastSummary ?: local.lastSummary,
                    categories = (server.categories + local.categories).distinct(),
                    isPinned = server.isPinned || local.isPinned,
                )
            }
        }
        val serverKeys = mergedServerCustomers.map { it.normalizedPhoneKey() }.toSet()
        return (mergedServerCustomers + visibleManualCustomers.filter { it.normalizedPhoneKey() !in serverKeys })
            .sortedWith(
                compareByDescending<CustomerUiItem> { it.isPinned }
                    .thenByDescending { it.callCount }
                    .thenByDescending { it.lastCallAt ?: "" }
            )
    }

    private fun CustomerUiItem.normalizedPhoneKey(): String =
        phone.filter { it.isDigit() }

    fun deleteCustomer(customer: CustomerUiItem) {
        val key = customer.normalizedPhoneKey()
        if (key.isBlank()) return
        deletedCustomerKeys = deletedCustomerKeys + key
        saveDeletedCustomerKeys()
        manuallyAddedCustomers = manuallyAddedCustomers.filter { it.normalizedPhoneKey() != key }
        saveManualCustomers()
        _state.value = _state.value.copy(customers = mergeManualCustomers(_state.value.customers))
    }

    fun refresh() = loadCustomers()

    fun addCustomerFromContact(
        name: String?,
        rawPhone: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        addCustomersFromContacts(
            contacts = listOf(CustomerContactInput(name = name, phone = rawPhone)),
            onComplete = onComplete,
        )
    }

    fun addCustomersFromContacts(
        contacts: List<CustomerContactInput>,
        onComplete: (Boolean, String) -> Unit = { _, _ -> },
    ) {
        val validContacts = contacts.mapNotNull { contact ->
            val phone = contact.phone.filter { it.isDigit() || it == '+' }
            phone.takeIf { it.filter { ch -> ch.isDigit() }.length >= 7 }?.let {
                CustomerContactInput(
                    name = contact.name?.takeIf { name -> name.isNotBlank() },
                    phone = it,
                )
            }
        }.distinctBy { it.phone.filter { ch -> ch.isDigit() } }

        if (validContacts.isEmpty()) {
            onComplete(false, "연락처 번호를 확인해주세요")
            return
        }

        viewModelScope.launch {
            val localCustomers = validContacts.map { contact ->
                CustomerUiItem(
                    phone = contact.phone,
                    name = contact.name,
                    callCount = 0,
                    lastCallAt = null,
                    lastSummary = null,
                    categories = emptyList(),
                    calls = emptyList(),
                    grade = CustomerGrade.NEW,
                )
            }
            val addedKeys = localCustomers.map { it.normalizedPhoneKey() }.toSet()
            manuallyAddedCustomers = (manuallyAddedCustomers.filter { it.normalizedPhoneKey() !in addedKeys } + localCustomers)
            saveManualCustomers()
            _state.value = _state.value.copy(customers = mergeManualCustomers(_state.value.customers))

            val successCount = validContacts.count { contact ->
                val req = UpdateCustomerRequest(name = contact.name)
                val createResult = customerRepo.createConsentLink(
                    phone = contact.phone,
                    name = contact.name,
                    customerName = contact.name,
                )
                val updateResult = runCatching { api.updateCustomer(contact.phone, req) }
                createResult.isSuccess || updateResult.getOrNull()?.isSuccessful == true
            }

            loadCustomers()
            if (successCount > 0) {
                onComplete(true, "${successCount}명의 고객을 추가했어요")
            } else {
                onComplete(false, "고객 추가 실패")
            }
        }
    }

    private fun loadManualCustomers(): List<CustomerUiItem> {
        val raw = manualCustomerPrefs.getString(KEY_MANUAL_CUSTOMERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val phone = item.optString("phone").takeIf { it.isNotBlank() } ?: continue
                    add(
                        CustomerUiItem(
                            phone = phone,
                            name = item.optString("name").takeIf { it.isNotBlank() },
                            callCount = item.optInt("callCount", 0),
                            lastCallAt = item.optString("lastCallAt").takeIf { it.isNotBlank() },
                            lastSummary = item.optString("lastSummary").takeIf { it.isNotBlank() },
                            categories = emptyList(),
                            calls = emptyList(),
                            grade = CustomerGrade.NEW,
                            isPinned = item.optBoolean("isPinned", false),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveManualCustomers() {
        val array = JSONArray()
        manuallyAddedCustomers.forEach { customer ->
            array.put(
                JSONObject().apply {
                    put("phone", customer.phone)
                    put("name", customer.name.orEmpty())
                    put("callCount", customer.callCount)
                    put("lastCallAt", customer.lastCallAt.orEmpty())
                    put("lastSummary", customer.lastSummary.orEmpty())
                    put("isPinned", customer.isPinned)
                }
            )
        }
        manualCustomerPrefs.edit().putString(KEY_MANUAL_CUSTOMERS, array.toString()).apply()
    }

    private fun loadDeletedCustomerKeys(): Set<String> {
        val raw = manualCustomerPrefs.getString(KEY_DELETED_CUSTOMERS, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun saveDeletedCustomerKeys() {
        val array = JSONArray()
        deletedCustomerKeys.forEach { array.put(it) }
        manualCustomerPrefs.edit().putString(KEY_DELETED_CUSTOMERS, array.toString()).apply()
    }

    /**
     * 고객 상세 진입: 프로필 + AI 분석 + 통화별 메모/사진을 한 번에 로드.
     * 메모/사진은 통화 건수만큼 병렬로 조회한다.
     */
    fun enterDetail(customer: CustomerUiItem) {
        val phone = customer.phone
        val callIds = customer.calls.map { it.id }
        viewModelScope.launch {
            val cachedProfile = loadCachedProfile(phone)
            val localHistory = loadLocalManualMemos(phone)
            val localPhotos = loadLocalManualPhotos(phone)
            _detail.value = CustomerDetailState(
                loading = cachedProfile == null && localHistory.isEmpty() && localPhotos.isEmpty(),
                profile = cachedProfile,
                manualHistory = localHistory,
                manualPhotos = localPhotos,
            )

            // 1) 프로필 + 분석
            val profileDeferred = async { runCatching { api.getCustomer(phone) }.getOrNull() }
            val historyDeferred = async {
                runCatching { api.getCustomerHistory(phone).items }.getOrDefault(emptyList())
            }
            val resp = profileDeferred.await()
            resp?.profile?.let { saveCachedProfile(phone, it) }
            val historyItems = filterDeletedMemoPhotos(phone, historyDeferred.await())
            _detail.value = _detail.value.copy(
                loading = false,
                profile = resp?.profile ?: _detail.value.profile,
                analysis = resp?.analysis ?: _detail.value.analysis,
                manualHistory = mergeLocalManualMemos(phone, historyItems.filter { it.type == "manual_memo" }),
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
                    }
                    .let { mergeLocalManualPhotos(phone, it) },
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
            loadCachedProfile(phone)?.let { cached ->
                _detail.value = _detail.value.copy(profile = cached)
            }
            val resp = runCatching { api.getCustomer(phone) }.getOrNull()
            resp?.profile?.let { saveCachedProfile(phone, it) }
            _detail.value = _detail.value.copy(
                profile = resp?.profile ?: _detail.value.profile,
                analysis = resp?.analysis ?: _detail.value.analysis,
            )
        }
    }

    fun setPinned(customer: CustomerUiItem, pinned: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val updatedCustomer = customer.copy(isPinned = pinned)
            val key = updatedCustomer.normalizedPhoneKey()
            manuallyAddedCustomers = (
                manuallyAddedCustomers.filter { it.normalizedPhoneKey() != key } + updatedCustomer
            )
            saveManualCustomers()
            _state.value = _state.value.copy(customers = mergeManualCustomers(_state.value.customers))
            onSuccess()

            val req = UpdateCustomerRequest(isPinned = pinned)
            runCatching { api.updateCustomer(customer.phone, req) }.onSuccess {
                loadCustomers()
                loadDetail(customer.phone)
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
            val optimisticProfile = CustomerProfile(
                email = email.ifBlank { null },
                tendency = tendency.ifBlank { null },
                medical = medical.ifBlank { null },
                specialNotes = specialNotes.ifBlank { null },
                customFields = customFields.toJsonObjectOrNull(),
                isPinned = _detail.value.profile?.isPinned ?: false,
            )
            saveCachedProfile(phone, optimisticProfile)
            _detail.value = _detail.value.copy(
                saving = true,
                saveMessage = null,
                saveMessageSection = null,
                profile = optimisticProfile,
            )
            val req = UpdateCustomerRequest(
                email = email.ifBlank { null },
                tendency = tendency.ifBlank { null },
                medical = medical.ifBlank { null },
                specialNotes = specialNotes.ifBlank { null },
                customFields = customFields.ifEmpty { null },
            )
            runCatching { api.updateCustomer(phone, req) }.fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "저장됐어요",
                        saveMessageSection = CustomerDetailMessageSection.PROFILE,
                    )
                },
                onFailure = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "저장 실패: ${it.message}",
                        saveMessageSection = CustomerDetailMessageSection.PROFILE,
                    )
                },
            )
        }
    }

    private fun loadCachedProfile(phone: String): CustomerProfile? {
        val raw = profileCachePrefs.getString(profileCacheKey(phone), null) ?: return null
        return runCatching {
            val item = JSONObject(raw)
            CustomerProfile(
                email = item.optString("email").takeIf { it.isNotBlank() },
                tendency = item.optString("tendency").takeIf { it.isNotBlank() },
                medical = item.optString("medical").takeIf { it.isNotBlank() },
                specialNotes = item.optString("specialNotes").takeIf { it.isNotBlank() },
                customFields = item.optJSONObject("customFields")?.toStringMap()?.toJsonObjectOrNull(),
                updatedAt = item.optString("updatedAt").takeIf { it.isNotBlank() },
                isPinned = item.optBoolean("isPinned", false),
            )
        }.getOrNull()
    }

    private fun saveCachedProfile(phone: String, profile: CustomerProfile) {
        val item = JSONObject().apply {
            put("email", profile.email.orEmpty())
            put("tendency", profile.tendency.orEmpty())
            put("medical", profile.medical.orEmpty())
            put("specialNotes", profile.specialNotes.orEmpty())
            put("updatedAt", profile.updatedAt.orEmpty())
            put("isPinned", profile.isPinned)
            val customMap = (profile.customFields as? KxJsonObject)?.mapValues { it.value.toString().trim('"') }.orEmpty()
            put("customFields", JSONObject(customMap))
        }
        profileCachePrefs.edit().putString(profileCacheKey(phone), item.toString()).apply()
    }

    private fun profileCacheKey(phone: String): String =
        phone.filter { it.isDigit() }.ifBlank { phone }

    private fun Map<String, String>.toJsonObjectOrNull(): KxJsonObject? =
        takeIf { it.isNotEmpty() }?.let { map ->
            KxJsonObject(map.mapValues { JsonPrimitive(it.value) })
        }

    private fun JSONObject.toStringMap(): Map<String, String> =
        keys().asSequence().associateWith { key -> optString(key) }

    fun clearSaveMessage() {
        _detail.value = _detail.value.copy(saveMessage = null, saveMessageSection = null)
    }

    fun uploadRelatedPhoto(customer: CustomerUiItem, callId: String, fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null, saveMessageSection = null)
            notesRepo.uploadPhoto(callId, fileName, bytes).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지가 추가됐어요",
                        saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
                    )
                    enterDetail(customer)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지 추가 실패: ${it.message}",
                        saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
                    )
                },
            )
        }
    }

    fun deleteRelatedPhoto(customer: CustomerUiItem, photo: CustomerRelatedPhoto) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null, saveMessageSection = null)
            notesRepo.deletePhoto(photo.callId, photo.id).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지가 삭제됐어요",
                        saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
                    )
                    enterDetail(customer)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지 삭제 실패: ${it.message}",
                        saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
                    )
                },
            )
        }
    }

    fun uploadAdditionalInfoPhoto(
        customer: CustomerUiItem,
        fileName: String,
        bytes: ByteArray,
        showFailureMessage: Boolean = true,
        messageSection: CustomerDetailMessageSection = CustomerDetailMessageSection.ADDITIONAL_INFO,
    ) {
        viewModelScope.launch {
            _detail.value = _detail.value.copy(saving = true, saveMessage = null, saveMessageSection = null)
            val currentItems = _detail.value.manualHistory
            val memoIdResult = currentItems.firstOrNull { item ->
                val id = item.id.orEmpty()
                id.isNotBlank() && !id.startsWith("local_")
            }?.id
                ?.let { Result.success(it) }
                ?: customerRepo.createMemo(customer.phone, memo = "관련 이미지")
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
                            _detail.value = _detail.value.copy(
                                saving = false,
                                saveMessage = "이미지가 추가됐어요",
                                saveMessageSection = messageSection,
                            )
                            enterDetail(customer)
                        },
                        onFailure = {
                            _detail.value = _detail.value.copy(
                                saving = false,
                                saveMessage = if (showFailureMessage) "이미지 추가 실패: ${it.message}" else "이미지가 추가됐어요",
                                saveMessageSection = messageSection,
                            )
                        },
                    )
                },
                onFailure = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = if (showFailureMessage) "메모 생성 실패: ${it.message}" else "이미지가 추가됐어요",
                        saveMessageSection = messageSection,
                    )
                },
            )
        }
    }

    fun addLocalRelatedPhoto(customer: CustomerUiItem, localUrl: String) {
        val phone = customer.phone
        val photo = CustomerManualPhoto(
            id = "local_${System.currentTimeMillis()}",
            memoId = "local",
            url = localUrl,
        )
        val next = (_detail.value.manualPhotos + photo).distinctBy { it.url }
        saveLocalManualPhotos(phone, next.filter { it.id.startsWith("local_") })
        _detail.value = _detail.value.copy(
            manualPhotos = next,
            saveMessage = "이미지가 추가됐어요",
            saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
        )
    }

    fun saveAdditionalInfoMemo(customer: CustomerUiItem, memo: String) {
        val text = memo.trim()
        if (text.isBlank()) {
            saveLocalManualMemos(customer.phone, emptyList())
            _detail.value = _detail.value.copy(
                manualHistory = _detail.value.manualHistory.filter { it.photos.isNotEmpty() },
                saving = false,
                saveMessage = "저장됐어요",
                saveMessageSection = CustomerDetailMessageSection.ADDITIONAL_INFO,
            )
            return
        }
        val localMemo = CustomerHistoryItem(
            type = "manual_memo",
            id = "local_memo_primary",
            memo = text,
        )
        val serverPhotoItems = _detail.value.manualHistory.filter { item ->
            item.photos.isNotEmpty() && item.id?.startsWith("local_memo_") != true
        }
        val nextHistory = listOf(localMemo) + serverPhotoItems
        saveLocalManualMemos(customer.phone, listOf(localMemo))
        _detail.value = _detail.value.copy(
            manualHistory = nextHistory,
            saving = true,
            saveMessage = null,
            saveMessageSection = null,
        )
        viewModelScope.launch {
            customerRepo.createMemo(customer.phone, memo = text).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "저장됐어요",
                        saveMessageSection = CustomerDetailMessageSection.ADDITIONAL_INFO,
                    )
                },
                onFailure = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "저장됐어요",
                        saveMessageSection = CustomerDetailMessageSection.ADDITIONAL_INFO,
                    )
                },
            )
        }
    }

    fun uploadManualPhoto(customer: CustomerUiItem, fileName: String, bytes: ByteArray) {
        uploadAdditionalInfoPhoto(
            customer = customer,
            fileName = fileName,
            bytes = bytes,
            showFailureMessage = false,
            messageSection = CustomerDetailMessageSection.RELATED_IMAGES,
        )
    }

    fun deleteManualPhoto(customer: CustomerUiItem, photo: CustomerManualPhoto) {
        viewModelScope.launch {
            rememberDeletedMemoPhoto(customer.phone, memoId = photo.memoId, photoId = photo.id, url = photo.url)
            if (photo.id.startsWith("local_")) {
                val next = _detail.value.manualPhotos.filterNot { it.id == photo.id }
                saveLocalManualPhotos(customer.phone, next.filter { it.id.startsWith("local_") })
                _detail.value = _detail.value.copy(
                    manualPhotos = next,
                    saveMessage = "이미지가 삭제됐어요",
                    saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
                )
                return@launch
            }
            _detail.value = _detail.value.copy(saving = true, saveMessage = null, saveMessageSection = null)
            customerRepo.deleteMemoPhoto(
                phone = customer.phone,
                memoId = photo.memoId,
                photoId = photo.id,
            ).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지가 삭제됐어요",
                        saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
                    )
                    enterDetail(customer)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지가 삭제됐어요",
                        saveMessageSection = CustomerDetailMessageSection.RELATED_IMAGES,
                    )
                },
            )
        }
    }

    fun deleteAdditionalInfoPhoto(customer: CustomerUiItem, photo: AdditionalInfoPhotoItem) {
        viewModelScope.launch {
            rememberDeletedMemoPhoto(customer.phone, memoId = photo.memoId, photoId = photo.photoId, url = photo.url)
            removeAdditionalInfoPhotoFromState(photo)
            _detail.value = _detail.value.copy(saving = true, saveMessage = null, saveMessageSection = null)
            if (photo.memoId.startsWith("local_")) {
                _detail.value = _detail.value.copy(
                    saving = false,
                    saveMessage = "이미지가 삭제됐어요",
                    saveMessageSection = CustomerDetailMessageSection.ADDITIONAL_INFO,
                )
                return@launch
            }
            customerRepo.deleteMemoPhoto(
                phone = customer.phone,
                memoId = photo.memoId,
                photoId = photo.photoId,
            ).fold(
                onSuccess = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지가 삭제됐어요",
                        saveMessageSection = CustomerDetailMessageSection.ADDITIONAL_INFO,
                    )
                    enterDetail(customer)
                },
                onFailure = {
                    _detail.value = _detail.value.copy(
                        saving = false,
                        saveMessage = "이미지가 삭제됐어요",
                        saveMessageSection = CustomerDetailMessageSection.ADDITIONAL_INFO,
                    )
                },
            )
        }
    }

    private fun removeAdditionalInfoPhotoFromState(photo: AdditionalInfoPhotoItem) {
        _detail.value = _detail.value.copy(
            manualHistory = _detail.value.manualHistory.map { item ->
                if (item.id != photo.memoId) {
                    item
                } else {
                    item.copy(photos = item.photos.filterNot { it.id == photo.photoId || it.url == photo.url })
                }
            },
        )
    }

    private fun filterDeletedMemoPhotos(phone: String, items: List<CustomerHistoryItem>): List<CustomerHistoryItem> {
        val deletedKeys = loadDeletedMemoPhotoKeys(phone)
        if (deletedKeys.isEmpty()) return items
        return items.map { item ->
            item.copy(
                photos = item.photos.filterNot { photo ->
                    val memoId = item.id.orEmpty()
                    val photoId = photo.id.orEmpty()
                    val url = photo.url.orEmpty()
                    deletedKeys.contains(deletedMemoPhotoKey(memoId, photoId, url)) ||
                        deletedKeys.contains(deletedMemoPhotoUrlKey(url))
                },
            )
        }
    }

    private fun rememberDeletedMemoPhoto(phone: String, memoId: String, photoId: String, url: String) {
        val keys = loadDeletedMemoPhotoKeys(phone).toMutableSet()
        keys += deletedMemoPhotoKey(memoId, photoId, url)
        if (url.isNotBlank()) keys += deletedMemoPhotoUrlKey(url)
        saveDeletedMemoPhotoKeys(phone, keys)
    }

    private fun loadDeletedMemoPhotoKeys(phone: String): Set<String> {
        val raw = deletedMemoPhotoPrefs.getString(profileCacheKey(phone), null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun saveDeletedMemoPhotoKeys(phone: String, keys: Set<String>) {
        val array = JSONArray()
        keys.forEach { array.put(it) }
        deletedMemoPhotoPrefs.edit().putString(profileCacheKey(phone), array.toString()).apply()
    }

    private fun deletedMemoPhotoKey(memoId: String, photoId: String, url: String): String =
        "$memoId|$photoId|$url"

    private fun deletedMemoPhotoUrlKey(url: String): String =
        "url|$url"

    private fun mergeLocalManualPhotos(phone: String, serverPhotos: List<CustomerManualPhoto>): List<CustomerManualPhoto> {
        val serverUrls = serverPhotos.map { it.url }.toSet()
        return serverPhotos + loadLocalManualPhotos(phone).filter { it.url !in serverUrls }
    }

    private fun mergeLocalManualMemos(phone: String, serverMemos: List<CustomerHistoryItem>): List<CustomerHistoryItem> {
        val serverMemoTexts = serverMemos.mapNotNull { it.memo?.trim()?.takeIf { memo -> memo.isNotBlank() } }.toSet()
        return serverMemos + loadLocalManualMemos(phone).filter { local ->
            val text = local.memo?.trim().orEmpty()
            text.isNotBlank() && text !in serverMemoTexts
        }
    }

    private fun loadLocalManualMemos(phone: String): List<CustomerHistoryItem> {
        val raw = localMemoPrefs.getString(profileCacheKey(phone), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val memo = item.optString("memo").takeIf { it.isNotBlank() } ?: continue
                    add(
                        CustomerHistoryItem(
                            type = "manual_memo",
                            id = item.optString("id").takeIf { it.isNotBlank() } ?: "local_memo_$i",
                            createdAt = item.optString("createdAt").takeIf { it.isNotBlank() },
                            memo = memo,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLocalManualMemos(phone: String, memos: List<CustomerHistoryItem>) {
        val array = JSONArray()
        memos.forEach { memo ->
            array.put(
                JSONObject().apply {
                    put("id", memo.id.orEmpty())
                    put("createdAt", memo.createdAt.orEmpty())
                    put("memo", memo.memo.orEmpty())
                }
            )
        }
        localMemoPrefs.edit().putString(profileCacheKey(phone), array.toString()).apply()
    }

    private fun loadLocalManualPhotos(phone: String): List<CustomerManualPhoto> {
        val raw = localImagePrefs.getString(profileCacheKey(phone), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url").takeIf { it.isNotBlank() } ?: continue
                    add(
                        CustomerManualPhoto(
                            id = item.optString("id").takeIf { it.isNotBlank() } ?: "local_$i",
                            memoId = "local",
                            url = url,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLocalManualPhotos(phone: String, photos: List<CustomerManualPhoto>) {
        val array = JSONArray()
        photos.forEach { photo ->
            array.put(
                JSONObject().apply {
                    put("id", photo.id)
                    put("url", photo.url)
                }
            )
        }
        localImagePrefs.edit().putString(profileCacheKey(phone), array.toString()).apply()
    }
}
