package com.callrecorder.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.R
import com.callrecorder.app.service.RecordingObserverService
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.ui.theme.CallRecorderTheme
import com.callrecorder.app.worker.UploadWorker
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * 서비스 권한 설정 화면 (시안 4).
 *
 * 토글 3개:
 * 1. 통화 녹음 분석 및 저장 -> READ_MEDIA_AUDIO (또는 READ_EXTERNAL_STORAGE for SDK<33)
 * 2. 연락처 접근 권한       -> READ_CONTACTS
 * 3. 카카오 알림톡 전송      -> POST_NOTIFICATIONS (앱 알림 권한)
 *
 * - 토글 OFF -> 시스템 권한 다이얼로그 요청
 * - 토글 ON  -> 시스템 설정 화면 열기 (해제 안내)
 * - 한 번 거부된 후엔 다이얼로그가 안 떠서 설정 앱으로 이동
 *
 * "대시보드로 계속하기" 버튼 -> 1번 권한이 허용된 상태에서만 활성화.
 *   onContinue() 호출 시 옵저버 서비스 + 업로드 워커 시작.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current

    // 1) 녹음 파일 접근
    val audioPermName = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val audioPerm = rememberPermissionState(audioPermName)

    // 2) 연락처
    val contactsPerm = rememberPermissionState(Manifest.permission.READ_CONTACTS)

    // 2-1) 통화 기록 (녹음 시각으로 정확한 발신 번호/이름 매칭)
    val callLogPerm = rememberPermissionState(Manifest.permission.READ_CALL_LOG)

    // 3) 알림 (카카오 알림톡 수신용으로 매핑)
    //    Android 13(API 33) 미만에선 별도 권한 불필요 -> 이미 ON 상태로 표시
    val notifPerm = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null
    var audioToggleOn by remember { mutableStateOf(false) }
    var contactsToggleOn by remember { mutableStateOf(false) }
    var callLogToggleOn by remember { mutableStateOf(false) }
    var notificationsToggleOn by remember { mutableStateOf(false) }

    // 핵심 권한(녹음) 허용되면 백그라운드 서비스/워커 시작
    LaunchedEffect(audioPerm.status.isGranted) {
        if (audioPerm.status.isGranted) {
            RecordingObserverService.start(context)
            UploadWorker.enqueuePeriodic(context)
        }
    }
    LaunchedEffect(audioPerm.status.isGranted) {
        if (audioPerm.status.isGranted && audioToggleOn) audioToggleOn = true
    }
    LaunchedEffect(contactsPerm.status.isGranted) {
        if (contactsPerm.status.isGranted && contactsToggleOn) contactsToggleOn = true
    }
    LaunchedEffect(callLogPerm.status.isGranted) {
        if (callLogPerm.status.isGranted && callLogToggleOn) callLogToggleOn = true
    }
    LaunchedEffect(notifPerm?.status?.isGranted) {
        if ((notifPerm?.status?.isGranted ?: false) && notificationsToggleOn) notificationsToggleOn = true
    }

    PermissionContent(
        audioGranted = audioToggleOn,
        contactsGranted = contactsToggleOn,
        callLogGranted = callLogToggleOn,
        notificationsGranted = notificationsToggleOn,
        canContinue = audioPerm.status.isGranted,
        onAudioToggle = {
            audioToggleOn = true
            handlePermissionToggle(context, audioPerm)
        },
        onContactsToggle = {
            contactsToggleOn = true
            handlePermissionToggle(context, contactsPerm)
        },
        onCallLogToggle = {
            callLogToggleOn = true
            handlePermissionToggle(context, callLogPerm)
        },
        onNotificationsToggle = {
            notificationsToggleOn = true
            notifPerm?.let { handlePermissionToggle(context, it) }
        },
        onGranted = onGranted,
    )
}

@Composable
private fun PermissionContent(
    audioGranted: Boolean,
    contactsGranted: Boolean,
    callLogGranted: Boolean,
    notificationsGranted: Boolean,
    canContinue: Boolean = audioGranted,
    onAudioToggle: () -> Unit,
    onContactsToggle: () -> Unit,
    onCallLogToggle: () -> Unit,
    onNotificationsToggle: () -> Unit,
    onGranted: () -> Unit,
) {
    Scaffold(containerColor = Color.White) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                IconButton(
                    onClick = { },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icon_header_back),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(Modifier.height(45.dp))

            Text(
                text = "서비스 권한 설정",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1A1A21),
                    lineHeight = 32.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(11.dp))

            Text(
                text = "AI 통화 비서가 원활한 관리를 할 수 있도록\n권한을 허용해 주세요.",
                style = TextStyle(
                    fontSize = 18.sp,
                    color = Color(0xFF5A5F6C),
                    lineHeight = 24.sp,
                    letterSpacing = (-0.5).sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(33.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionCard(
                    iconRes = R.drawable.icon_access_file,
                    title = "통화 녹음 분석 및 저장",
                    desc = "통화 녹음 분석시 파일 접근 권한 허용,\n통화 내용 분석 및 정보 추출 허용",
                    checked = audioGranted,
                    onToggle = onAudioToggle,
                )

                PermissionCard(
                    iconRes = R.drawable.icon_access_num,
                    title = "연락처 접근 권한",
                    desc = "휴대폰 연락처 정보 접근 권한 허용,\n전화 수신 시 기본 고객 정보 표시 허용",
                    checked = contactsGranted,
                    onToggle = onContactsToggle,
                )

                PermissionCard(
                    iconRes = R.drawable.icon_access_mach,
                    title = "통화 기록 접근",
                    desc = "녹음된 통화의 발신 번호와 연락처 이름 자동 매칭 허용",
                    checked = callLogGranted,
                    onToggle = onCallLogToggle,
                )

                PermissionCard(
                    iconRes = R.drawable.icon_access_alarm,
                    title = "카카오 알림톡 전송",
                    desc = "일정 등록 시 카카오톡 실시간 알림\n송신 허용",
                    checked = notificationsGranted,
                    onToggle = onNotificationsToggle,
                )
            }

            Spacer(Modifier.weight(1f))

            OnboardingPrimaryButton(
                text = "통화비서 이용하기",
                onClick = onGranted,
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 64.dp),
            )
        }
    }
}

@Preview(
    name = "Permission",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=360dp,height=800dp,dpi=440",
)
@Composable
private fun PermissionScreenPreview() {
    CallRecorderTheme {
        PermissionContent(
            audioGranted = false,
            contactsGranted = false,
            callLogGranted = false,
            notificationsGranted = false,
            onAudioToggle = {},
            onContactsToggle = {},
            onCallLogToggle = {},
            onNotificationsToggle = {},
            onGranted = {},
        )
    }
}

/**
 * 권한 토글 처리.
 * - 거부된 적 없으면 -> 다이얼로그 요청
 * - 이미 거부됐거나 ON 상태면 -> 시스템 설정으로 이동
 */
@OptIn(ExperimentalPermissionsApi::class)
private fun handlePermissionToggle(
    context: android.content.Context,
    permState: PermissionState,
) {
    if (permState.status.isGranted) {
        // 이미 허용됨 -> 설정에서 해제하라고 안내 (시스템 설정 열기)
        openAppSettings(context)
    } else {
        // 거부 상태 (처음이든 두 번째든) -> 다이얼로그 요청 시도.
        // 영구 거부 후엔 다이얼로그가 안 뜨므로, 사용자가 한 번 더 누르면 설정으로 이동.
        permState.launchPermissionRequest()
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { context.startActivity(intent) }
}

/* ─────────────────────────────────────────────────
 * 컴포저블: 권한 카드
 * ───────────────────────────────────────────────── */

@Composable
private fun PermissionCard(
    iconRes: Int,
    title: String,
    desc: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.DeepBrown50,
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A21),
                    ),
                )
                Text(
                    text = desc,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = Color(0xFF656565),
                        lineHeight = 16.sp,
                    ),
                )
            }

            Spacer(Modifier.width(8.dp))

            Image(
                painter = painterResource(
                    if (checked) R.drawable.icon_toggle_l_on else R.drawable.icon_toggle_l_off
                ),
                contentDescription = null,
                modifier = Modifier.size(
                    width = 48.dp,
                    height = if (checked) 48.dp else 52.dp,
                ),
            )
        }
    }
}
