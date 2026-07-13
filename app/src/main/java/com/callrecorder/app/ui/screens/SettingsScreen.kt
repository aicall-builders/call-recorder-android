package com.callrecorder.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.callrecorder.app.R
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.ui.theme.AppColors

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
private val FianoSettingsBg = AppColors.DeepBrown900
private val FianoSettingsContent = Color(0xFFFFFFFF)
private val FianoSettingsText = AppColors.DeepBrown900
private val FianoSettingsSubText = Color(0xFF99A1B0)
private val FianoSettingsLine = AppColors.DeepBrown200
private val FianoSettingsAvatar = AppColors.DeepBrown50
private val FianoSettingsMutedBg = AppColors.DeepBrown100

/* ─────────────────────────────────────────────────────
 * 업종 프리셋
 * ───────────────────────────────────────────────────── */
data class IndustryPreset(
    val label: String,
    @DrawableRes val iconRes: Int,
    val keywords: List<String>,
)

val INDUSTRY_PRESETS = listOf(
    IndustryPreset("부동산중개업", R.drawable.icon_biz_real_estate, listOf(
        "매물", "계약금", "잔금", "전세", "월세", "보증금", "평수", "입주일", "등기", "매매", "임대", "계약"
    )),
    IndustryPreset("보험설계업", R.drawable.icon_biz_insurance, listOf(
        "보장내용", "보장", "납입", "보험료", "갱신", "특약", "청구", "보험금", "만기", "해지", "가입", "설계"
    )),
    IndustryPreset("교육사업", R.drawable.icon_biz_education, listOf(
        "수강료", "커리큘럼", "과정", "개강", "개강일", "수강생", "등록", "규정", "상담예약", "상담", "교재", "환불"
    )),
    IndustryPreset("판매업", R.drawable.icon_biz_retail, listOf(
        "재고", "단가", "배송", "배송비", "발주", "결제", "반품", "교환", "입고", "품절", "주문", "환불"
    )),
    IndustryPreset("시공업", R.drawable.icon_biz_construction, listOf(
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
    onExternalCalendarClick: (() -> Unit)? = null,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
    auth: AuthViewModel = viewModel(),
    vm: SettingsViewModel = viewModel(),
    calendarVm: CalendarViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val calendarState by calendarVm.state.collectAsState()
    var currentSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }
    var showCalendarSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.syncPrefs()
        calendarVm.loadConnections()
    }

    BackHandler(enabled = showCalendarSheet || currentSubScreen != null) {
        when {
            showCalendarSheet -> showCalendarSheet = false
            currentSubScreen != null -> currentSubScreen = null
        }
    }

    currentSubScreen?.let { sub ->
        when (sub) {
            SettingsSubScreen.USER_INFO -> UserInfoScreen(
                vm = vm,
                onBack = { currentSubScreen = null },
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )
            SettingsSubScreen.CALL_FILTER -> CallFilterScreen(
                onBack = { currentSubScreen = null },
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )
            SettingsSubScreen.SUBSCRIPTION -> SimpleSubScreen(
                title = "구독 및 결제",
                message = "구독 상태와 결제 내역을 확인하는 화면입니다.\n준비 중이에요.",
                onBack = { currentSubScreen = null },
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )
            SettingsSubScreen.CALENDAR -> ExternalCalendarScreen(
                onBack = { currentSubScreen = null },
            )
            SettingsSubScreen.PERMISSION -> PermissionSettingsScreen(
                onBack = { currentSubScreen = null },
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )
        }
        return
    }

    Scaffold(containerColor = FianoSettingsBg) { padding ->
        val heroEmail = state.accountEmail.ifBlank { "계정 정보 없음" }
        val heroProvider = loginProviderLabel(state.loginProvider)
        val heroName = state.userName
            .ifBlank { state.accountEmail.substringBefore("@").takeIf { it.isNotBlank() }.orEmpty() }
            .ifBlank { heroProvider }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(FianoSettingsBg),
        ) {
            FianoTopHeader(
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(FianoSettingsAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        heroName.take(1).ifBlank { "내" },
                        style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = FianoSettingsBg),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        heroName,
                        style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$heroProvider · $heroEmail",
                        style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = FianoSettingsSubText),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = FianoSettingsContent,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        val connectedProvider = calendarState.connections.firstOrNull()?.provider
                        val connectedServiceName = connectedProvider?.calendarProviderLabel()
                        SettingsMainSection {
                            SettingsMainRow(
                                title = "외부 캘린더 연동",
                                subtitle = connectedServiceName,
                                onClick = {
                                    if (onExternalCalendarClick != null) {
                                        onExternalCalendarClick()
                                    } else {
                                        showCalendarSheet = true
                                    }
                                },
                                trailing = {
                                    SettingsConnectionTag(
                                        text = if (connectedProvider != null) "연결 해제" else "연동하기",
                                        onClick = connectedProvider?.let { provider ->
                                            { calendarVm.disconnect(provider) }
                                        },
                                    )
                                },
                            )
                        }
                    }

                    item {
                        SettingsMainSection {
                            SettingsMainRow(
                                title = "통화 자동 분석",
                                trailing = {
                                    FianoSettingToggle(
                                        checked = state.autoAnalyzeEnabled,
                                        onCheckedChange = { vm.setAutoAnalyze(it) },
                                    )
                                },
                            )
                            SettingsMainRow(
                                title = "통화 자동 필터링",
                                trailing = {
                                    FianoSettingToggle(
                                        checked = state.importantCategories.isNotEmpty(),
                                        onCheckedChange = { enabled ->
                                            if (!enabled) {
                                                state.importantCategories.forEach { vm.toggleCategory(it) }
                                            } else if (state.importantCategories.isEmpty()) {
                                                ALL_CALL_CATEGORIES.forEach { vm.toggleCategory(it) }
                                            }
                                        },
                                    )
                                },
                            )
                            SettingsMainRow(
                                title = "필터링 키워드 관리",
                                onClick = { currentSubScreen = SettingsSubScreen.CALL_FILTER },
                                trailing = { SettingsChevron() },
                            )
                        }
                    }

                    item {
                        SettingsMainSection {
                            SettingsMainRow(
                                title = "계정 정보",
                                onClick = { currentSubScreen = SettingsSubScreen.USER_INFO },
                                trailing = { SettingsChevron() },
                            )
                            SettingsMainRow(
                                title = "구독 및 결제",
                                onClick = { currentSubScreen = SettingsSubScreen.SUBSCRIPTION },
                                trailing = { SettingsChevron() },
                            )
                            OutlinedButton(
                                onClick = { auth.logout(); onLoggedOut() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp)
                                    .height(48.dp),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, FianoSettingsBg),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = FianoSettingsBg),
                                contentPadding = PaddingValues(horizontal = 24.dp),
                            ) {
                                Text(
                                    "로그아웃",
                                    style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCalendarSheet) {
        ExternalCalendarBottomSheetOverlay(
            onDismiss = { showCalendarSheet = false },
        )
    }

    state.successMessage?.let { msg ->
        LaunchedEffect(msg) { vm.clearMessage() }
    }
}

@Composable
fun ExternalCalendarBottomSheetOverlay(
    onDismiss: () -> Unit,
    calendarVm: CalendarViewModel = viewModel(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {},
            color = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            ExternalCalendarBottomSheetContent(
                vm = calendarVm,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun ExternalCalendarBottomSheetContent(
    vm: CalendarViewModel,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val redirectBase = "https://dsoh4vn0si08a.cloudfront.net/oauth"
    val provider = "google"
    val isConnected = state.connections.any { it.provider == provider }

    LaunchedEffect(Unit) {
        vm.loadConnections()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp),
    ) {
        Text(
            "외부 캘린더 연동",
            style = TextStyle(
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = FianoSettingsText,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "예약 일정이 등록되면 연결한 캘린더에 자동으로 반영됩니다.",
            style = TextStyle(
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                color = FianoSettingsSubText,
            ),
        )
        Spacer(Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF7F7F7),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Google 캘린더",
                        style = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = FianoSettingsText,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isConnected) "현재 연동되어 있습니다." else "아직 연동되지 않았습니다.",
                        style = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Normal,
                            color = FianoSettingsSubText,
                        ),
                    )
                }
                SettingsConnectionTag(if (isConnected) "연동됨" else "미연동")
            }
        }

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "오류: ${state.error}",
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFE53E32),
                ),
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExternalCalendarSheetButton(
                label = "취소",
                filled = false,
                enabled = !state.actionLoading,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            ExternalCalendarSheetButton(
                label = if (isConnected) "연동 해제" else "연동하기",
                filled = true,
                enabled = !state.actionLoading,
                showProgress = state.actionLoading,
                onClick = {
                    if (isConnected) {
                        vm.disconnect(provider)
                    } else {
                        val oauthState = "app:" + java.util.UUID.randomUUID().toString()
                        val redirectUri = "$redirectBase/$provider"
                        vm.getOAuthUrl(provider, redirectUri, oauthState) { authUrl ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExternalCalendarSheetButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showProgress: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        color = if (filled) FianoSettingsBg else Color.White,
        shape = RoundedCornerShape(999.dp),
        border = if (filled) null else BorderStroke(1.dp, FianoSettingsBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                    color = if (filled) Color.White else FianoSettingsBg,
                ),
            )
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 키워드 관리 화면
 * ───────────────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallFilterScreen(
    onBack: () -> Unit,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
    keywordVm: KeywordViewModel = viewModel(),
) {
    val state by keywordVm.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var labelText by remember { mutableStateOf("") }
    var actionRequired by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf<IndustryPreset?>(null) }

    Scaffold(containerColor = FianoSettingsBg) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FianoSettingsBg)
                .padding(padding),
            contentPadding = PaddingValues(0.dp),
        ) {
            item {
                FianoSettingsSubHeader(
                    title = "통화 필터링 관리",
                    onBack = onBack,
                    onNotificationClick = onNotificationClick,
                    hasNotification = hasNotification,
                )
            }

            item {
                Text(
                    "지정한 키워드가 포함된 통화를 자동으로 분류합니다.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
                    style = TextStyle(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White,
                    ),
                )
            }

            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillParentMaxHeight().fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                    ) {
                        SettingsFilterSectionTitle(
                            title = "기본 키워드",
                            subtitle = "업종별 키워드를 선택해 주세요.",
                        )

                        INDUSTRY_PRESETS.forEachIndexed { index, preset ->
                            val isExpanded = selectedPreset == preset
                            Surface(
                                onClick = { selectedPreset = if (isExpanded) null else preset },
                                color = AppColors.DeepBrown100,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                                    ) {
                                        Image(
                                            painter = painterResource(preset.iconRes),
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                preset.label,
                                                style = TextStyle(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AppColors.DeepBrown900,
                                                ),
                                            )
                                        }
                                        Icon(
                                            if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            null,
                                            tint = AppColors.DeepBrown900,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    if (isExpanded) {
                                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                            KeywordFlowRow(
                                                keywords = preset.keywords,
                                                onAdd = { keyword -> keywordVm.addKeyword(keyword) },
                                            )
                                        }
                                    }
                                }
                            }
                            if (index < INDUSTRY_PRESETS.lastIndex) Spacer(Modifier.height(16.dp))
                        }

                        Spacer(Modifier.height(16.dp))
                        SettingsFilterSectionTitle(
                            title = "커스텀 키워드",
                            subtitle = "최대 5개까지 등록 가능합니다.",
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = {
                                        Text(
                                            "키워드를 입력하세요",
                                            style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = AppColors.DeepBrown500),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = AppColors.DeepBrown100,
                                        unfocusedContainerColor = AppColors.DeepBrown100,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = AppColors.DeepBrown900,
                                        focusedTextColor = AppColors.DeepBrown900,
                                        unfocusedTextColor = AppColors.DeepBrown900,
                                    ),
                                )
                                OutlinedButton(
                                    onClick = {
                                        if (inputText.isNotBlank()) {
                                            keywordVm.addKeyword(inputText.trim(), labelText, actionRequired)
                                            inputText = ""
                                            labelText = ""
                                        }
                                    },
                                    enabled = inputText.isNotBlank() && !state.loading,
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, AppColors.DeepBrown900),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = AppColors.DeepBrown900,
                                        disabledContentColor = AppColors.DeepBrown500,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                                ) {
                                    Text("추가", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "중요 통화로 분류",
                                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.DeepBrown900),
                                    )
                                    Text(
                                        "이 키워드가 나오면 조치 필요 통화로 표시",
                                        style = TextStyle(fontSize = 11.sp, color = AppColors.DeepBrown500),
                                    )
                                }
                                Switch(
                                    checked = actionRequired,
                                    onCheckedChange = { actionRequired = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AppColors.FianoBlack900,
                                        checkedBorderColor = AppColors.FianoBlack900,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = AppColors.FianoBlack300,
                                        uncheckedBorderColor = AppColors.FianoBlack300,
                                    ),
                                )
                            }
                        }

                        if (state.keywords.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            KeywordFlowRow(
                                keywords = state.keywords.map { it.keyword },
                                selected = true,
                                showRemove = true,
                                onAdd = {},
                                onRemove = { keyword ->
                                    state.keywords.firstOrNull { it.keyword == keyword }?.let { keywordVm.deleteKeyword(it.id) }
                                },
                            )
                        } else if (!state.loading) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "등록된 키워드가 없어요",
                                style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = AppColors.DeepBrown500),
                            )
                        }

                        if (state.error != null) {
                            Spacer(Modifier.height(16.dp))
                            Surface(color = AppColors.SignalRed50, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    state.error!!,
                                    modifier = Modifier.padding(12.dp),
                                    style = TextStyle(fontSize = 12.sp, color = AppColors.SignalRed700),
                                )
                            }
                        }

                        if (state.loading) {
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AppColors.DeepBrown900, modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, AppColors.DeepBrown900),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.DeepBrown900),
                        ) {
                            Text(
                                "키워드 저장",
                                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                            )
                        }
                        Spacer(Modifier.height(80.dp))
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
private fun SettingsFilterSectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.DeepBrown900),
        )
        Text(
            subtitle,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = AppColors.DeepBrown500),
        )
    }
}

@Composable
private fun KeywordFlowRow(
    keywords: List<String>,
    selected: Boolean = false,
    showRemove: Boolean = false,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit = {},
) {
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
                        color = if (selected) AppColors.DeepBrown900 else Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = if (selected) null else BorderStroke(1.dp, AppColors.DeepBrown200),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                keyword,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) Color.White else AppColors.DeepBrown500,
                                ),
                            )
                            if (showRemove) {
                                Text(
                                    "×",
                                    modifier = Modifier.clickable { onRemove(keyword) },
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White),
                                )
                            }
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
private fun SimpleSubScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    Scaffold(containerColor = FianoSettingsBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(FianoSettingsBg),
        ) {
            FianoSettingsSubHeader(
                title = title,
                onBack = onBack,
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )
            FianoSettingsSheet(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        message,
                        style = TextStyle(fontSize = 14.sp, color = AppColors.DeepBrown500, lineHeight = 22.sp),
                    )
                }
            }
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 회원 정보 수정 화면
 * ───────────────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    val state by vm.state.collectAsState()
    var showWithdrawDialog by remember { mutableStateOf(false) }
    val loginProvider = loginProviderLabel(state.loginProvider)
    val accountEmail = state.accountEmail.ifBlank { "이메일 정보 없음" }
    val fallbackUserName = state.accountEmail.substringBefore("@").takeIf { it.isNotBlank() }.orEmpty()
    var userNameInput by remember(state.userName, fallbackUserName) {
        mutableStateOf(state.userName.ifBlank { fallbackUserName })
    }
    var userNameSavedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = FianoSettingsBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(FianoSettingsBg),
        ) {
            FianoSettingsSubHeader(
                title = "계정 정보",
                onBack = onBack,
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )
            FianoSettingsSheet(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.error != null) {
                        Surface(color = AppColors.SignalRed50, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "오류: ${state.error}",
                                modifier = Modifier.padding(12.dp),
                                style = TextStyle(fontSize = 13.sp, color = AppColors.SignalRed700),
                            )
                        }
                    }

                    AccountInfoCard {
                        AccountNameEditRow(
                            value = userNameInput,
                            onValueChange = {
                                userNameInput = it
                                vm.updateUserName(it)
                            },
                            onSave = {
                                vm.saveUserName(userNameInput) {
                                    userNameSavedMessage = "저장됐어요"
                                }
                            },
                        )
                        SettingsMainDivider()
                        AccountInfoRow(label = "로그인 방식", value = loginProvider)
                        SettingsMainDivider()
                        AccountInfoRow(label = "계정 이메일", value = accountEmail)
                    }

                    userNameSavedMessage?.let { message ->
                        Text(
                            message,
                            modifier = Modifier.padding(start = 16.dp),
                            style = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.SignalRed700,
                            ),
                        )
                        LaunchedEffect(message) {
                            kotlinx.coroutines.delay(1600)
                            userNameSavedMessage = null
                        }
                    }

                    Text(
                        "이메일과 프로필 정보는 가입한 간편 로그인 계정에서 변경할 수 있습니다.",
                        style = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = AppColors.DeepBrown500,
                        ),
                    )

                    Surface(
                        onClick = { showWithdrawDialog = true },
                        color = Color.White,
                        border = BorderStroke(1.dp, FianoSettingsBg),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "회원 탈퇴",
                                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = FianoSettingsBg),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showWithdrawDialog) {
        FianoConfirmDialog(
            title = "회원 탈퇴",
            message = "탈퇴 시 모든 통화 데이터와\n계정 정보가 삭제됩니다.\n정말 탈퇴하시겠어요?",
            confirmLabel = "탈퇴",
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { showWithdrawDialog = false },
        )
    }
}

private fun loginProviderLabel(provider: String): String = when (provider.lowercase()) {
    "kakao" -> "카카오"
    "google" -> "Google"
    "naver" -> "네이버"
    else -> "간편 로그인"
}

private fun String.calendarProviderLabel(): String = when (lowercase()) {
    "google" -> "Google 캘린더"
    "kakao" -> "카카오 캘린더"
    "naver" -> "네이버 캘린더"
    else -> this
}

@Composable
private fun AccountInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, FianoSettingsLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun AccountInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(88.dp),
            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = AppColors.DeepBrown500),
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = FianoSettingsText),
        )
    }
}

@Composable
private fun AccountNameEditRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "사용자명",
            modifier = Modifier.width(88.dp),
            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = AppColors.DeepBrown500),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = FianoSettingsText),
                cursorBrush = SolidColor(FianoSettingsText),
            )
        }
        Text(
            "저장",
            modifier = Modifier.clickable { onSave() },
            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = FianoSettingsText),
        )
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
private fun PermissionSettingsScreen(
    onBack: () -> Unit,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
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

    Scaffold(containerColor = FianoSettingsBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(FianoSettingsBg),
        ) {
            FianoSettingsSubHeader(
                title = "권한 설정",
                onBack = onBack,
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )
            FianoSettingsSheet(modifier = Modifier.weight(1f)) {
                Text(
                    "통화 녹음과 고객 정보 정리를 위해 필요한 권한을 관리합니다.",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = AppColors.DeepBrown500),
                )

                Surface(color = FianoSettingsMutedBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        statuses.forEachIndexed { index, s ->
                            SettingsPermissionRow(
                                status = s,
                                onCheckedChange = { wantOn ->
                                    if (wantOn) {
                                        requestLauncher.launch(arrayOf(s.permission))
                                    } else {
                                        openAppSettings()
                                    }
                                },
                            )
                            if (index < statuses.lastIndex) {
                                HorizontalDivider(
                                    color = FianoSettingsLine,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(start = 68.dp),
                                )
                            }
                        }
                    }
                }

                Surface(
                    onClick = { openAppSettings() },
                    color = Color.White,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, FianoSettingsBg),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "앱 설정 열기",
                            style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = FianoSettingsBg),
                        )
                    }
                }
            }
        }
    }
}

@DrawableRes
private fun permissionIconRes(permission: String): Int = when (permission) {
    Manifest.permission.RECORD_AUDIO -> R.drawable.icon_access_file
    Manifest.permission.READ_CONTACTS -> R.drawable.icon_access_num
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_PHONE_STATE -> R.drawable.icon_access_mach
    Manifest.permission.POST_NOTIFICATIONS -> R.drawable.icon_access_alarm
    else -> R.drawable.icon_access_file
}

@Composable
private fun SettingsPermissionRow(
    status: PermStatus,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(permissionIconRes(status.permission)),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                status.label,
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = FianoSettingsText),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (status.granted) "허용됨" else "허용 필요",
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (status.granted) AppColors.DeepBrown600 else AppColors.SignalRed500,
                ),
            )
        }
        FianoSettingToggle(
            checked = status.granted,
            onCheckedChange = onCheckedChange,
        )
    }
}
/* ─────────────────────────────────────────────────────
 * 공통 컴포넌트
 * ───────────────────────────────────────────────────── */
enum class SettingsSubScreen { USER_INFO, CALL_FILTER, SUBSCRIPTION, CALENDAR, PERMISSION }

@Composable
private fun FianoSettingsSubHeader(
    title: String,
    onBack: () -> Unit,
    showAlarm: Boolean = true,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.detail_icon_back),
                contentDescription = "뒤로",
                modifier = Modifier.size(32.dp).clickable { onBack() },
            )
            Text(
                title,
                style = TextStyle(
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showAlarm) {
            FianoHeaderAlarmButton(
                onClick = onNotificationClick,
                hasNotification = hasNotification,
            )
        }
    }
}

@Composable
private fun FianoSettingsSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = FianoSettingsContent,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsMainSection(
    hasDivider: Boolean = false,
    verticalPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = verticalPadding, bottom = bottomPadding),
            content = content,
        )
        if (hasDivider) SettingsMainDivider()
    }
}

@Composable
private fun SettingsMainRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = FianoSettingsText),
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = FianoSettingsSubText),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
private fun SettingsMainDivider() {
    HorizontalDivider(
        color = FianoSettingsLine,
        thickness = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FianoSettingToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val track = if (checked) AppColors.FianoBlack900 else AppColors.FianoBlack300
    val knobAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(track)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = knobAlignment,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun SettingsConnectionTag(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(FianoSettingsBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, color = Color.White),
        )
    }
}

@Composable
private fun SettingsChevron() {
    Image(
        painter = painterResource(R.drawable.settings_icon_right_figma),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
    )
}

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
                if (subtitle != null) Text(subtitle, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = OnLightMuted))
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
            if (subtitle != null) Text(subtitle, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = OnLightMuted))
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AppColors.FianoBlack900,
                checkedBorderColor = AppColors.FianoBlack900,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = AppColors.FianoBlack300,
                uncheckedBorderColor = AppColors.FianoBlack300,
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
                if (subtitle != null) Text(subtitle, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = OnLightMuted))
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
        Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.DeepBrown600),
            modifier = Modifier.padding(bottom = 8.dp))
        Surface(color = FianoSettingsMutedBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun settingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FianoSettingsBg,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = FianoSettingsBg,
    focusedLabelColor = FianoSettingsBg,
    unfocusedLabelColor = AppColors.DeepBrown500,
    focusedTextColor = FianoSettingsText,
    unfocusedTextColor = FianoSettingsText,
)
