package com.callrecorder.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

private val DarkNavy       = Color(0xFF3D4D6B)
private val LightBg        = Color(0xFFF0F2F5)
private val WhiteCard      = Color(0xFFFFFFFF)
private val AccentBlue     = Color(0xFF3B7DD8)
private val OnDarkPrimary  = Color(0xFFFFFFFF)
private val OnDarkSub      = Color(0xFFB8C2D6)
private val OnLightPrimary = Color(0xFF1F2937)
private val OnLightSub      = Color(0xFF6B7280)
private val OnLightMuted   = Color(0xFF9CA3AF)
private val DividerLight   = Color(0xFFF0F1F4)

/* ─────────────────────────────────────────────────────
 * 업종 프리셋
 * ───────────────────────────────────────────────────── */
data class IndustryPreset(
    val label: String,
    val emoji: String,
    val keywords: List<String>,
)

val INDUSTRY_PRESETS = listOf(
    IndustryPreset("부동산중개업", "🏠", listOf(
        "매물", "계약금", "잔금", "전세", "월세", "보증금", "평수", "입주일", "등기", "매매", "임대", "계약"
    )),
    IndustryPreset("보험설계업", "🛡️", listOf(
        "보장내용", "보장", "납입", "보험료", "갱신", "특약", "청구", "보험금", "만기", "해지", "가입", "설계"
    )),
    IndustryPreset("교육사업", "📚", listOf(
        "수강료", "커리큘럼", "과정", "개강", "개강일", "수강생", "등록", "규정", "상담예약", "상담", "교재", "환불"
    )),
    IndustryPreset("판매업", "📦", listOf(
        "재고", "단가", "배송", "배송비", "발주", "결제", "반품", "교환", "입고", "품절", "주문", "환불"
    )),
    IndustryPreset("시공업", "🔨", listOf(
        "견적", "공사", "공사기간", "자재", "하자", "평수", "현장", "시공", "일정", "마감", "추가공사", "계약금"
    )),
)

/* ─────────────────────────────────────────────────────
 * 설정 메인 화면
 * ───────────────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeStore: () -> Unit,
    onLoggedOut: () -> Unit,
    auth: AuthViewModel = viewModel(),
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var currentSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    currentSubScreen?.let { sub ->
        when (sub) {
            SettingsSubScreen.USER_INFO -> UserInfoScreen(
                vm = vm,
                onBack = { currentSubScreen = null },
            )
            SettingsSubScreen.CALL_FILTER -> CallFilterScreen(
                onBack = { currentSubScreen = null },
            )
            SettingsSubScreen.SUBSCRIPTION -> SimpleSubScreen(
                title = "구독 및 결제",
                message = "구독 상태와 결제 내역을 확인하는 화면입니다.\n준비 중이에요.",
                onBack = { currentSubScreen = null },
            )
            SettingsSubScreen.CALENDAR -> ExternalCalendarScreen(
                onBack = { currentSubScreen = null },
            )
            SettingsSubScreen.PERMISSION -> PermissionSettingsScreen(
                onBack = { currentSubScreen = null },
            )
        }
        return
    }

    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = {
                    Text("설정", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = OnDarkPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Filled.Notifications, "알림", tint = OnDarkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // 프로필 카드
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkNavy)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = WhiteCard,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFDBEAFE)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    state.userName.take(1).ifBlank { "홍" },
                                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentBlue),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    state.userName.ifBlank { "사장님" },
                                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnLightPrimary),
                                )
                                Text("관리자", style = TextStyle(fontSize = 12.sp, color = OnLightSub))
                            }
                        }
                    }
                }
            }

            // 통화 설정
            item { SettingSectionLabel("통화 설정") }
            item {
                SettingGroup {
                    SettingRowToggle(
                        icon = Icons.Filled.GraphicEq,
                        iconBg = Color(0xFFFFF7ED), iconTint = Color(0xFFEA8C2E),
                        title = "통화 자동 업로드",
                        subtitle = if (state.autoAnalyzeEnabled) "녹음 감지 시 자동으로 업로드·분석" else "직접 선택해서 업로드 (기본)",
                        checked = state.autoAnalyzeEnabled,
                        onCheckedChange = { vm.setAutoAnalyze(it) },
                    )
                    SettingDivider()
                    SettingRow(
                        icon = Icons.Filled.FilterAlt,
                        iconBg = Color(0xFFFFF7ED), iconTint = Color(0xFFEA8C2E),
                        title = "중요 통화 키워드 관리",
                        subtitle = "업종별 키워드 설정",
                        onClick = { currentSubScreen = SettingsSubScreen.CALL_FILTER },
                    )
                }
            }

            // 앱 권한
            item { SettingSectionLabel("앱 권한") }
            item {
                SettingGroup {
                    SettingRow(
                        icon = Icons.Filled.Security,
                        iconBg = Color(0xFFEFF6FF), iconTint = AccentBlue,
                        title = "권한 설정",
                        subtitle = "마이크 · 통화기록 · 연락처 · 알림",
                        onClick = { currentSubScreen = SettingsSubScreen.PERMISSION },
                    )
                }
            }

            // 연동 설정
            item { SettingSectionLabel("연동 설정") }
            item {
                SettingGroup {
                    SettingRowBadge(
                        icon = Icons.Filled.CalendarMonth,
                        iconBg = Color(0xFFEFF6FF), iconTint = AccentBlue,
                        title = "외부 캘린더 연동",
                        subtitle = "Google · Naver · Kakao",
                        badgeText = "연결됨",
                        badgeBg = Color(0xFFECFDF5),
                        badgeFg = Color(0xFF059669),
                        onClick = { currentSubScreen = SettingsSubScreen.CALENDAR },
                    )
                }
            }

            // 계정 관리
            item { SettingSectionLabel("계정 관리") }
            item {
                SettingGroup {
                    SettingRow(
                        icon = Icons.Filled.Person,
                        iconBg = Color(0xFFEFF6FF), iconTint = AccentBlue,
                        title = "회원 정보 수정",
                        subtitle = "이름, 연락처, 비밀번호",
                        onClick = { currentSubScreen = SettingsSubScreen.USER_INFO },
                    )
                    SettingDivider()
                    SettingRow(
                        icon = Icons.Filled.CreditCard,
                        iconBg = Color(0xFFEFF6FF), iconTint = AccentBlue,
                        title = "구독 및 결제",
                        subtitle = "구독 상태, 결제 내역",
                        onClick = { currentSubScreen = SettingsSubScreen.SUBSCRIPTION },
                    )
                    SettingDivider()
                    SettingRow(
                        icon = Icons.Filled.PersonOff,
                        iconBg = Color(0xFFFEF2F2), iconTint = Color(0xFFEF4444),
                        title = "회원 탈퇴",
                        subtitle = "계정 및 모든 데이터 삭제",
                        titleColor = Color(0xFFEF4444),
                        onClick = { showWithdrawDialog = true },
                    )
                }
            }

            // 로그아웃
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                    OutlinedButton(
                        onClick = { auth.logout(); onLoggedOut() },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.5.dp, AccentBlue),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                    ) {
                        Text("로그아웃", style = TextStyle(fontSize = 15.sp, color = AccentBlue))
                    }
                }
            }
        }
    }

    if (showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text("회원 탈퇴", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) },
            text = { Text("탈퇴 시 모든 통화 데이터와 계정 정보가 삭제됩니다.\n정말 탈퇴하시겠어요?") },
            confirmButton = {
                Button(
                    onClick = { showWithdrawDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) { Text("탈퇴하기") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWithdrawDialog = false }) { Text("취소") }
            },
            containerColor = Color.White,
        )
    }

    state.successMessage?.let { msg ->
        LaunchedEffect(msg) { vm.clearMessage() }
    }
}

/* ─────────────────────────────────────────────────────
 * 키워드 관리 화면
 * ───────────────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallFilterScreen(
    onBack: () -> Unit,
    keywordVm: KeywordViewModel = viewModel(),
) {
    val state by keywordVm.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var labelText by remember { mutableStateOf("") }
    var actionRequired by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf<IndustryPreset?>(null) }

    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "중요 통화 키워드 관리",
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = OnDarkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 안내
            item {
                Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "키워드가 포함된 통화는 중요 통화로 분류됩니다.\n업종 프리셋으로 빠르게 설정하거나 직접 추가하세요.",
                            style = TextStyle(fontSize = 12.sp, color = AccentBlue, lineHeight = 18.sp),
                        )
                    }
                }
            }

            // 업종 프리셋
            item {
                Text(
                    "업종별 기본 키워드",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnLightSub),
                )
            }
            item {
                Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        INDUSTRY_PRESETS.forEachIndexed { index, preset ->
                            val isExpanded = selectedPreset == preset
                            Surface(
                                onClick = { selectedPreset = if (isExpanded) null else preset },
                                color = Color.Transparent,
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(preset.emoji, style = TextStyle(fontSize = 20.sp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(preset.label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnLightPrimary))
                                            Text(
                                                preset.keywords.take(4).joinToString(" · "),
                                                style = TextStyle(fontSize = 11.sp, color = OnLightMuted),
                                            )
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            null, tint = OnLightMuted, modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    // 확장 시 키워드 칩 + 일괄 추가 버튼
                                    if (isExpanded) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
                                            FlowRow(preset.keywords) { keyword ->
                                                keywordVm.addKeyword(keyword)
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    preset.keywords.forEach { keywordVm.addKeyword(it) }
                                                    selectedPreset = null
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(vertical = 10.dp),
                                            ) {
                                                Text("전체 추가", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                                            }
                                        }
                                    }
                                }
                            }
                            if (index < INDUSTRY_PRESETS.lastIndex) {
                                HorizontalDivider(color = DividerLight, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }

            // 커스텀 키워드 입력
            item {
                Text(
                    "커스텀 키워드",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnLightSub),
                )
            }
            item {
                Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 키워드 + 추가 버튼
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                placeholder = { Text("키워드 입력 (최대 20개)", fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = settingTextFieldColors(),
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        keywordVm.addKeyword(inputText.trim(), labelText, actionRequired)
                                        inputText = ""
                                        labelText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                enabled = inputText.isNotBlank() && !state.loading,
                            ) {
                                Text("추가", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // 분류 라벨 (선택)
                        OutlinedTextField(
                            value = labelText,
                            onValueChange = { labelText = it },
                            placeholder = { Text("분류 (선택, 예: 예약·불만·견적)", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = settingTextFieldColors(),
                        )
                        Spacer(Modifier.height(4.dp))
                        // 중요(조치 필요) 토글
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "중요 통화로 분류",
                                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnLightPrimary),
                                )
                                Text(
                                    "이 키워드가 나오면 조치 필요 통화로 표시",
                                    style = TextStyle(fontSize = 11.sp, color = OnLightMuted),
                                )
                            }
                            Switch(
                                checked = actionRequired,
                                onCheckedChange = { actionRequired = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
                            )
                        }
                    }
                }
            }

            // 에러/성공 메시지
            if (state.error != null) {
                item {
                    Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(state.error!!, modifier = Modifier.padding(12.dp),
                            style = TextStyle(fontSize = 12.sp, color = Color(0xFFB91C1C)))
                    }
                }
            }

            // 등록된 키워드 목록
            if (state.keywords.isNotEmpty()) {
                item {
                    Text(
                        "등록된 키워드 ${state.keywords.size}개",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnLightSub),
                    )
                }
                item {
                    Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            state.keywords.forEachIndexed { index, keyword ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(6.dp),
                                    ) {
                                        Text(
                                            keyword.keyword,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue),
                                        )
                                    }
                                    if (!keyword.label.isNullOrBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(color = Color(0xFFF1F2F7), shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                keyword.label!!,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = TextStyle(fontSize = 11.sp, color = OnLightSub),
                                            )
                                        }
                                    }
                                    if (keyword.actionRequired == 1) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(color = Color(0xFFFFF1E6), shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                "중요",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC2691C)),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { keywordVm.deleteKeyword(keyword.id) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(Icons.Filled.Close, "삭제", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < state.keywords.lastIndex) {
                                    HorizontalDivider(color = DividerLight, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                                }
                            }
                        }
                    }
                }
            } else if (!state.loading) {
                item {
                    Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("등록된 키워드가 없어요", style = TextStyle(fontSize = 13.sp, color = OnLightMuted))
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 키워드 칩 플로우 레이아웃
 * ───────────────────────────────────────────────────── */
@Composable
private fun FlowRow(keywords: List<String>, onAdd: (String) -> Unit) {
    var row = mutableListOf<String>()
    val rows = mutableListOf<List<String>>()
    keywords.forEach { word ->
        row.add(word)
        if (row.size == 4) {
            rows.add(row.toList())
            row = mutableListOf()
        }
    }
    if (row.isNotEmpty()) rows.add(row)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { rowKeywords ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowKeywords.forEach { keyword ->
                    Surface(
                        onClick = { onAdd(keyword) },
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(keyword, style = TextStyle(fontSize = 12.sp, color = AccentBlue))
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Add, null, tint = AccentBlue, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 공통 빈 서브 화면
 * ───────────────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleSubScreen(title: String, message: String, onBack: () -> Unit) {
    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = { Text(title, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = OnDarkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(message, style = TextStyle(fontSize = 14.sp, color = OnLightSub, lineHeight = 22.sp))
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 회원 정보 수정 화면
 * ───────────────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var name by remember { mutableStateOf(state.userName) }
    var phone by remember { mutableStateOf(state.userPhone) }
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = { Text("회원 정보 수정", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = OnDarkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showSuccess) {
                Surface(color = Color(0xFFECFDF5), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("✅ 저장되었습니다.", modifier = Modifier.padding(12.dp),
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF059669)))
                }
            }
            if (state.error != null) {
                Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("오류: ${state.error}", modifier = Modifier.padding(12.dp),
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFFB91C1C)))
                }
            }

            SettingInputSection("기본 정보") {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("이름", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Person, null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = settingTextFieldColors(),
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("연락처", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Phone, null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = settingTextFieldColors(),
                )
            }

            SettingInputSection("비밀번호 변경") {
                OutlinedTextField(
                    value = currentPw, onValueChange = { currentPw = it },
                    label = { Text("현재 비밀번호", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Lock, null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = settingTextFieldColors(),
                )
                OutlinedTextField(
                    value = newPw, onValueChange = { newPw = it },
                    label = { Text("새 비밀번호", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.LockOpen, null, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = settingTextFieldColors(),
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    vm.updateUserName(name)
                    vm.updateUserPhone(phone)
                    vm.saveUserInfo { showSuccess = true }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                enabled = !state.loading,
            ) {
                if (state.loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("저장", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 권한 설정 화면 (설정 → 앱 권한)
 * 런치 경로와 달리 자동 통과하지 않고, 현재 권한 상태를 보여주고 변경하게 한다.
 * ───────────────────────────────────────────────────── */
private data class PermStatus(val permission: String, val label: String, val granted: Boolean)

private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.RECORD_AUDIO       -> "마이크 (통화 녹음)"
    Manifest.permission.READ_PHONE_STATE   -> "전화 상태"
    Manifest.permission.READ_CALL_LOG      -> "통화 기록"
    Manifest.permission.READ_CONTACTS      -> "연락처 (발신자 이름)"
    Manifest.permission.POST_NOTIFICATIONS -> "알림"
    else -> permission.substringAfterLast('.')
}

private fun currentPermissionStatuses(context: Context): List<PermStatus> =
    com.callrecorder.app.REQUIRED_PERMISSIONS.map { p ->
        PermStatus(
            permission = p,
            label = permissionLabel(p),
            granted = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED,
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var statuses by remember { mutableStateOf(currentPermissionStatuses(context)) }

    // OS 앱 설정 다녀온 뒤 상태 갱신
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { statuses = currentPermissionStatuses(context) }

    // 켤 때: 시스템 권한 요청 → 응답 후 상태 갱신
    val requestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { statuses = currentPermissionStatuses(context) }

    fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        settingsLauncher.launch(intent)
    }

    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = { Text("권한 설정", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = OnDarkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 안내
            item {
                Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "통화 녹음·발신자 이름 매칭에 필요한 권한이에요.\n" +
                                    "권한을 켜거나 끄려면 토글을 누르면 시스템 설정 화면으로 바로 이동해요.",
                            style = TextStyle(fontSize = 12.sp, color = AccentBlue, lineHeight = 18.sp),
                        )
                    }
                }
            }

            // 권한 토글 목록 — 토글을 누르면 곧장 OS 앱 설정으로 이동
            item {
                Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        statuses.forEachIndexed { index, s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.label, style = TextStyle(fontSize = 14.sp, color = OnLightPrimary))
                                    Text(
                                        if (s.granted) "허용됨" else "거부됨",
                                        style = TextStyle(
                                            fontSize = 11.sp,
                                            color = if (s.granted) Color(0xFF059669) else Color(0xFFEF4444),
                                        ),
                                    )
                                }
                                Switch(
                                    checked = s.granted,
                                    onCheckedChange = { wantOn ->
                                        if (wantOn) {
                                            // 켤 때: 이동 없이 바로 시스템 권한 요청
                                            requestLauncher.launch(arrayOf(s.permission))
                                        } else {
                                            // 끌 때: 앱에서 해제 불가 → OS 앱 설정으로 이동
                                            openAppSettings()
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White, checkedTrackColor = DarkNavy,
                                        uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB),
                                    ),
                                )
                            }
                            if (index < statuses.lastIndex) {
                                HorizontalDivider(color = DividerLight, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }

            // 전체 앱 설정 바로가기
            item {
                OutlinedButton(
                    onClick = { openAppSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.5.dp, AccentBlue),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("앱 설정 열기", style = TextStyle(fontSize = 15.sp, color = AccentBlue))
                }
            }
        }
    }
}
/* ─────────────────────────────────────────────────────
 * 공통 컴포넌트
 * ───────────────────────────────────────────────────── */
enum class SettingsSubScreen { USER_INFO, CALL_FILTER, SUBSCRIPTION, CALENDAR, PERMISSION }

@Composable
private fun SettingSectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnLightSub),
    )
}

@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = WhiteCard,
        shape = RoundedCornerShape(14.dp),
    ) { Column(content = content) }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(color = DividerLight, thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
}

@Composable
private fun SettingIconBox(bg: Color, tint: Color, icon: ImageVector) {
    Box(
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun SettingRow(
    icon: ImageVector, iconBg: Color, iconTint: Color,
    title: String, subtitle: String? = null,
    titleColor: Color = OnLightPrimary,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingIconBox(bg = iconBg, tint = iconTint, icon = icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = TextStyle(fontSize = 14.sp, color = titleColor))
                if (subtitle != null) Text(subtitle, style = TextStyle(fontSize = 11.sp, color = OnLightMuted))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingRowToggle(
    icon: ImageVector, iconBg: Color, iconTint: Color,
    title: String, subtitle: String? = null,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIconBox(bg = iconBg, tint = iconTint, icon = icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 14.sp, color = OnLightPrimary))
            if (subtitle != null) Text(subtitle, style = TextStyle(fontSize = 11.sp, color = OnLightMuted))
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = DarkNavy,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB),
            ),
        )
    }
}

@Composable
private fun SettingRowBadge(
    icon: ImageVector, iconBg: Color, iconTint: Color,
    title: String, subtitle: String? = null,
    badgeText: String, badgeBg: Color, badgeFg: Color,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingIconBox(bg = iconBg, tint = iconTint, icon = icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = TextStyle(fontSize = 14.sp, color = OnLightPrimary))
                if (subtitle != null) Text(subtitle, style = TextStyle(fontSize = 11.sp, color = OnLightMuted))
            }
            Surface(color = badgeBg, shape = RoundedCornerShape(20.dp)) {
                Text(badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = badgeFg))
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SettingInputSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnLightSub),
            modifier = Modifier.padding(bottom = 8.dp))
        Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun settingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = DividerLight,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
)