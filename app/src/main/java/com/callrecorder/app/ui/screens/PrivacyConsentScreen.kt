package com.callrecorder.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.R
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.ui.theme.CallRecorderTheme

@Composable
fun PrivacyConsentScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var agreed by remember { mutableStateOf(false) }

    Scaffold(containerColor = AppColors.Surface) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Surface)
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.privacy_icon_back),
                    contentDescription = "뒤로",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 20.dp)
                        .size(32.dp)
                        .clickable { onBack() },
                )
            }

            Spacer(Modifier.height(18.5.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "개인 정보 이용 동의",
                    style = TextStyle(
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = AppColors.DeepBrown900,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(11.dp))
                Text(
                    text = "통화 분석 서비스 ‘FIANO’의 원활한\n사용자 맞춤 서비스 제공을 허용해주세요.",
                    style = TextStyle(
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = AppColors.DeepBrown600,
                    ),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                TermsBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { agreed = !agreed }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Image(
                        painter = painterResource(
                            if (agreed) R.drawable.approval_checkbox_on else R.drawable.approval_checkbox_off,
                        ),
                        contentDescription = if (agreed) "동의함" else "동의 안 함",
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "개인정보 제공 및 음성 데이터 활용에 동의합니다.",
                        style = TextStyle(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.DeepBrown900,
                        ),
                    )
                }

                Spacer(Modifier.height(0.dp))

                OnboardingPrimaryButton(
                    text = "다음",
                    onClick = onNext,
                    enabled = agreed,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = AppColors.Brand,
                )
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun TermsBox(modifier: Modifier = Modifier) {
    Surface(
        color = AppColors.DeepBrown100,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Text(
                text = privacyTermsText,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = AppColors.DeepBrown900,
                ),
            )
        }
    }
}

private val privacyTermsText = """
개인정보 제3자 제공 및 통화 음성 데이터 활용 동의서
(Agreement for Third-Party Provision of Personal Information and Use of Call Voice Data)

본 동의서는 FIANO(이하 "회사")가 고객과의 통화 음성 데이터를 수집·이용하여 개인화 서비스를 제공하는 것에 대한 동의를 받기 위한 것입니다.

1. 개인정보를 수집·이용하는 자 (Controller)
FIANO ([대표자명] / [연락처])

2. 수집·이용 목적 (Purpose of Use)
• 통화 음성 데이터 분석을 통한 고객 성향·선호도 파악 및 개인화 서비스 제공
• 통화 내용의 요약 및 상담 이력 관리
• 서비스 품질 향상 및 상담 품질 개선

3. 수집하는 개인정보의 항목 (Items Collected)
• 고객과 회사 간의 실제 통화 음성 녹음 파일 (.mp3, .wav 등)
• 통화 내용에서 추출된 텍스트 요약 및 통화 중 언급된 정보
• 통화 음성 데이터 매칭을 위한 통화 메타데이터 (통화 일시, 발신·수신 번호)
• 동의자 식별 정보: 이름, 전화번호

4. 보유 및 이용 기간 (Retention Period)
• 수집된 개인정보는 사업자의 서비스 이용 기간 동안 보유하며, 사업자가 서비스를 해지하는 경우 회사가 지체 없이 파기합니다.
• 수집된 데이터는 회사가 운영하는 서버에 저장되며, 음성 인식·요약 처리를 위한 위탁을 제외하고 목적 외로 이용하거나 외부에 제공하지 않습니다.
• 관련 법령의 규정에 의하여 보존할 필요가 있는 경우 해당 법령이 정한 기간 동안 보관합니다.

5. 동의 거부 권리 및 불이익 (Right to Refuse)
귀하는 본 통화 음성 데이터 수집·이용 동의를 거부할 권리가 있습니다. 동의를 거부하실 경우 음성 분석 기반의 개인화 서비스 제공이 제한될 수 있습니다.

6. 통화 녹음 사실 고지 (Notice of Recording)
본 통화는 녹음되며, 녹음 및 데이터 활용에 동의한 경우에만 저장·처리됩니다.

동의 확인 (Consent)
동의자 정보 — 통화 전화번호와 일치하도록 정확히 입력해 주세요.
""".trimIndent()

@Preview(
    name = "Privacy consent",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=360dp,height=800dp,dpi=440",
)
@Composable
private fun PrivacyConsentPreview() {
    CallRecorderTheme {
        PrivacyConsentScreen(onBack = {}, onNext = {})
    }
}
