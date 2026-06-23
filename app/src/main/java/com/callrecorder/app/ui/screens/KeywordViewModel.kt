package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.CustomKeyword
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KeywordUiState(
    val loading: Boolean = false,
    val keywords: List<CustomKeyword> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
)

class KeywordViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val keywordRepo = container.keywordRepo
    private val storeRepo = container.storeRepo

    private val _state = MutableStateFlow(KeywordUiState())
    val state: StateFlow<KeywordUiState> = _state.asStateFlow()

    init { loadKeywords() }

    fun loadKeywords() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val storeId = storeRepo.activeStoreId() ?: ""
            keywordRepo.listKeywords(storeId).fold(
                onSuccess = { keywords ->
                    _state.value = _state.value.copy(loading = false, keywords = keywords)
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    fun addKeyword(keyword: String, label: String? = null, actionRequired: Boolean = true) {
        if (keyword.isBlank()) return
        if (_state.value.keywords.size >= 20) {
            _state.value = _state.value.copy(error = "키워드는 최대 20개까지 등록 가능해요")
            return
        }
        // 중복 방지
        if (_state.value.keywords.any { it.keyword == keyword.trim() }) {
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val storeId = storeRepo.activeStoreId() ?: ""
            keywordRepo.createKeyword(
                storeId,
                keyword.trim(),
                label?.takeIf { it.isNotBlank() }?.trim(),
                actionRequired,
            ).fold(
                onSuccess = { newKeyword ->
                    _state.value = _state.value.copy(
                        loading = false,
                        keywords = _state.value.keywords + newKeyword,
                        successMessage = "키워드가 추가됐어요",
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = "추가 실패: ${it.message}")
                },
            )
        }
    }

    fun deleteKeyword(keywordId: String) {
        viewModelScope.launch {
            val storeId = storeRepo.activeStoreId() ?: ""
            keywordRepo.deleteKeyword(storeId, keywordId).fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        keywords = _state.value.keywords.filter { it.id != keywordId },
                        successMessage = "키워드가 삭제됐어요",
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(error = "삭제 실패: ${it.message}")
                },
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(error = null, successMessage = null)
    }
}