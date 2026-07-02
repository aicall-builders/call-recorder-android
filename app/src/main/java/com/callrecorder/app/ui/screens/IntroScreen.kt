package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.ui.theme.AppColors

/**
 * 인트로(시작) 화면.
 * 시안 1번: AI 통화 비서 로고 + 헤드라인 + [시작하기] / [건너뛰기]
 *
 * - "시작하기"를 누르면 onStart()  -> LoginScreen 으로 이동
 * - "건너뛰기"를 누르면 onSkip()   -> 동일하게 LoginScreen 으로 이동
 *   (현재는 동작이 같지만, 추후 데모 모드 등에 대비해 콜백 분리)
 */
@Composable
fun IntroScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit = onStart,
) {
    Scaffold(
        containerColor = AppColors.Background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 상단 여백
            Spacer(Modifier.weight(1.1f))

            // ===== 로고 박스 (흰색 카드 + 그림자 + 폰 아이콘) =====
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = AppColors.IconBoxShadow,
                        spotColor = AppColors.IconBoxShadow,
                    )
                    .background(AppColors.IconBoxBg, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    tint = AppColors.BrandBlue,
                    modifier = Modifier.size(44.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            // ===== 서비스 명 =====
            Text(
                text = "통화비서 FIANO",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                ),
            )

            // 로고 ↔ 헤드라인 사이 큰 여백
            Spacer(Modifier.weight(1f))

            // ===== 헤드라인 =====
            Text(
                text = "전화 응대는 AI에게 맡기고,\n비즈니스에만 집중하세요.",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    lineHeight = 32.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // ===== 서브 설명 =====
            Text(
                text = "AI 통화 비서가 수신 예약 통화를 자동으로 정리해\n고객 응대의 가장 중요한 가치에 집중하세요.",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    lineHeight = 20.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1.2f))

            // ===== 시작하기 버튼 =====
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.BrandBlue,
                    contentColor = AppColors.TextOnPrimary,
                ),
            ) {
                Text(
                    "시작하기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // ===== 건너뛰기 텍스트 버튼 =====
            Text(
                text = "건너뛰기",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                ),
                modifier = Modifier
                    .clickable(onClick = onSkip)
                    .padding(vertical = 10.dp, horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}