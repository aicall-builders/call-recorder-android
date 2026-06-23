package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StoreViewModel : ViewModel() {
    private val repo = CallRecorderApp.instance.container.storeRepo

    private val _state = MutableStateFlow(StoreUiState())
    val state: StateFlow<StoreUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repo.list().fold(
                onSuccess = { stores ->
                    _state.value = _state.value.copy(
                        loading = false,
                        stores = stores,
                        activeStoreId = repo.activeStoreId(),
                        error = null,
                    )
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun ensureActiveStore(onReady: () -> Unit) {
        viewModelScope.launch {
            if (repo.activeStoreId() != null) { onReady(); return@launch }

            repo.list().fold(
                onSuccess = { stores ->
                    val first = stores.firstOrNull()
                    if (first != null) {
                        repo.setActive(first.id)
                        onReady()
                    } else {
                        repo.create("내 가게", "기타", null, null).fold(
                            onSuccess = { onReady() },
                            onFailure = {
                                _state.value = _state.value.copy(error = it.message)
                                onReady()
                            },
                        )
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(error = it.message)
                    onReady()
                },
            )
        }
    }

    fun create(name: String, category: String, phone: String?, address: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            repo.create(name, category, phone, address).fold(
                onSuccess = {
                    refresh()
                    onDone()
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = it.message) },
            )
        }
    }

    fun setActive(storeId: String) {
        viewModelScope.launch {
            repo.setActive(storeId)
            _state.value = _state.value.copy(activeStoreId = storeId)
        }
    }
}

data class StoreUiState(
    val loading: Boolean = false,
    val stores: List<Store> = emptyList(),
    val activeStoreId: String? = null,
    val error: String? = null,
)