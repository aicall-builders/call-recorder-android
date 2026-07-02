package com.callrecorder.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.ui.theme.AppColors
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

    Scaffold(containerColor = AppColors.Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(AppColors.Surface, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = AppColors.BrandBlue,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "통화비서 FIANO",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary),
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = "로그인하고 시작하기",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "소셜 계정으로 1초만에 시작하세요.\n번거로운 가입 절차가 없습니다.",
                style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary, lineHeight = 20.sp),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1.4f))

            // ── 에러 표시 ──
            state.error?.let {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "로그인 실패: $it",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = TextStyle(fontSize = 12.sp, color = Color(0xFFB91C1C)),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── 카카오 버튼 ──
            SocialLoginButton(
                onClick = { vm.loginWithKakao(context) },
                loading = state.loading && state.loginType == LoginType.KAKAO,
                enabled = !state.loading,
                containerColor = AppColors.KakaoYellow,
                contentColor = AppColors.KakaoBlack,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.ChatBubble,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AppColors.KakaoBlack,
                    )
                },
                text = "카카오로 시작하기",
                loadingColor = AppColors.KakaoBlack,
            )

            Spacer(Modifier.height(10.dp))

            // ── 구글 버튼 ──
            SocialLoginButton(
                onClick = {
                    // requestServerAuthCode 없이 idToken만 요청
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken("637616780815-1jqnk92f4l0icuma1gqabfr6a7tq4fce.apps.googleusercontent.com")
                        .requestEmail()
                        .build()
                    val signInIntent = GoogleSignIn.getClient(context, gso).signInIntent
                    vm.setLoading(LoginType.GOOGLE)
                    googleLauncher.launch(signInIntent)
                },
                loading = state.loading && state.loginType == LoginType.GOOGLE,
                enabled = !state.loading,
                containerColor = Color.White,
                contentColor = Color(0xFF3C4043),
                borderColor = Color(0xFFDCE0E6),
                icon = {
                    Text(
                        text = "G",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4)),
                    )
                },
                text = "Google로 시작하기",
                loadingColor = Color(0xFF4285F4),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "로그인 시 이용약관 및 개인정보 처리방침에\n동의하는 것으로 간주됩니다.",
                style = TextStyle(fontSize = 11.sp, color = AppColors.TextSecondary, lineHeight = 16.sp),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
        }
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
            .height(54.dp)
            .then(
                if (borderColor != null)
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(12.dp))
                else Modifier,
            ),
        shape = RoundedCornerShape(12.dp),
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
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
        }
    }
}