package com.callrecorder.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.CallRecorderApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LoginType { KAKAO, GOOGLE, NAVER, NONE }

class AuthViewModel : ViewModel() {

    private val container = CallRecorderApp.instance.container
    private val repo = container.authRepo

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    val isLoggedIn = container.tokenStore.accessTokenFlow

    fun loginWithKakao(context: Context) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null, loginType = LoginType.KAKAO)
        viewModelScope.launch {
            repo.loginWithKakao(context).fold(
                onSuccess = { _state.value = AuthUiState(loading = false, success = true, loginType = LoginType.KAKAO) },
                onFailure = { _state.value = AuthUiState(loading = false, error = it.message) },
            )
        }
    }

    fun setLoading(type: LoginType) {
        _state.value = _state.value.copy(loading = true, error = null, loginType = type)
    }

    // idToken만 받도록 단순화
    fun handleGoogleSignInResult(idToken: String) {
        viewModelScope.launch {
            repo.loginWithGoogle(idToken).fold(
                onSuccess = { _state.value = AuthUiState(loading = false, success = true, loginType = LoginType.GOOGLE) },
                onFailure = { _state.value = AuthUiState(loading = false, error = it.message) },
            )
        }
    }

    fun loginWithNaver(context: Context) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null, loginType = LoginType.NAVER)
        viewModelScope.launch {
            repo.loginWithNaver(context).fold(
                onSuccess = { _state.value = AuthUiState(loading = false, success = true, loginType = LoginType.NAVER) },
                onFailure = { _state.value = AuthUiState(loading = false, error = it.message) },
            )
        }
    }

    fun setError(message: String?) {
        _state.value = _state.value.copy(loading = false, error = message, loginType = LoginType.NONE)
    }

    fun logout() {
        viewModelScope.launch { repo.logout() }
    }
}

data class AuthUiState(
    val loading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
    val loginType: LoginType = LoginType.NONE,
)
