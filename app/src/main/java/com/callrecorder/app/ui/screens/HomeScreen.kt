package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.R
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.data.model.CallStatus
import com.callrecorder.app.data.model.CustomerListItem
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import com.callrecorder.app.onboarding.FeatureTourController
import com.callrecorder.app.onboarding.TourKeys
import com.callrecorder.app.onboarding.tourTarget
import com.callrecorder.app.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* 색상 (FIANO 0705 디자인 시스템) */
private val ScreenBg    = AppColors.DeepBrown900
private val ContentBg   = Color(0xFFFFFFFF)
private val SectionAltBg = Color(0xFFEEEEEE)
private val Navy        = AppColors.DeepBrown900
private val OnDark      = Color(0xFFFFFFFF)
private val OnDarkSub   = Color(0xFFFFFFFF)
private val TimeBlue    = AppColors.SignalRed500
private val SchedTimeHi = Color(0xFFF11800)
private val SchedTimeSm = Color(0xFF6B7078)
private val SchedMeta   = Color(0xFF454C55)
private val Connector   = AppColors.FianoBlack200
private val AvatarBg    = AppColors.DeepBrown50
private val AvatarText  = AppColors.DeepBrown700
private val AccentBlue  = AppColors.SignalRed500
private val UploadBlue  = AppColors.DeepBrown900
private val UploadBlueBg = AppColors.DeepBrown100

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCallClick: (String) -> Unit,
    onSettings: () -> Unit,
    onApprovalClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onSeeAllCalls: () -> Unit = {},
    onSeeAllSchedules: () -> Unit = {},
    onSeeAllCustomers: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
    tourController: FeatureTourController,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var showUploadSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.syncSettingsFromPrefs()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        // ── 히어로 (그레이) ──
        Hero(
            pendingCount = state.pendingApprovalCount,
            autoSummaryOn = state.autoSummaryEnabled,
            importantFilterOn = state.importantFilterEnabled,
            uploadingCount = state.uploadingCount,
            onAutoSummaryChange = { vm.setAutoSummary(it) },
            onImportantFilterChange = { vm.setImportantFilter(it) },
            onApprovalClick = onApprovalClick,
            onUploadClick = onUploadClick,
            onRefresh = { vm.refresh() },
            onUploadingClick = { showUploadSheet = true },
            onNotificationClick = onNotificationClick,
            hasNotification = hasNotification,
            tourController = tourController,
        )

        // ── 흰색 컨텐츠 (그레이 위로 라운드) ──
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(ContentBg)
                .verticalScroll(rememberScrollState()),
        ) {
            // 최근 분석 통화
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .tourTarget(tourController, TourKeys.RECENT_CALLS),
            ) {
                SectionHeader("최근 분석 통화", onSeeAll = onSeeAllCalls)
                when {
                    state.loading && state.recentCalls.isEmpty() ->
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentBlue)
                        }
                    state.recentCalls.isEmpty() -> EmptyBox("아직 분석된 통화가 없어요")
                    else -> state.recentCalls.distinctBy { it.id }.take(3).forEach { call ->
                        CallRow(call = call, onClick = { onCallClick(call.id) })
                    }
                }
            }

            // ── 주요 관리 고객 (#EEE 밴드) ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SectionAltBg)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                SectionHeader("주요 관리 고객", onSeeAll = onSeeAllCustomers)
                val pinnedCustomers = state.pinnedCustomers
                val customers = state.recentCalls.distinctBy { customerName(it) }.take(3)
                if (pinnedCustomers.isEmpty() && customers.isEmpty()) {
                    EmptyBox("관리 중인 고객이 없어요")
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (pinnedCustomers.isNotEmpty()) {
                            pinnedCustomers.forEach { c ->
                                PinnedCustomerCard(c, Modifier.weight(1f), onClick = onSeeAllCustomers)
                            }
                            repeat(3 - pinnedCustomers.size) { Spacer(Modifier.weight(1f)) }
                        } else {
                            customers.forEach { call ->
                                CustomerCard(call, Modifier.weight(1f)) { onCallClick(call.id) }
                            }
                            repeat(3 - customers.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            // ── 다가오는 일정 (#EEE 밴드 위 흰색 라운드) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(SectionAltBg),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(ContentBg)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    SectionHeader("다가오는 일정", onSeeAll = onSeeAllSchedules, emphasis = true)
                    if (state.schedules.isEmpty()) {
                        EmptyBox("예정된 일정이 없어요")
                    } else {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            state.schedules.forEachIndexed { idx, ev ->
                                ScheduleTimelineItem(ev, isFirst = idx == 0, isLast = idx == state.schedules.lastIndex)
                            }
                        }
                    }
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
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

@Composable
private fun Hero(
    pendingCount: Int,
    autoSummaryOn: Boolean,
    importantFilterOn: Boolean,
    uploadingCount: Int,
    onAutoSummaryChange: (Boolean) -> Unit,
    onImportantFilterChange: (Boolean) -> Unit,
    onApprovalClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRefresh: () -> Unit,
    onUploadingClick: () -> Unit,
    onNotificationClick: () -> Unit,
    hasNotification: Boolean,
    tourController: FeatureTourController,
) {
    val today = remember { todayFullDateLabel() }
    Column(Modifier.fillMaxWidth()) {
        FianoTopHeader(
            onNotificationClick = onNotificationClick,
            hasNotification = hasNotification,
        )

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)) {
            Text(today, style = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = OnDark))
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onApprovalClick,
                    color = Color.White,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Row(
                        Modifier.padding(start = 24.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.home_icon_refresh),
                            contentDescription = "새로고침",
                            modifier = Modifier
                                .width(24.dp)
                                .height(26.dp)
                                .clickable { onRefresh() },
                        )
                        Text("통화 분석 대기 ${pendingCount}건",
                            style = TextStyle(fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Navy))
                    }
                }
                Surface(
                    onClick = onUploadClick,
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp).tourTarget(tourController, TourKeys.UPLOAD),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.home_icon_add_calendar),
                            contentDescription = "파일 업로드",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            if (uploadingCount > 0) {
                Spacer(Modifier.height(10.dp))
                Surface(onClick = onUploadingClick, color = UploadBlueBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = UploadBlue)
                        Spacer(Modifier.width(10.dp))
                        Text("녹음 ${uploadingCount}건 업로드·분석 중",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = UploadBlue),
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, null, tint = UploadBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleButton(autoSummaryOn, "통화 자동 요약",
                    { onAutoSummaryChange(!autoSummaryOn) },
                    Modifier.weight(1f))
                ToggleButton(importantFilterOn, "통화 자동 필터링",
                    { onImportantFilterChange(!importantFilterOn) },
                    Modifier.weight(1f).tourTarget(tourController, TourKeys.IMPORTANT_FILTER))
            }
        }
    }
}

@Composable
private fun ToggleButton(on: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { onToggle() }
            .padding(horizontal = 0.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = if (on) R.drawable.home_icon_action_filter_on else R.drawable.home_icon_action_summary_off),
            contentDescription = if (on) "켜짐" else "꺼짐",
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$label ${if (on) "ON" else "OFF"}",
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = OnDark),
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit, emphasis: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.home_icon_section_open),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = TextStyle(fontSize = 16.sp, fontWeight = if (emphasis) FontWeight.ExtraBold else FontWeight.Bold, color = Navy))
        }
        Surface(onClick = onSeeAll, color = Color.Transparent) {
            Text("전체보기 →", modifier = Modifier.padding(4.dp), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallRow(call: Call, onClick: () -> Unit) {
    val name = customerName(call)
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.home_icon_call),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy), maxLines = 1)
                // 이름이 진짜 이름일 때만 번호 한 줄 더
                if (hasRealName(call)) {
                    phoneOf(call)?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = TextStyle(fontSize = 11.sp, color = SchedMeta), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(callSubtitle(call), style = TextStyle(fontSize = 13.sp, color = Navy), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(callTimeLabel(call.createdAt), style = TextStyle(fontSize = 14.sp, color = TimeBlue))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerCard(call: Call, modifier: Modifier, onClick: () -> Unit) {
    val name = customerName(call)
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(name, 34.dp)
            Column {
                Text(name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy), maxLines = 1)
                if (hasRealName(call)) {
                    phoneOf(call)?.let {
                        Text(it, style = TextStyle(fontSize = 10.sp, color = SchedMeta), maxLines = 1)
                    }
                }
                Text(callSubtitle(call), style = TextStyle(fontSize = 11.sp, color = Navy), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val displayName = name.ifBlank { "발신번호 없음" }
    val isPhone = displayName.firstOrNull()?.isDigit() == true || displayName == "발신번호 없음"
    Box(Modifier.size(size).clip(CircleShape).background(AvatarBg), contentAlignment = Alignment.Center) {
        if (isPhone) {
            Icon(Icons.Filled.Phone, null, tint = AvatarText, modifier = Modifier.size(size * 0.45f))
        } else {
            Text(
                displayName.first().toString(),
                style = TextStyle(
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold,
                    color = AvatarText,
                ),
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinnedCustomerCard(customer: CustomerListItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val name = customer.name?.takeIf { it.isNotBlank() } ?: customer.phone
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(8.dp), modifier = modifier.height(112.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(AvatarBg), contentAlignment = Alignment.Center) {
                Text(name.take(1), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AvatarText))
            }
            Text(name, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy), maxLines = 1)
            Text(customer.phone, style = TextStyle(fontSize = 10.sp, color = SchedMeta), maxLines = 1)
            Text("★ 주요관리", style = TextStyle(fontSize = 10.sp, color = AccentBlue), maxLines = 1)
        }
    }
}


@Composable
private fun ScheduleTimelineItem(event: CalendarEvent, isFirst: Boolean, isLast: Boolean) {
    val itemHeight = if (isFirst) 86.dp else 66.dp
    val timeFontSize = if (isFirst) 14.sp else 12.sp
    val titleFontSize = if (isFirst) 14.sp else 12.sp
    val detailFontSize = if (isFirst) 12.sp else 10.sp
    Row(
        Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .padding(horizontal = 8.dp),
    ) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isFirst) {
                Image(
                    painter = painterResource(id = R.drawable.call_icon_timeline_marker),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .border(1.dp, Connector, CircleShape)
                            .clip(CircleShape),
                    )
                }
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(Connector))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(
                    start = 0.dp,
                    top = if (isFirst) 2.dp else 0.dp,
                    bottom = if (isFirst) 24.dp else 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                listOfNotNull(scheduleDateLabel(event), event.time.takeIf { it.isNotBlank() }).joinToString("  "),
                style = TextStyle(
                    fontSize = timeFontSize,
                    lineHeight = timeFontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isFirst) SchedTimeHi else SchedTimeSm,
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    event.title,
                    style = TextStyle(
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = Navy,
                        lineHeight = if (isFirst) 18.sp else 16.sp,
                    ),
                )
                Text(
                    event.description.takeIf { it.isNotBlank() } ?: "통화 자동 등록",
                    style = TextStyle(
                        fontSize = detailFontSize,
                        lineHeight = if (isFirst) 16.sp else 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = SchedMeta,
                    ),
                )
            }
        }
    }
}

private fun scheduleDateLabel(event: CalendarEvent): String? {
    val raw = event.startAt?.takeIf { it.isNotBlank() } ?: return null
    val datePart = raw.substringBefore("T").substringBefore(" ")
    val parts = datePart.split("-")
    if (parts.size < 3) return datePart.takeIf { it.isNotBlank() }
    val month = parts[1].toIntOrNull() ?: return datePart
    val day = parts[2].toIntOrNull() ?: return datePart
    return "${parts[0]}년 ${month}월 ${day}일"
}

@Composable
private fun EmptyBox(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = SchedMeta))
    }
}

/* 데이터 헬퍼 */
private fun customerName(call: Call): String =
    call.extractedInfoOrNull()?.customerName?.takeIf { it.isNotBlank() }
        ?: call.callerName?.takeIf { it.isNotBlank() }
        ?: call.callerNumber?.takeIf { it.isNotBlank() }
        ?: "발신번호 없음"

/** 표시 이름이 전화번호가 아니라 진짜 이름인지 */
private fun hasRealName(call: Call): Boolean {
    val n = customerName(call)
    return n != call.callerNumber && n != "발신번호 없음"
}

private fun phoneOf(call: Call): String? = call.callerNumber?.takeIf { it.isNotBlank() }

private fun callSubtitle(call: Call): String {
    call.summary?.takeIf { it.isNotBlank() }?.let { return it }
    val info = call.extractedInfoOrNull()
    val brief = info?.let { i -> listOfNotNull(i.time?.takeIf { it.isNotBlank() }, i.partySize?.let { "${it}명" }).joinToString(" · ") }.orEmpty()
    if (brief.isNotBlank()) return brief
    val kw = call.internalKeywordsMap().entries.take(2).joinToString(" · ") { it.value }
    if (kw.isNotBlank()) return kw
    return when {
        call.status.equals(CallStatus.PROCESSING, true) -> "분석 중..."
        call.status.equals(CallStatus.UPLOADED, true) -> "서버 분석 대기"
        else -> "-"
    }
}

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
    return if (diffMs < 86_400_000L) SimpleDateFormat("HH:mm", Locale.KOREAN).format(date)
    else SimpleDateFormat("M/d", Locale.KOREAN).format(date)
}

private fun parseServerTime(s: String): Date? {
    for (fmt in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")) {
        try { return SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s) ?: continue } catch (_: Exception) {}
    }
    return null
}

/* 업로드 시트 (기존 유지) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSheet(items: List<com.callrecorder.app.ui.screens.UploadItem>, onDismiss: () -> Unit, onDelete: (Long) -> Unit, onDeleteAll: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("업로드 진행 중", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1C23)))
                    Spacer(Modifier.height(4.dp))
                    Text("${items.size}건 처리 중", style = TextStyle(fontSize = 13.sp, color = Color(0xFF8A8B94)))
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onDeleteAll) {
                        Text("모두 삭제", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444)))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                Text("진행 중인 업로드가 없습니다.", style = TextStyle(fontSize = 14.sp, color = Color(0xFF8A8B94)))
            } else {
                items.forEach { u -> UploadSheetItem(u, onDelete); Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun UploadSheetItem(u: com.callrecorder.app.ui.screens.UploadItem, onDelete: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (u.phase != "대기중") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = UploadBlue)
        else Icon(Icons.Filled.UploadFile, null, tint = Color(0xFFB0B5C0), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(u.name, style = TextStyle(fontSize = 14.sp, color = Color(0xFF1B1C23)), modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(u.phase, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (u.phase != "대기중") UploadBlue else Color(0xFF8A8B94)))
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { onDelete(u.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, "삭제", tint = Color(0xFFC44545), modifier = Modifier.size(18.dp))
        }
    }
}
