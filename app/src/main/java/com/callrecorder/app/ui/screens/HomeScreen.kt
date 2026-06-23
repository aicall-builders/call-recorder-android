package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.data.model.CallStatus
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import com.callrecorder.app.onboarding.FeatureTourController
import com.callrecorder.app.onboarding.TourKeys
import com.callrecorder.app.onboarding.tourTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* 색상 (피그마 2026-06-23 파운데이션) */
private val ScreenBg    = Color(0xFF5F6071)
private val ContentBg   = Color(0xFFFFFFFF)
private val SectionAltBg = Color(0xFFEEEEEE)
private val Navy        = Color(0xFF343659)
private val OnDark      = Color(0xFFFFFFFF)
private val OnDarkSub   = Color(0xFFB9BECB)
private val TimeBlue    = Color(0xFF2867E5)
private val SchedTimeHi = Color(0xFF1C6BD4)
private val SchedTimeSm = Color(0xFF767676)
private val SchedMeta   = Color(0xFF757575)
private val Connector   = Color(0xFFD6D9E5)
private val AvatarBg    = Color(0xFFF1F1F1)
private val AvatarText  = Color(0xFF5F5F5F)
private val AccentBlue  = Color(0xFF3B7DD8)
private val UploadBlue  = Color(0xFF005ABE)
private val UploadBlueBg = Color(0xFFEAF2FF)

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
    tourController: FeatureTourController,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var showUploadSheet by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState()),
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
            tourController = tourController,
        )

        // ── 흰색 컨텐츠 (그레이 위로 라운드) ──
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(ContentBg),
        ) {
            // 주요 분석 통화
            Column(Modifier.tourTarget(tourController, TourKeys.RECENT_CALLS)) {
                SectionHeader("주요 분석 통화", onSeeAll = onSeeAllCalls)
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
            Spacer(Modifier.height(8.dp))

            // ── 주요 관리 고객 (#EEE, 흰색 위로 라운드) ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SectionAltBg),
            ) {
                SectionHeader("주요 관리 고객", onSeeAll = onSeeAllCustomers)
                val customers = state.recentCalls.distinctBy { customerName(it) }.take(3)
                if (customers.isEmpty()) {
                    EmptyBox("관리 중인 고객이 없어요")
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        customers.forEach { call ->
                            CustomerCard(call, Modifier.weight(1f)) { onCallClick(call.id) }
                        }
                        repeat(3 - customers.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // ── 다가오는 일정 (흰색, #EEE 위로 라운드) ──
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(ContentBg),
                ) {
                    SectionHeader("다가오는 일정", onSeeAll = onSeeAllSchedules, emphasis = true)
                    if (state.schedules.isEmpty()) {
                        EmptyBox("예정된 일정이 없어요")
                    } else {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            state.schedules.forEachIndexed { idx, ev ->
                                ScheduleTimelineItem(ev, isFirst = idx == 0, isLast = idx == state.schedules.lastIndex)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
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
    tourController: FeatureTourController,
) {
    val today = remember { todayFullDateLabel() }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhoneAndroid, null, tint = OnDark, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("AI 통화 비서", style = TextStyle(fontSize = 18.sp, color = OnDark))
            }
            Icon(Icons.Filled.NotificationsNone, "알림", tint = OnDark, modifier = Modifier.size(20.dp))
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
            Text(today, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnDark))
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onApprovalClick,
                    color = Color.White,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.weight(1f).height(64.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Refresh, "새로고침", tint = Navy, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("통화 분석 대기 ${pendingCount}건",
                            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Navy))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    onClick = onUploadClick,
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp).tourTarget(tourController, TourKeys.UPLOAD),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, "파일 업로드", tint = Navy, modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (uploadingCount > 0) {
                Spacer(Modifier.height(10.dp))
                Surface(onClick = onUploadingClick, color = UploadBlueBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
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

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                ToggleButton(autoSummaryOn, "통화 자동 요약 ${if (autoSummaryOn) "ON" else "OFF"}",
                    { onAutoSummaryChange(!autoSummaryOn) }, Modifier.weight(1f))
                ToggleButton(importantFilterOn, "중요 통화 필터링 ${if (importantFilterOn) "ON" else "OFF"}",
                    { onImportantFilterChange(!importantFilterOn) },
                    Modifier.weight(1f).tourTarget(tourController, TourKeys.IMPORTANT_FILTER), important = true)
            }
        }
    }
}

@Composable
private fun ToggleButton(on: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier, important: Boolean = false) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(999.dp)).clickable { onToggle() }.padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            important && on -> Icons.Filled.Star
            important -> Icons.Filled.StarBorder
            on -> Icons.Filled.CheckCircle
            else -> Icons.Filled.RadioButtonUnchecked
        }
        Icon(icon, null, tint = if (on) OnDark else OnDarkSub, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) OnDark else OnDarkSub), maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit, emphasis: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Navy), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
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
            Avatar(name, 36.dp)
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
    val isPhone = name.firstOrNull()?.isDigit() == true || name == "발신번호 없음"
    Box(Modifier.size(size).clip(CircleShape).background(AvatarBg), contentAlignment = Alignment.Center) {
        if (isPhone) {
            Icon(Icons.Filled.Phone, null, tint = AvatarText, modifier = Modifier.size(size * 0.45f))
        } else {
            Text(name.first().toString(), style = TextStyle(fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Bold, color = AvatarText))
        }
    }
}

@Composable
private fun ScheduleTimelineItem(event: CalendarEvent, isFirst: Boolean, isLast: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(if (isFirst) 14.dp else 12.dp).clip(CircleShape).background(if (isFirst) SchedTimeHi else Connector))
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(Connector))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 4.dp else 20.dp)) {
            Text(event.time, style = TextStyle(fontSize = if (isFirst) 14.sp else 12.sp, fontWeight = FontWeight.Bold, color = if (isFirst) SchedTimeHi else SchedTimeSm))
            Spacer(Modifier.height(6.dp))
            Text(event.title, style = TextStyle(fontSize = if (isFirst) 14.sp else 12.sp, fontWeight = FontWeight.Bold, color = Navy, lineHeight = (if (isFirst) 18 else 16).sp))
            Spacer(Modifier.height(4.dp))
            Text(event.description.takeIf { it.isNotBlank() } ?: "통화 자동 등록", style = TextStyle(fontSize = 10.sp, color = SchedMeta))
        }
    }
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
        ?: call.callerNumber
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
    return if (call.status.equals(CallStatus.PROCESSING, true) || call.status.equals(CallStatus.UPLOADED, true)) "분석 중…" else "—"
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