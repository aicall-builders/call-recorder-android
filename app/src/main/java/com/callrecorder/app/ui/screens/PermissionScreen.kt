package com.callrecorder.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.service.RecordingObserverService
import com.callrecorder.app.ui.theme.AppColors
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
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

    // 핵심 권한(녹음) 허용되면 백그라운드 서비스/워커 시작
    LaunchedEffect(audioPerm.status.isGranted) {
        if (audioPerm.status.isGranted) {
            RecordingObserverService.start(context)
            UploadWorker.enqueuePeriodic(context)
        }
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI 통화 비서",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* 시작 흐름이라 뒤로가기 비활성. 필요시 onBack 콜백 추가 */ }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = AppColors.TextPrimary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: 도움말 */ }) {
                        Icon(
                            imageVector = Icons.Filled.HelpOutline,
                            contentDescription = "도움말",
                            tint = AppColors.TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ===== 헤드라인 =====
            Text(
                text = "서비스 권한 설정",
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                ),
            )

            Spacer(Modifier.height(10.dp))

            // ===== 안내 문구 =====
            Text(
                text = "AI 통화 비서가 예약 관리와 고객 응대를 원활하게 처리할 수 있도록 권한을 허용해 주세요. 설정은 언제든 변경 가능합니다.",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    lineHeight = 20.sp,
                ),
            )

            Spacer(Modifier.height(24.dp))

            // ===== 권한 카드들 =====
            PermissionCard(
                icon = Icons.Filled.Mic,
                title = "통화 녹음 분석 및 저장",
                desc = "에이전트가 저장된 통화 녹음들을 안전하게 불러 분석하고, 예약 정보를 추출하기 위해 필요합니다.",
                checked = audioPerm.status.isGranted,
                onToggle = { handlePermissionToggle(context, audioPerm) },
            )

            Spacer(Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Filled.People,
                title = "연락처 접근 권한",
                desc = "고객에게 전화가 왔을 때 기본 고객 정보를 자동으로 미리 표시합니다.",
                checked = contactsPerm.status.isGranted,
                onToggle = { handlePermissionToggle(context, contactsPerm) },
            )

            Spacer(Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Filled.Call,
                title = "통화 기록 접근",
                desc = "녹음된 통화의 실제 발신 번호와 연락처 이름을 자동으로 채워 정확하게 표시합니다.",
                checked = callLogPerm.status.isGranted,
                onToggle = { handlePermissionToggle(context, callLogPerm) },
            )

            Spacer(Modifier.height(12.dp))

            PermissionCard(
                icon = Icons.Filled.Notifications,
                title = "카카오 알림톡 전송",
                desc = "예약이 확정되면 빠르게 고객님께 카카오톡으로 실시간 알림을 보내드립니다.",
                checked = notifPerm?.status?.isGranted ?: true,  // SDK<33 은 권한 불필요
                onToggle = {
                    notifPerm?.let { handlePermissionToggle(context, it) }
                },
            )

            Spacer(Modifier.weight(1f))

            // ===== 대시보드로 계속하기 =====
            Button(
                onClick = onGranted,
                enabled = audioPerm.status.isGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.BrandBlue,
                    contentColor = AppColors.TextOnPrimary,
                    disabledContainerColor = AppColors.BrandBlue.copy(alpha = 0.4f),
                    disabledContentColor = AppColors.TextOnPrimary.copy(alpha = 0.7f),
                ),
            ) {
                Text(
                    "대시보드로 계속하기",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = Color(0x10000000),
                spotColor = Color(0x10000000),
            ),
        color = AppColors.Surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 좌측 아이콘 박스 (연한 파란 배경)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IconBoxBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.BrandBlue,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // 본문
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 16.sp,
                    ),
                )
            }

            Spacer(Modifier.width(8.dp))

            // 우측 토글
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.BrandBlue,
                    checkedBorderColor = AppColors.BrandBlue,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = ToggleOffTrack,
                    uncheckedBorderColor = ToggleOffTrack,
                ),
            )
        }
    }
}

// ===== 이 화면 전용 색상 =====
private val IconBoxBg = Color(0xFFEBEFF6)        // 카드 좌측 아이콘 박스 연파랑
private val ToggleOffTrack = Color(0xFFE0E2EA)   // 토글 OFF 회색 트랙