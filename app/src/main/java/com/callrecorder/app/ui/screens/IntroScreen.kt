package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.ui.theme.CallRecorderTheme

@Composable
fun IntroScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit = onStart,
) {
    Scaffold(containerColor = AppColors.DeepBrown900) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.DeepBrown900)
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FianoFolderIcon(dark = false)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "고객관리는 FIANO에게 맡기고,\n비즈니스만 생각하세요.",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 32.sp,
                    ),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "통화는 FIANO가 자동으로 정리해요.\n가장 중요한 가치에 집중하세요.",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = AppColors.DeepBrown100,
                        lineHeight = 24.sp,
                        letterSpacing = (-0.5).sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OnboardingPrimaryButton(
                    text = "로그인하기",
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = AppColors.SignalRed500,
                )
                OnboardingOutlineButton(
                    text = "간편회원가입",
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview(
    name = "Intro",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=360dp,height=800dp,dpi=440",
)
@Composable
private fun IntroScreenPreview() {
    CallRecorderTheme {
        IntroScreen(onStart = {}, onSkip = {})
    }
}
