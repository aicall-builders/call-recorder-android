package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.R
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.ui.theme.CallRecorderTheme

@Composable
fun KakaoLinkedScreen(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    KakaoLinkedContent(
        onContinue = onContinue,
        onCancel = {
            vm.logout()
            onCancel()
        },
    )
}

@Composable
private fun KakaoLinkedContent(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(containerColor = Color.White) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding),
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.icon_onboarding_back),
                    contentDescription = "뒤로가기",
                    modifier = Modifier.padding(4.dp),
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 19.dp),
                color = Color.White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CheckBadge(sizeDp = 54)

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "카카오 계정 연결 완료",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1A1A21),
                            lineHeight = 32.sp,
                        ),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "카카오 계정이 성공적으로 연결되었습니다.",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF5A5F6C),
                            lineHeight = 24.sp,
                            letterSpacing = (-0.5).sp,
                        ),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "연결 취소",
                        modifier = Modifier
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8C8F9E),
                            lineHeight = 16.sp,
                        ),
                    )
                }
            }

            OnboardingPrimaryButton(
                text = "권한설정으로 이동",
                onClick = onContinue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 64.dp),
                containerColor = AppColors.DeepBrown900,
            )
        }
    }
}

@Preview(
    name = "Kakao linked",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=360dp,height=800dp,dpi=440",
)
@Composable
private fun KakaoLinkedPreview() {
    CallRecorderTheme {
        KakaoLinkedContent(onContinue = {}, onCancel = {})
    }
}
