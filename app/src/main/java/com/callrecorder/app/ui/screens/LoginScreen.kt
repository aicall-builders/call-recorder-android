package com.callrecorder.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.R
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.ui.theme.CallRecorderTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(
    onLoggedIn: (LoginType) -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onLoggedIn(state.loginType)
    }

    // ── 구글 Activity result 런처 ──
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    vm.handleGoogleSignInResult(idToken)  // idToken만 전달
                } else {
                    vm.setError("Google ID Token을 가져올 수 없습니다.")
                }
            } catch (e: ApiException) {
                vm.setError("Google 로그인 실패: ${e.statusCode}")
            }
        } else {
            vm.setError(null)
        }
    }

    LoginContent(
        state = state,
        onKakaoClick = { vm.loginWithKakao(context) },
        onGoogleClick = {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("637616780815-1jqnk92f4l0icuma1gqabfr6a7tq4fce.apps.googleusercontent.com")
                .requestEmail()
                .build()
            val signInIntent = GoogleSignIn.getClient(context, gso).signInIntent
            vm.setLoading(LoginType.GOOGLE)
            googleLauncher.launch(signInIntent)
        },
    )
}

@Composable
private fun LoginContent(
    state: AuthUiState,
    onKakaoClick: () -> Unit,
    onGoogleClick: () -> Unit,
) {
    Scaffold(containerColor = Color.White) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FianoFolderIcon()

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "간편 로그인으로 시작하기",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF1A1A21), lineHeight = 32.sp),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "소셜 계정으로 1초만에 시작하세요.\n번거로운 가입 절차가 없습니다.",
                    style = TextStyle(fontSize = 18.sp, color = Color(0xFF5A5F6C), lineHeight = 24.sp, letterSpacing = (-0.5).sp),
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── 에러 표시 ──
                state.error?.let {
                    Surface(
                        color = AppColors.SignalRed50,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "로그인 실패: $it",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = TextStyle(fontSize = 12.sp, color = AppColors.SignalRed800),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── 카카오 버튼 ──
                SocialLoginButton(
                    onClick = onKakaoClick,
                    loading = state.loading && state.loginType == LoginType.KAKAO,
                    enabled = !state.loading,
                    containerColor = AppColors.KakaoYellow,
                    contentColor = AppColors.KakaoBlack,
                    icon = {
                        Image(
                            painter = painterResource(id = R.drawable.icon_kakao_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = "카카오로 시작하기",
                    loadingColor = AppColors.KakaoBlack,
                )

                Spacer(Modifier.height(16.dp))

                // ── 구글 버튼 ──
                SocialLoginButton(
                    onClick = onGoogleClick,
                    loading = state.loading && state.loginType == LoginType.GOOGLE,
                    enabled = !state.loading,
                    containerColor = Color.White,
                    contentColor = Color(0xFF4D4D57),
                    borderColor = Color(0xFF747775),
                    icon = {
                        Image(
                            painter = painterResource(id = R.drawable.icon_google_logo),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    text = "Google로 시작하기",
                    loadingColor = AppColors.Brand,
                )

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "로그인 시 이용약관 및 개인정보 처리방침에\n동의하는 것으로 간주됩니다.",
                    style = TextStyle(fontSize = 14.sp, color = Color(0xFF474B6B), lineHeight = 16.sp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(
    name = "Login",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=360dp,height=800dp,dpi=440",
)
@Composable
private fun LoginScreenPreview() {
    CallRecorderTheme {
        LoginContent(
            state = AuthUiState(),
            onKakaoClick = {},
            onGoogleClick = {},
        )
    }
}

@Composable
private fun SocialLoginButton(
    onClick: () -> Unit,
    loading: Boolean,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color? = null,
    icon: @Composable () -> Unit,
    text: String,
    loadingColor: Color,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (borderColor != null)
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(999.dp))
                else Modifier,
            ),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.6f),
            disabledContentColor = contentColor.copy(alpha = 0.6f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = loadingColor,
                strokeWidth = 2.dp,
            )
        } else {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}
