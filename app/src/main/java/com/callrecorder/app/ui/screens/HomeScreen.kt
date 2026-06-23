package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CallCategoryLabel
import com.callrecorder.app.data.model.CallStatus
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.data.model.DtoJson
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import com.callrecorder.app.onboarding.FeatureTourController
import com.callrecorder.app.onboarding.TourKeys
import com.callrecorder.app.onboarding.tourTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* ─────────────────────────────────────────────
 * 색상 팔레트
 * ───────────────────────────────────────────── */
private val DarkNavy        = Color(0xFF3D4D6B)
private val DarkNavyBtn     = Color(0xFFFFFFFF)
private val DarkNavyBtnIcon = Color(0xFF3D4D6B)

private val LightBg         = Color(0xFFF0F2F5)
private val UploadBlue      = Color(0xFF005ABE)
private val UploadBlueBg    = Color(0xFFEAF2FF)
private val UploadBlueTrack = Color(0xFFCFE0FF)
private val WhiteCard       = Color(0xFFFFFFFF)
private val AccentBlue      = Color(0xFF3B7DD8)

private val OnDarkPrimary   = Color(0xFFFFFFFF)
private val OnDarkSub       = Color(0xFFC5D0E0)
private val OnLightPrimary  = Color(0xFF1F2A3D)
private val OnLightSub      = Color(0xFF6B7889)
private val OnLightMuted    = Color(0xFF9AA5B5)

private val BadgeResBg = Color(0xFFE3EEFB); private val BadgeResFg = Color(0xFF2563B5)
private val BadgeCnlBg = Color(0xFFFBE3E3); private val BadgeCnlFg = Color(0xFFC23B3B)
private val BadgeCmpBg = Color(0xFFFBF0E0); private val BadgeCmpFg = Color(0xFFC07818)
private val BadgeInqBg = Color(0xFFEBE9FB); private val BadgeInqFg = Color(0xFF5B4FC2)
private val BadgeNeuBg = Color(0xFFE8EBF0); private val BadgeNeuFg = Color(0xFF6B7889)
private val BadgeInBg  = Color(0xFF3B7DD8); private val BadgeInFg  = Color(0xFFFFFFFF)
private val BadgeOutBg = Color(0xFFE8EBF0); private val BadgeOutFg = Color(0xFF6B7889)
private val NewBadgeBg = Color(0xFFE53E3E)

private val AvatarColors = listOf(
    Color(0xFFD4A03C) to Color(0xFFFFFFFF),
    Color(0xFF5B7FB5) to Color(0xFFFFFFFF),
    Color(0xFF5BA876) to Color(0xFFFFFFFF),
    Color(0xFFB56B8A) to Color(0xFFFFFFFF),
    Color(0xFF7B6BC2) to Color(0xFFFFFFFF),
)

/* ─────────────────────────────────────────────
 * HomeScreen
 * ───────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCallClick: (String) -> Unit,
    onSettings: () -> Unit,
    onApprovalClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onSeeAllCalls: () -> Unit = {},
    onSeeAllSchedules: () -> Unit = {},
    tourController: FeatureTourController,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var showUploadSheet by remember { mutableStateOf(false) }

    Scaffold(containerColor = LightBg) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding()
                ),
            contentPadding = PaddingValues(0.dp),
        ) {
            // 상단 다크 헤더
            item {
                DarkHeader(
                    pendingCount = state.pendingApprovalCount,
                    autoSummaryOn = state.autoSummaryEnabled,
                    importantFilterOn = state.importantFilterEnabled,
                    onAutoSummaryChange = { vm.setAutoSummary(it) },
                    onImportantFilterChange = { vm.setImportantFilter(it) },
                    onApprovalClick = onApprovalClick,
                    onUploadClick = onUploadClick,
                    onRefresh = { vm.refresh() },
                    tourController = tourController,
                )
            }

            // 업로드 상태 칩 (진행 중일 때만, 탭하면 목록 시트)
            if (state.uploadingCount > 0) {
                item {
                    UploadStatusRow(
                        count = state.uploadingCount,
                        onClick = { showUploadSheet = true },
                    )
                }
            }

            // 주요 통화 분석 헤더
            item {
                Spacer(Modifier.height(20.dp))
                Box(Modifier.tourTarget(tourController, TourKeys.RECENT_CALLS)) {
                    SectionHeader(title = "주요 통화 분석", onSeeAll = onSeeAllCalls)
                }
            }

            // 통화 카드 (최대 3건)
            if (state.loading && state.recentCalls.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
            } else if (state.recentCalls.isEmpty()) {
                item { EmptyRecentCalls() }
            } else {
                val uniqueCalls = state.recentCalls.distinctBy { it.id }.take(3)
                items(uniqueCalls, key = { it.id }) { call ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        CallCard(call = call, onClick = { onCallClick(call.id) })
                    }
                }
            }

            // 주요 일정 헤더
            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(title = "주요 일정", onSeeAll = onSeeAllSchedules)
            }

            // 일정 카드
            if (state.schedules.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        EmptySchedules()
                    }
                }
            } else {
                items(state.schedules, key = { it.id }) { schedule ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        ScheduleCard(schedule)
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }

        if (showUploadSheet) {
            UploadSheet(
                items = state.activeUploads,
                onDismiss = { showUploadSheet = false },
                onDelete = { id -> vm.deleteUpload(id) },
                onDeleteAll = { vm.deleteAllUploads() },
            )
        }
    }
}

/* ─────────────────────────────────────────────
 * 상단 다크 헤더
 * ───────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkHeader(
    pendingCount: Int,
    autoSummaryOn: Boolean,
    importantFilterOn: Boolean,
    onAutoSummaryChange: (Boolean) -> Unit,
    onImportantFilterChange: (Boolean) -> Unit,
    onApprovalClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRefresh: () -> Unit,
    tourController: FeatureTourController,
) {
    val today = remember { todayFullDateLabel() }

    Surface(color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 18.dp)) {

            // 앱바
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.PhoneAndroid, null,
                        tint = OnDarkPrimary, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI 통화 비서",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnDarkPrimary)
                    )
                }
                androidx.compose.material3.Icon(
                    Icons.Filled.NotificationsNone, "알림",
                    tint = OnDarkPrimary, modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 날짜
            Text(today, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary))

            Spacer(Modifier.height(14.dp))

            // 분석 대기 버튼 + 업로드 버튼
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onApprovalClick,
                    color = DarkNavyBtn,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 새로고침 아이콘 — 별도 클릭 영역
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(28.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Refresh, "새로고침",
                                tint = OnLightPrimary, modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "통화 분석 대기 ${pendingCount}건",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary),
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // 업로드 버튼
                Surface(
                    onClick = onUploadClick,
                    color = DarkNavyBtnIcon,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OnDarkSub.copy(alpha = 0.3f)),
                    modifier = Modifier.tourTarget(tourController, TourKeys.UPLOAD),
                ) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.UploadFile, "파일 업로드",
                            tint = OnDarkPrimary, modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 토글 행
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToggleItem(
                    checked = autoSummaryOn,
                    label = "통화 자동 분석 ${if (autoSummaryOn) "ON" else "OFF"}",
                    onChange = onAutoSummaryChange,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ToggleItem(
                    checked = importantFilterOn,
                    label = "중요 통화 필터링 ${if (importantFilterOn) "ON" else "OFF"}",
                    onChange = onImportantFilterChange,
                    modifier = Modifier
                        .weight(1f)
                        .tourTarget(tourController, TourKeys.IMPORTANT_FILTER),
                )
            }
        }
    }
}

@Composable
private fun ToggleItem(checked: Boolean, label: String, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = AccentBlue,
                uncheckedColor = OnDarkSub,
                checkmarkColor = Color.White,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = TextStyle(
                fontSize = 12.sp,
                color = OnDarkPrimary,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

/* ─────────────────────────────────────────────
 * 섹션 헤더
 * ───────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
        Surface(onClick = onSeeAll, color = Color.Transparent, shape = RoundedCornerShape(20.dp)) {
            Text(
                "전체보기 →",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                style = TextStyle(fontSize = 12.sp, color = OnLightSub),
            )
        }
    }
}

/* ─────────────────────────────────────────────
 * 통화 카드
 * ───────────────────────────────────────────── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallCard(call: Call, onClick: () -> Unit) {
    val info = call.extractedInfoOrNull()
    val displayName = info?.customerName?.takeIf { it.isNotBlank() }
        ?: call.callerNumber
        ?: "발신번호 없음"

    val initial = displayName.firstOrNull()?.takeIf { !it.isDigit() }?.toString() ?: "📞"
    val isPhone = initial == "📞"
    val avatarColorPair = AvatarColors[(displayName.hashCode().absoluteValue) % AvatarColors.size]

    val isOutgoing = false
    val dirLabel = if (isOutgoing) "발신" else "수신"
    val dirBg = if (isOutgoing) BadgeOutBg else BadgeInBg
    val dirFg = if (isOutgoing) BadgeOutFg else BadgeInFg

    val callTime = callTimeLabel(call.createdAt)

    val internalKw: Map<String, String> = call.internalKeywordsMap()

    val briefDesc = info?.let { i ->
        listOfNotNull(
            i.customerName?.takeIf { it.isNotBlank() },
            i.time?.takeIf { it.isNotBlank() },
            i.partySize?.let { "${it}명" },
        ).joinToString(" · ")
    } ?: internalKw.entries.take(2).joinToString(" · ") { (_, v) -> v }

    Surface(
        onClick = onClick,
        color = WhiteCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (isPhone) BadgeNeuBg else avatarColorPair.first),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial,
                    style = TextStyle(
                        fontSize = if (isPhone) 16.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPhone) OnLightSub else avatarColorPair.second,
                    ),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                    if (call.isRead == 0) {
                        Spacer(Modifier.width(6.dp))
                        NewBadge()
                    }
                }
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CategoryBadge(call.category)
                    DirBadge(dirLabel, dirBg, dirFg)
                }
                val summary = call.summary
                val descText = when {
                    !summary.isNullOrBlank() -> summary
                    briefDesc.isNotBlank() -> briefDesc
                    call.status.equals(CallStatus.PROCESSING, true) ||
                            call.status.equals(CallStatus.UPLOADED, true) -> "분석 중…"
                    else -> null
                }
                if (descText != null) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        descText,
                        style = TextStyle(fontSize = 12.sp, color = OnLightSub, lineHeight = 17.sp),
                        maxLines = 1
                    )
                }
            }

            Text(callTime, style = TextStyle(fontSize = 12.sp, color = OnLightMuted), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/* ─────────────────────────────────────────────
 * 일정 카드
 * ───────────────────────────────────────────── */
private fun providerDotColor(provider: String) = when (provider) {
    "kakao"  -> Color(0xFFFBC02D)
    "naver"  -> Color(0xFF03C75A)
    "google" -> Color(0xFF4285F4)
    else     -> AccentBlue
}

@Composable
private fun ScheduleCard(schedule: CalendarEvent) {
    Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(providerDotColor(schedule.provider)))
            Spacer(Modifier.width(12.dp))
            Text(
                schedule.time,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentBlue),
                modifier = Modifier.width(44.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(schedule.title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OnLightPrimary))
                if (schedule.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(schedule.description, style = TextStyle(fontSize = 12.sp, color = OnLightSub))
                }
            }
        }
    }
}

/* ─────────────────────────────────────────────
 * 배지
 * ───────────────────────────────────────────── */
@Composable
private fun CategoryBadge(category: String?) {
    val (label, bg, fg) = when (category) {
        CallCategoryLabel.RESERVATION -> Triple("예약", BadgeResBg, BadgeResFg)
        CallCategoryLabel.CANCEL      -> Triple("취소", BadgeCnlBg, BadgeCnlFg)
        CallCategoryLabel.COMPLAINT   -> Triple("불만", BadgeCmpBg, BadgeCmpFg)
        CallCategoryLabel.INQUIRY     -> Triple("문의", BadgeInqBg, BadgeInqFg)
        else                          -> Triple("기타", BadgeNeuBg, BadgeNeuFg)
    }
    Surface(color = bg, shape = RoundedCornerShape(5.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
        )
    }
}

@Composable
private fun DirBadge(label: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(5.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg)
        )
    }
}

@Composable
private fun NewBadge() {
    Surface(color = NewBadgeBg, shape = RoundedCornerShape(5.dp)) {
        Text(
            "NEW",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        )
    }
}

/* ─────────────────────────────────────────────
 * 빈 상태
 * ───────────────────────────────────────────── */
@Composable
private fun EmptySchedules() {
    Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Text("오늘 등록된 일정이 없어요", style = TextStyle(fontSize = 13.sp, color = OnLightMuted))
        }
    }
}

@Composable
private fun EmptyRecentCalls() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📞", style = TextStyle(fontSize = 36.sp))
        Spacer(Modifier.height(12.dp))
        Text("아직 분석된 통화가 없어요", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OnLightPrimary))
        Spacer(Modifier.height(4.dp))
        Text("통화 후 잠시만 기다려 주세요.", style = TextStyle(fontSize = 12.sp, color = OnLightSub))
    }
}

/* ─────────────────────────────────────────────
 * 유틸
 * ───────────────────────────────────────────── */
private val Weekdays = arrayOf("일", "월", "화", "수", "목", "금", "토")

private fun todayFullDateLabel(): String {
    val cal = java.util.Calendar.getInstance()
    val dow = Weekdays[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    return SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN).format(Date()) + " ${dow}요일"
}

private fun callTimeLabel(serverTime: String?): String {
    if (serverTime.isNullOrBlank()) return ""
    val date = parseServerTime(serverTime) ?: return ""
    val diffMs = System.currentTimeMillis() - date.time
    return if (diffMs < 86_400_000L)
        SimpleDateFormat("HH:mm", Locale.KOREAN).format(date)
    else
        SimpleDateFormat("M/d", Locale.KOREAN).format(date)
}

private fun parseServerTime(s: String): Date? {
    val fmts = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    )
    for (fmt in fmts) {
        try {
            return SimpleDateFormat(fmt, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(s) ?: continue
        } catch (_: Exception) {}
    }
    return null
}

private val Int.absoluteValue get() = if (this < 0) -this else this

@Composable
private fun UploadStatusRow(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = UploadBlueBg,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = UploadBlue,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "녹음 ${count}건 업로드·분석 중",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = UploadBlue),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "보기",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = UploadBlue),
            )
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = UploadBlue,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSheet(
    items: List<com.callrecorder.app.ui.screens.UploadItem>,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "업로드 진행 중",
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1C23)),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${items.size}건 처리 중",
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF8A8B94)),
                    )
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onDeleteAll) {
                        Text(
                            "모두 삭제",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                Text(
                    "진행 중인 업로드가 없습니다.",
                    style = TextStyle(fontSize = 14.sp, color = Color(0xFF8A8B94)),
                )
            } else {
                items.forEach { u ->
                    UploadSheetItem(u, onDelete = onDelete)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun UploadSheetItem(
    u: com.callrecorder.app.ui.screens.UploadItem,
    onDelete: (Long) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (u.phase != "대기중") {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = UploadBlue,
            )
        } else {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.UploadFile,
                contentDescription = null,
                tint = Color(0xFFB0B5C0),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = u.name,
            style = TextStyle(fontSize = 14.sp, color = Color(0xFF1B1C23)),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = u.phase,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (u.phase != "대기중") UploadBlue else Color(0xFF8A8B94),
            ),
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { onDelete(u.id) }, modifier = Modifier.size(32.dp)) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "삭제",
                tint = Color(0xFFC44545),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}