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
    var allAgreed by remember { mutableStateOf(false) }
    var termsAgreed by remember { mutableStateOf(false) }
    var privacyAgreed by remember { mutableStateOf(false) }
    var recordingAgreed by remember { mutableStateOf(false) }
    var overseasAgreed by remember { mutableStateOf(false) }
    val requiredAgreed = termsAgreed && privacyAgreed && recordingAgreed && overseasAgreed

    fun updateAll(value: Boolean) {
        allAgreed = value
        termsAgreed = value
        privacyAgreed = value
        recordingAgreed = value
        overseasAgreed = value
    }

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
                    text = "통화자 정보 활용 동의",
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
                    text = "통화 녹음, STT 변환, AI 요약 제공을 위해\n필수 약관을 확인해주세요.",
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
                        .height(310.dp),
                )

                Spacer(Modifier.height(8.dp))

                ConsentCheckRow(
                    text = "전체 동의",
                    checked = allAgreed,
                    emphasized = true,
                    onToggle = { updateAll(!requiredAgreed) },
                )
                ConsentCheckRow(
                    text = "[필수] 서비스 이용약관에 동의합니다.",
                    checked = termsAgreed,
                    onToggle = {
                        termsAgreed = !termsAgreed
                        allAgreed = termsAgreed && privacyAgreed && recordingAgreed && overseasAgreed
                    },
                )
                ConsentCheckRow(
                    text = "[필수] 개인정보 수집·이용에 동의합니다.",
                    checked = privacyAgreed,
                    onToggle = {
                        privacyAgreed = !privacyAgreed
                        allAgreed = termsAgreed && privacyAgreed && recordingAgreed && overseasAgreed
                    },
                )
                ConsentCheckRow(
                    text = "[필수] 통화 녹음 및 AI 요약 처리에 동의합니다.",
                    checked = recordingAgreed,
                    onToggle = {
                        recordingAgreed = !recordingAgreed
                        allAgreed = termsAgreed && privacyAgreed && recordingAgreed && overseasAgreed
                    },
                )
                ConsentCheckRow(
                    text = "[필수] 개인정보 국외 이전에 동의합니다.",
                    checked = overseasAgreed,
                    onToggle = {
                        overseasAgreed = !overseasAgreed
                        allAgreed = termsAgreed && privacyAgreed && recordingAgreed && overseasAgreed
                    },
                )

                Spacer(Modifier.height(8.dp))

                OnboardingPrimaryButton(
                    text = "동의하고 계속하기",
                    onClick = onNext,
                    enabled = requiredAgreed,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = AppColors.Brand,
                )
                Spacer(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun ConsentCheckRow(
    text: String,
    checked: Boolean,
    onToggle: () -> Unit,
    emphasized: Boolean = false,
) {
    Surface(
        color = if (emphasized) AppColors.DeepBrown100 else AppColors.Surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(
                    if (checked) R.drawable.approval_checkbox_on else R.drawable.approval_checkbox_off,
                ),
                contentDescription = if (checked) "동의함" else "동의 안 함",
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
                    color = AppColors.DeepBrown900,
                ),
            )
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
Fiano(피아노) 서비스 이용약관 및 개인정보 국외이전 동의서

제1장 총칙

제1조 (목적)
이 약관은 [회사명](이하 "회사")이 제공하는 통화 녹음 기반 기록·요약·고객관리 서비스 "Fiano(피아노)"의 이용 조건, 절차, 권리·의무 및 기타 필요한 사항을 정합니다.

제2조 (정의)
1. "서비스"란 회사가 운영하는 웹사이트, 데스크톱 및 모바일 앱을 통해 제공하는 통화 자동 업로드, STT 변환, 요약, AI 고객분석 및 캘린더 연동 기능을 말합니다.
2. "회원"이란 본 약관에 동의하고 회사와 이용계약을 체결한 자를 말합니다.
3. "콘텐츠"란 회원이 서비스를 통해 생성·업로드한 통화 녹음, 음성, 텍스트, 메모, 요약, 히스토리 및 고객정보 등을 말합니다.
4. "AI 요약"이란 OpenAI GPT-4o mini 모델 등을 이용해 통화 내용을 요약하는 기능을 말합니다.
5. "히스토리"란 회원이 특정 상대방 또는 고객과의 통화 기록을 누적하여 관리할 수 있도록 제공되는 기록 단위를 말합니다.

제3조 (약관의 게시 및 변경)
회사는 약관을 서비스 내에서 확인할 수 있도록 게시하며, 관계 법령을 위반하지 않는 범위에서 약관을 변경할 수 있습니다.

제2장 이용계약 및 회원관리

제4조 (약관 외 준칙)
이 약관에서 정하지 않은 사항은 개인정보 처리방침 등 회사가 정한 별도 정책과 관계 법령에 따릅니다.

제5조 (이용계약의 체결)
1. 서비스 이용계약은 회원이 본 약관 및 개인정보 수집·이용, 개인정보 국외이전에 동의하고 회사가 승낙함으로써 성립합니다.
2. 회원은 실제 소유한 계정을 통해 가입해야 하며, 만 14세 미만은 가입할 수 없습니다.
3. 회사는 타인의 정보 이용, 허위 정보 기재, 법령 위반, 사회질서에 반하는 행위 등이 있는 경우 이용을 제한할 수 있습니다.

제6조 (회원에 대한 통지)
회사는 회원에게 필요한 통지를 이메일, 서비스 내 알림, 푸시 메시지 등으로 할 수 있으며, 전체 회원 공지는 서비스 내 게시로 갈음할 수 있습니다.

제7조 (회원정보의 변경)
회원은 서비스 이용과 관련하여 회사가 보유한 연락처 등 정보를 정확하게 유지해야 하며, 변경사항을 수정하지 않아 발생한 손해에 대해 회사는 책임지지 않습니다.

제3장 서비스의 이용

제8조 (서비스의 제공 및 변경)
1. 회사는 회원에게 통화 자동 업로드, 통화녹음 원본 다운로드, STT 변환, 요약, AI 요약본 정리 서비스를 제공합니다.
2. 서비스는 연중무휴 24시간 제공을 원칙으로 하나, 시스템 점검이나 운영상 필요가 있는 경우 일시 중지될 수 있습니다.
3. 회사는 서비스 전부 또는 일부를 변경할 수 있으며, 중요한 변경은 사전에 공지합니다.
4. STT 변환 및 AI 요약 기능은 외부 전문기관(네이버클라우드, OpenAI) 처리를 거치며, 이를 위해 제10조의 처리위탁 및 국외이전에 대한 동의가 필요합니다.

제9조 (녹음 데이터의 저장 및 열람)
1. 회사는 회원의 통화 녹음 원본 파일을 회사 서버에 영구 저장하지 않습니다. 녹음 원본은 회원 기기에 남아 있고, 요약 처리 본인만 열람할 수 있습니다.
2. STT 변환 및 요약 제공을 위해 회사는 회원의 음성 또는 텍스트를 처리에 필요한 범위 내에서 외부 처리기관에 전송하며, 처리가 완료되면 그 결과를 서비스에 제공합니다.
3. 회사는 녹음 데이터 및 파생 데이터를 AI 학습·모델 개선·마케팅 등 목적 외 용도로 활용하지 않습니다.
4. 회원은 필요한 경우 원본 통화 파일을 별도로 백업해야 하며, 회원이 삭제한 데이터는 복구되지 않습니다.

제10조 (개인정보 처리위탁 및 국외이전)
회사는 서비스 제공을 위해 다음 개인정보 처리를 위탁하거나 일부를 국외로 이전합니다.

[처리위탁 - 국내]
- 수탁자: 네이버클라우드 주식회사
- 위탁업무: 음성의 텍스트 변환(STT)

[국외이전]
- 이전받는 자: OpenAI, L.L.C.
- 이전 국가: 미국
- 이전 항목: 텍스트로 변환된 통화 내용 중 요약 처리에 필요한 데이터
- 이전 목적: AI 요약 기능 제공(GPT-4o mini 모델 이용)
- 이전 일시 및 방법: 요약 기능 이용 시점에 정보통신망을 통한 전송
- 이전받는 자의 이용기간: 요약 처리 완료 시까지(처리 후 미보관)

회원은 개인정보의 국외이전에 동의하지 않을 권리가 있으며, 동의하지 않을 경우 요약 기능 이용이 제한될 수 있습니다. 회사는 수탁자 및 이전받는 자가 개인정보를 안전하게 처리하도록 필요한 조치를 취합니다.

제11조 (콘텐츠의 관리)
회원은 서비스에 업로드하거나 생성한 콘텐츠에 대한 권리를 보유하며, 회사는 서비스 운영 및 제공에 필요한 최소 범위 내에서만 이를 처리합니다. 회사는 회원의 콘텐츠를 별도로 백업하거나 보관하지 않으며, 회원이 삭제한 콘텐츠는 복구되지 않습니다.

제12조 (민감정보 및 이용자의 책임)
1. 회원은 주민등록번호, 고유식별정보, 계좌·카드번호 등 금융정보, 건강·질병 정보, 종교·정치적 견해, 성생활 등 민감정보가 포함된 내용을 녹음·저장하지 않도록 주의해야 합니다.
2. 회원이 위 정보를 포함하여 녹음·저장함으로써 발생하는 문제에 대해 책임은 회원에게 있으며, 회사는 이에 대해 책임을 지지 않습니다.
3. 통화 상대방 등 제3자의 음성이 녹음될 수 있으므로, 회원은 관련 법령에 따라 필요한 경우 상대방의 동의를 직접 얻어야 합니다.

제4장 권리와 책임

제13조 (회원의 의무)
회원은 타인의 정보를 도용하거나, 저작권 등 지식재산권을 침해하거나, 서비스 운영을 방해하거나, 관련 법령을 위반하는 행위를 해서는 안 됩니다.

제14조 (회사의 의무)
회사는 법령과 본 약관이 금지하거나 사회질서에 반하는 행위를 하지 않으며, 안정적으로 서비스를 제공하기 위해 최선을 다합니다. 회사는 개인정보 보호를 위해 보안시스템을 구축하고 개인정보 처리방침을 수립·공개·준수합니다.

제15조 (개인정보의 보호)
회사는 회원의 개인정보를 관련 법령에 따라 보호하며, 개인정보 수집·이용·보관·파기에 관한 사항은 별도의 개인정보 처리방침에 따릅니다.

제16조 (데이터의 삭제 및 회원 탈퇴)
회원은 언제든지 서비스 내 또는 고객센터를 통해 콘텐츠 삭제 및 회원 탈퇴를 요청할 수 있습니다. 회원이 탈퇴하는 경우 모든 데이터는 복구할 수 없도록 삭제되며, 관계 법령상 보관이 필요한 정보는 해당 기간 동안 분리 보관 후 파기됩니다.

제17조 (이용제한 및 계약 해지)
회사는 회원이 약관을 위반하거나 불법행위를 한 경우 서비스 이용을 제한하거나 계약을 해지할 수 있습니다. 계약 해지 시 회원 데이터 처리는 제16조에 따릅니다.

제18조 (손해배상 및 면책)
회사는 회사의 귀책사유로 회원에게 손해가 발생한 경우 관련 법령에 따른 손해를 배상합니다. 다만 천재지변, 시스템 장애 등 불가항력, 회원의 귀책사유, 회원 기기에 저장된 데이터의 분실·훼손, 백업 의무 미이행 등으로 발생한 손해에 대해서는 책임지지 않습니다.

제5장 기타

제19조 (분쟁의 해결 및 준거법)
회사와 회원 간 분쟁은 원만한 해결을 위해 성실히 협의하며, 협의가 이루어지지 않을 경우 민사소송법에 따른 관할법원에 제소합니다. 본 약관은 대한민국 법률에 따라 해석되고 적용됩니다.

부칙
본 약관은 2026년 07월 06일부터 시행합니다.

© 2026, Fiano. All Rights Reserved.
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
