package com.callrecorder.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 전체 카테고리 목록
val ALL_CALL_CATEGORIES = listOf("예약", "취소", "불만", "문의", "기타")

data class SettingsUiState(
    val loading: Boolean = false,
    val storeName: String = "",
    val storeCategory: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val loginProvider: String = "",
    val accountEmail: String = "",
    val smsEnabled: Boolean = true,
    val memberCount: Int = 1,
    val error: String? = null,
    val successMessage: String? = null,
    // 중요 통화 필터 카테고리 (선택된 것만 홈에 표시)
    val importantCategories: Set<String> = setOf("예약", "취소", "불만", "문의"),
    // 통화 자동 분석 ON/OFF
    val autoAnalyzeEnabled: Boolean = false,
)

class SettingsViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val storeRepo = container.storeRepo

    private val prefs by lazy {
        CallRecorderApp.instance.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadPrefs()
        loadAccountInfo()
        loadSettings()
    }

    // SharedPreferences에서 설정 읽기
    private fun loadPrefs() {
        val savedCategories = prefs.getStringSet(
            "important_categories",
            setOf("예약", "취소", "불만", "문의")
        ) ?: setOf("예약", "취소", "불만", "문의")
        val autoAnalyze = prefs.getBoolean("auto_analyze", false)
        _state.value = _state.value.copy(
            importantCategories = savedCategories,
            autoAnalyzeEnabled = autoAnalyze,
        )
    }

    fun loadSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val storeId = storeRepo.activeStoreId()
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
            _state.value = _state.value.copy(loading = false)
        }
    }

    private fun loadAccountInfo() {
        viewModelScope.launch {
            val tokenStore = container.tokenStore
            val storedProvider = tokenStore.getLoginProvider().orEmpty()
            val storedEmail = tokenStore.getAccountEmail().orEmpty()
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            val firebaseEmail = firebaseUser?.email.orEmpty()
            val inferredProvider = inferLoginProvider()

            _state.value = _state.value.copy(
                loginProvider = storedProvider.ifBlank { inferredProvider },
                accountEmail = storedEmail.ifBlank { firebaseEmail },
            )
        }
    }

    private fun inferLoginProvider(): String {
        val providerIds = FirebaseAuth.getInstance().currentUser
            ?.providerData
            ?.map { it.providerId }
            .orEmpty()
        return when {
            providerIds.any { it.contains("google", ignoreCase = true) } -> "google"
            providerIds.any { it.contains("kakao", ignoreCase = true) } -> "kakao"
            providerIds.any { it.contains("naver", ignoreCase = true) } -> "naver"
            else -> ""
        }
    }

    fun syncPrefs() {
        loadPrefs()
    }

    // 중요 통화 카테고리 토글
    fun toggleCategory(category: String) {
        val current = _state.value.importantCategories.toMutableSet()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        prefs.edit().putStringSet("important_categories", current).apply()
        _state.value = _state.value.copy(importantCategories = current)
    }

    // 통화 자동 분석 ON/OFF
    fun setAutoAnalyze(enabled: Boolean) {
        prefs.edit().putBoolean("auto_analyze", enabled).apply()
        _state.value = _state.value.copy(autoAnalyzeEnabled = enabled)
    }

    fun setSmsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(smsEnabled = enabled)
    }

    fun updateUserName(name: String) {
        _state.value = _state.value.copy(userName = name)
    }

    fun updateUserPhone(phone: String) {
        _state.value = _state.value.copy(userPhone = phone)
    }

    fun saveUserInfo(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                // TODO: 회원정보 수정 API 연동
            }.onSuccess {
                _state.value = _state.value.copy(
                    loading = false,
                    successMessage = "회원 정보가 수정되었습니다.",
                )
                onSuccess()
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.message)
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(error = null, successMessage = null)
    }
}
