package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.extractedInfoOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CustomerUiItem(
    val phone: String,                          // 전화번호 = 고객 식별키
    val name: String?,                          // extractedInfo.customerName
    val callCount: Int,
    val lastCallAt: String?,
    val lastSummary: String?,
    val categories: List<String>,               // 통화 카테고리 목록
    val calls: List<Call>,                      // 해당 고객의 전체 통화 목록
    val isVip: Boolean,                         // 통화 3회 이상
)

data class CustomerUiState(
    val loading: Boolean = false,
    val customers: List<CustomerUiItem> = emptyList(),
    val error: String? = null,
)

class CustomerViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val callRepo = container.callRepo
    private val storeRepo = container.storeRepo

    private val _state = MutableStateFlow(CustomerUiState())
    val state: StateFlow<CustomerUiState> = _state.asStateFlow()

    init { loadCustomers() }

    fun loadCustomers() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val storeId = storeRepo.activeStoreId()
            callRepo.listCalls(storeId).fold(
                onSuccess = { allCalls ->
                    // 전화번호 기준으로 고객 그룹핑
                    val grouped = allCalls
                        .filter { !it.callerNumber.isNullOrBlank() }
                        .groupBy { it.callerNumber!! }

                    val customers = grouped.map { (phone, calls) ->
                        val sorted = calls.sortedByDescending { it.createdAt }
                        val latest = sorted.first()
                        val info = latest.extractedInfoOrNull()

                        CustomerUiItem(
                            phone = phone,
                            name = calls.mapNotNull { it.extractedInfoOrNull()?.customerName }
                                .firstOrNull { it.isNotBlank() },
                            callCount = calls.size,
                            lastCallAt = latest.createdAt,
                            lastSummary = latest.summary,
                            categories = calls.mapNotNull { it.category }.distinct(),
                            calls = sorted,
                            isVip = calls.size >= 3,
                        )
                    }.sortedByDescending { it.callCount }

                    _state.value = CustomerUiState(
                        loading = false,
                        customers = customers,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    fun refresh() = loadCustomers()
}
