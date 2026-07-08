package com.callrecorder.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.CallCategoryCode
import com.callrecorder.app.data.model.CallCategoryLabel
import com.callrecorder.app.data.model.CalendarEvent as ApiEvent
import com.callrecorder.app.ui.theme.AppColors
import coil.compose.AsyncImage
import java.util.Calendar

// ─────────────────────────────────────────────────────
// 다크 2-tone 색상
// ─────────────────────────────────────────────────────
private val DarkNavy = AppColors.DeepBrown900
private val LightBg = AppColors.DeepBrown900
private val WhiteCard = Color(0xFFFFFFFF)
private val AccentBlue = AppColors.SignalRed600
private val OnDarkPrimary = Color(0xFFFFFFFF)
private val OnLightPrimary = AppColors.DeepBrown900
private val OnLightSub = AppColors.DeepBrown500
private val DividerLight = AppColors.DeepBrown200

// ─────────────────────────────────────────────────────
// 화면 전용 모델 (API 모델과 별개 — 캘린더 그리드/카드 표시용)
// ─────────────────────────────────────────────────────

private enum class EventType { AUTO, MANUAL }

private enum class ScheduleChip(val label: String) {
    RESERVATION("예약"),
    INQUIRY("문의"),
    DAILY("일상"),
    OTHER("기타"),
}

private data class CalEvent(
    val id: String,
    val title: String,
    val date: Int,           // 해당 월의 일(day)
    val time: String,        // "19:00"
    val type: EventType,
    val chip: ScheduleChip,
    val description: String = "",
    val callId: String? = null,
    val dateLabel: String = "",
    val hasAttachments: Boolean = false,
    val imageUris: List<Uri> = emptyList(),
)

/** API CalendarEvent → 화면용 CalEvent 변환 */
private fun ApiEvent.toCalEvent(): CalEvent? {
    val day = dayOfMonth ?: return null
    val displayTitle = callerName?.takeIf { it.isNotBlank() }
        ?: callerNumber?.takeIf { it.isNotBlank() }
        ?: title.takeIf { it.isNotBlank() }
        ?: "발신번호 없음"
    return CalEvent(
        id = id,
        title = displayTitle,
        date = day,
        time = time,
        type = EventType.AUTO,   // 캘린더 API에서 온 건 통화 자동등록
        chip = category.toScheduleChip(title, description),
        description = description.ifBlank { title },
        callId = callId,
        dateLabel = startAt.toEventDateLabel(day),
    )
}

// ─────────────────────────────────────────────────────
// 내부 캘린더 메인 화면 (다크 헤더 "일정관리", 탭 없음)
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalCalendarScreen(
    calVm: CalendarViewModel = viewModel(),
    onCallDetailClick: (String) -> Unit = {},
    onMemoImageClick: (callId: String, title: String) -> Unit = { _, _ -> },
) {
    val calState by calVm.state.collectAsState()

    val nowCal = remember { Calendar.getInstance() }
    var currentYear by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(nowCal.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableStateOf(nowCal.get(Calendar.DAY_OF_MONTH)) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingManualEvent by remember { mutableStateOf<CalEvent?>(null) }

    // 수동 추가 일정 (로컬 보관)
    val manualEvents = remember { mutableStateListOf<CalEvent>() }

    // API 일정 + 수동 일정 병합
    val apiEvents = remember(calState.events) {
        calState.events.mapNotNull { it.toCalEvent() }
    }
    val allEvents = remember(apiEvents, manualEvents.toList()) {
        apiEvents + manualEvents
    }

    Scaffold(containerColor = LightBg) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(DarkNavy)
                .padding(padding),
        ) {
            FianoTopHeader()
            CalendarHero()
            InternalCalendarTab(
                year = currentYear,
                month = currentMonth,
                selectedDay = selectedDay,
                events = allEvents,
                loading = calState.eventsLoading,
                onPrevMonth = {
                    if (currentMonth == 1) { currentMonth = 12; currentYear-- }
                    else currentMonth--
                    selectedDay = 1
                    calVm.loadMonthEvents(currentYear, currentMonth)
                },
                onNextMonth = {
                    if (currentMonth == 12) { currentMonth = 1; currentYear++ }
                    else currentMonth++
                    selectedDay = 1
                    calVm.loadMonthEvents(currentYear, currentMonth)
                },
                onDaySelect = { selectedDay = it },
                onAddClick = {
                    editingManualEvent = null
                    showAddDialog = true
                },
                onCallDetailClick = onCallDetailClick,
                onMemoImageClick = onMemoImageClick,
                onManualEditClick = { event ->
                    editingManualEvent = event
                    showAddDialog = true
                },
            )
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            defaultDay = selectedDay,
            initialEvent = editingManualEvent,
            onDismiss = {
                showAddDialog = false
                editingManualEvent = null
            },
            onConfirm = { title, time, day, description, chip, imageUris ->
                val savedEvent = CalEvent(
                    id = editingManualEvent?.id ?: System.currentTimeMillis().toString(),
                    title = title,
                    date = day,
                    time = time,
                    type = EventType.MANUAL,
                    chip = chip,
                    description = description,
                    dateLabel = "%04d.%02d.%02d".format(currentYear, currentMonth, day),
                    hasAttachments = imageUris.isNotEmpty(),
                    imageUris = imageUris,
                )
                val editingIndex = manualEvents.indexOfFirst { it.id == savedEvent.id }
                if (editingIndex >= 0) {
                    manualEvents[editingIndex] = savedEvent
                } else {
                    manualEvents.add(savedEvent)
                }
                selectedDay = day.coerceIn(1, daysInMonth(currentYear, currentMonth))
                showAddDialog = false
                editingManualEvent = null
            },
        )
    }
}

// ─────────────────────────────────────────────────────
// 캘린더 본문
// ─────────────────────────────────────────────────────

@Composable
private fun CalendarHero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Text(
            "일정을 확인해보세요.",
            style = TextStyle(
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = OnDarkPrimary,
            ),
        )
    }
}

@Composable
private fun InternalCalendarTab(
    year: Int,
    month: Int,
    selectedDay: Int,
    events: List<CalEvent>,
    loading: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelect: (Int) -> Unit,
    onAddClick: () -> Unit,
    onCallDetailClick: (String) -> Unit,
    onMemoImageClick: (callId: String, title: String) -> Unit,
    onManualEditClick: (CalEvent) -> Unit,
) {
    val daysInMonth = daysInMonth(year, month)
    val firstDayOfWeek = firstDayOfWeek(year, month)

    val selectedEvents = events
        .filter { it.date == selectedDay }
        .sortedWith(compareBy<CalEvent> { it.time.toSortMinutes() }.thenBy { it.title })
    val eventDays = events.groupBy { it.date }

    val today = remember { Calendar.getInstance() }
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(WhiteCard),
        contentPadding = PaddingValues(top = 24.dp, bottom = 92.dp),
    ) {
        // 월 네비게이션
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonthCircleButton(onClick = onPrevMonth) {
                    Icon(Icons.Filled.ChevronLeft, "이전 달", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${year}년 ${month}월", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary, lineHeight = 24.sp))
                    if (loading) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentBlue)
                    }
                }
                MonthCircleButton(onClick = onNextMonth) {
                    Icon(Icons.Filled.ChevronRight, "다음 달", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
        }

        // 캘린더 그리드
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { idx, day ->
                        Text(
                            day,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = when (idx) {
                                    0 -> AppColors.DeepBrown600
                                    6 -> AppColors.DeepBrown700
                                    else -> AppColors.DeepBrown500
                                },
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                val totalCells = firstDayOfWeek + daysInMonth
                val rows = (totalCells + 6) / 7
                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val day = cellIndex - firstDayOfWeek + 1
                            val isValid = day in 1..daysInMonth
                            val isToday = isValid && year == todayYear && month == todayMonth && day == todayDay
                            val isSelected = isValid && day == selectedDay
                            val dayEvents = if (isValid) eventDays[day].orEmpty() else emptyList()

                            CalendarDayCell(
                                day = day,
                                valid = isValid,
                                selected = isSelected,
                                today = isToday,
                                column = col,
                                events = dayEvents,
                                onClick = { if (isValid) onDaySelect(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        }

        // 범례 (통화 자동등록 · 수동 등록)
        item {
            Row(Modifier.padding(horizontal = 40.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(AccentBlue, "통화 자동등록")
                LegendItem(AppColors.DeepBrown900, "수동 등록")
            }
            Spacer(Modifier.height(18.dp))
        }

        // 선택된 날 일정 헤더
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${month}월 ${selectedDay}일 일정", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary, lineHeight = 20.sp))
                Text(
                    "+ 일정 추가",
                    modifier = Modifier.clickable(onClick = onAddClick).padding(8.dp),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary, lineHeight = 16.sp),
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (selectedEvents.isEmpty()) {
            item {
                EmptyScheduleTimeline(onAddClick = onAddClick)
            }
        } else {
            items(selectedEvents, key = { it.id }) { event ->
                TimelineEvent(
                    event = event,
                    showConnector = event != selectedEvents.last(),
                    onCallDetailClick = onCallDetailClick,
                    onMemoImageClick = onMemoImageClick,
                    onManualEditClick = onManualEditClick,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = AppColors.DeepBrown900, modifier = Modifier.size(20.dp)) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    valid: Boolean,
    selected: Boolean,
    today: Boolean,
    column: Int,
    events: List<CalEvent>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (valid) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> AppColors.DeepBrown900
                        today -> AppColors.DeepBrown100
                        else -> Color.Transparent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (valid) {
                Text(
                    day.toString(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = when {
                            selected -> Color.White
                            column == 0 -> AppColors.DeepBrown600
                            column == 6 -> AppColors.DeepBrown700
                            else -> OnLightPrimary
                        },
                        lineHeight = 16.sp,
                    ),
                )
            }
        }
        if (events.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                events.take(2).forEach { ev ->
                    Box(Modifier.size(4.dp).clip(CircleShape).background(eventDotColor(ev.type)))
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduleTimeline(onAddClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 24.dp),
    ) {
        TimelineRail(type = EventType.AUTO, showConnector = false, modifier = Modifier.fillMaxHeight())
        Column(Modifier.weight(1f).padding(start = 8.dp, bottom = 16.dp)) {
            Text("등록된 일정이 없습니다.", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
            Spacer(Modifier.height(6.dp))
            Text(
                "새 일정을 직접 추가할 수 있어요.",
                style = TextStyle(fontSize = 12.sp, color = OnLightSub, lineHeight = 16.sp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "+일정 추가하기",
                modifier = Modifier.clickable(onClick = onAddClick),
                style = TextStyle(fontSize = 12.sp, color = AppColors.SignalRed700, lineHeight = 16.sp),
            )
        }
    }
}

@Composable
private fun ImportanceChip(chip: ScheduleChip, modifier: Modifier = Modifier) {
    val (bg, fg) = when (chip) {
        ScheduleChip.RESERVATION -> AppColors.DeepBrown900 to Color.White
        ScheduleChip.INQUIRY -> AppColors.DeepBrown700 to Color.White
        ScheduleChip.DAILY -> AppColors.DeepBrown400 to Color.White
        ScheduleChip.OTHER -> AppColors.DeepBrown100 to AppColors.DeepBrown800
    }
    Surface(color = bg, shape = RoundedCornerShape(999.dp), modifier = modifier) {
        Text(
            chip.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                lineHeight = 14.sp,
            ),
        )
    }
}

@Composable
private fun TimelineRail(
    type: EventType,
    showConnector: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(24.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(eventDotColor(type)),
            )
        }
        if (showConnector) {
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(AppColors.DeepBrown200),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun TimelineEvent(
    event: CalEvent,
    showConnector: Boolean,
    onCallDetailClick: (String) -> Unit,
    onMemoImageClick: (callId: String, title: String) -> Unit,
    onManualEditClick: (CalEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        TimelineRail(type = event.type, showConnector = showConnector, modifier = Modifier.fillMaxHeight())
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ImportanceChip(event.chip)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        event.title,
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = OnLightPrimary,
                            lineHeight = 20.sp,
                        ),
                    )
                    val callId = event.callId?.takeIf { it.isNotBlank() }
                    if (event.type == EventType.AUTO && callId != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "통화상세",
                            modifier = Modifier
                                .height(20.dp)
                                .clickable { onCallDetailClick(callId) },
                            style = TextStyle(fontSize = 11.sp, color = AppColors.SignalRed700, lineHeight = 16.sp),
                        )
                    }
                }
                Text(
                    "${event.dateLabel.ifBlank { eventDateLabel(event.date) }} ${event.time}",
                    style = TextStyle(fontSize = 12.sp, color = OnLightSub, lineHeight = 16.sp),
                )
            }
            Text(
                event.description.ifBlank {
                    if (event.type == EventType.AUTO) "통화에서 추출된 일정입니다." else "직접 추가한 일정입니다."
                },
                style = TextStyle(fontSize = 12.sp, color = AppColors.DeepBrown700, lineHeight = 16.sp),
            )
            if (event.hasAttachments || event.imageUris.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (event.imageUris.isNotEmpty()) {
                        event.imageUris.take(2).forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFEFEFEF)),
                            )
                        }
                    } else {
                        repeat(2) {
                            Box(
                                Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFEFEFEF)),
                            )
                        }
                    }
                }
            }
            val footerLabel = if (event.type == EventType.MANUAL) "수정하기" else "메모 / 이미지 추가하기+"
            Text(
                footerLabel,
                modifier = Modifier
                    .padding(8.dp)
                    .then(
                        when (event.type) {
                            EventType.MANUAL -> Modifier.clickable { onManualEditClick(event) }
                            EventType.AUTO -> event.callId
                                ?.takeIf { it.isNotBlank() }
                                ?.let { callId -> Modifier.clickable { onMemoImageClick(callId, event.title) } }
                                ?: Modifier
                        },
                    ),
                style = TextStyle(fontSize = 12.sp, color = AppColors.SignalRed700, lineHeight = 16.sp),
            )
        }
    }
}

private fun eventDateLabel(day: Int): String = "2026.06.${day.toString().padStart(2, '0')}"

private fun String.toSortMinutes(): Int {
    val normalized = trim()
    val parts = normalized.split(":")
    val hour = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull()
    val minute = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    return if (hour != null && hour in 0..23 && minute in 0..59) {
        hour * 60 + minute
    } else {
        Int.MAX_VALUE
    }
}

private fun String?.toEventDateLabel(fallbackDay: Int): String {
    val datePart = this?.substringBefore("T")?.substringBefore(" ").orEmpty()
    val parts = datePart.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()
    val day = parts.getOrNull(2)?.toIntOrNull() ?: fallbackDay
    return if (year != null && month != null) {
        "%04d.%02d.%02d".format(year, month, day)
    } else {
        eventDateLabel(day)
    }
}

private fun String?.toScheduleChip(title: String = "", description: String = ""): ScheduleChip {
    val raw = listOfNotNull(this, title, description).joinToString(" ").lowercase()
    return when {
        raw.contains(CallCategoryLabel.RESERVATION) ||
            raw.contains(CallCategoryCode.RESERVATION) ||
            raw.contains("예약") ||
            raw.contains("방문") ||
            raw.contains("일정") -> ScheduleChip.RESERVATION
        raw.contains(CallCategoryLabel.INQUIRY) ||
            raw.contains(CallCategoryCode.INQUIRY) ||
            raw.contains("문의") -> ScheduleChip.INQUIRY
        raw.contains("일상") ||
            raw.contains("daily") -> ScheduleChip.DAILY
        else -> ScheduleChip.OTHER
    }
}

// ─────────────────────────────────────────────────────
// 외부 캘린더 연동 화면 (설정에서 호출 — 독립 화면)
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalCalendarScreen(
    onBack: () -> Unit,
    vm: CalendarViewModel = viewModel(),
) {
    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = { Text("외부 캘린더 연동", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ChevronLeft, "뒤로", tint = OnDarkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ExternalCalendarTab(vm = vm)
        }
    }
}

@Composable
private fun ExternalCalendarTab(vm: CalendarViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val redirectBase = "https://dk1k75g0ji3vw.cloudfront.net/oauth"

    // 브라우저에서 callrecorder://oauth/{provider} 딥링크로 돌아오면 앱 토큰으로 직접 완료
    val bridge = CallRecorderApp.instance.container.calendarOAuthBridge
    val pending by bridge.pending.collectAsState()
    LaunchedEffect(pending) {
        pending?.let { cb ->
            vm.completeOAuth(
                provider = cb.provider,
                code = cb.code,
                redirectUri = "$redirectBase/${cb.provider}",  // 인가 때와 동일해야 토큰 교환 성공
                state = cb.state,
            )
            bridge.consume()
        }
    }

    // 캘린더 연동은 구글만 지원 (로그인은 카카오/구글 그대로 — 별개 흐름)
    val providers = listOf(
        Triple("google", "Google 캘린더", Color(0xFF4285F4)),
    )

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("예약 일정을 자동으로 캘린더에 등록하세요.", style = TextStyle(fontSize = 13.sp, color = OnLightSub))
        Spacer(Modifier.height(20.dp))

        if (state.loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
        } else {
            val connectedProviders = state.connections.map { it.provider }.toSet()
            providers.forEach { (provider, label, color) ->
                val isConnected = provider in connectedProviders
                ExternalProviderCard(
                    label = label, color = color, isConnected = isConnected,
                    onConnect = {
                        // state 앞에 "app:" 마커 -> 웹 콜백이 로그인/웹연결이 아니라 "앱 릴레이"로 인식
                        val oauthState = "app:" + java.util.UUID.randomUUID().toString()
                        val redirectUri = "$redirectBase/$provider"
                        val url = when (provider) {
                            "google" -> {
                                val clientId = "141325097922-rnsj0gfhd44nm6evsc2ue4nsungg1f2p.apps.googleusercontent.com"
                                val params = listOf(
                                    "client_id" to clientId,
                                    "redirect_uri" to redirectUri,
                                    "response_type" to "code",
                                    "scope" to "https://www.googleapis.com/auth/calendar",
                                    "state" to oauthState,
                                    "access_type" to "offline",
                                    "prompt" to "consent"
                                ).joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
                                "https://accounts.google.com/o/oauth2/v2/auth?$params"
                            }
                            "kakao" -> {
                                val clientId = "a05635006ea378df6a0a4ba7de8aed61"
                                val params = listOf(
                                    "client_id" to clientId,
                                    "redirect_uri" to redirectUri,
                                    "response_type" to "code",
                                    "scope" to "talk_calendar",
                                    "state" to oauthState
                                ).joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
                                "https://kauth.kakao.com/oauth/authorize?$params"
                            }
                            "naver" -> {
                                val clientId = "ZkYLir0G86UhIB264uO0"
                                val params = listOf(
                                    "client_id" to clientId,
                                    "redirect_uri" to redirectUri,
                                    "response_type" to "code",
                                    "state" to oauthState
                                ).joinToString("&") { "${it.first}=${Uri.encode(it.second)}" }
                                "https://nid.naver.com/oauth2.0/authorize?$params"
                            }
                            else -> null
                        }
                        url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    },
                    onDisconnect = { vm.disconnect(provider) },
                )
                Spacer(Modifier.height(10.dp))
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text("오류: ${state.error}", style = TextStyle(fontSize = 12.sp, color = Color.Red))
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { vm.loadConnections() }) {
                Text("새로고침", color = AccentBlue)
            }
        }
    }
}

@Composable
private fun ExternalProviderCard(
    label: String, color: Color, isConnected: Boolean,
    onConnect: () -> Unit, onDisconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = WhiteCard,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CalendarMonth, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnLightPrimary))
                Text(if (isConnected) "연결됨" else "연결 안 됨",
                    style = TextStyle(fontSize = 12.sp, color = if (isConnected) Color(0xFF059669) else OnLightSub))
            }
            if (isConnected) {
                OutlinedButton(onClick = onDisconnect, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) {
                    Text("해제", fontSize = 12.sp)
                }
            } else {
                Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                    Text("연결", fontSize = 12.sp)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// 일정 추가 다이얼로그
// ─────────────────────────────────────────────────────

@Composable
private fun AddEventDialog(
    defaultDay: Int,
    initialEvent: CalEvent? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, time: String, day: Int, description: String, chip: ScheduleChip, imageUris: List<Uri>) -> Unit,
) {
    val initialKey = initialEvent?.id ?: "new-$defaultDay"
    var title by remember(initialKey) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var time by remember(initialKey) { mutableStateOf(initialEvent?.time.orEmpty()) }
    var dayText by remember(initialKey) { mutableStateOf((initialEvent?.date ?: defaultDay).toString()) }
    var description by remember(initialKey) { mutableStateOf(initialEvent?.description.orEmpty()) }
    var selectedChip by remember(initialKey) { mutableStateOf(initialEvent?.chip ?: ScheduleChip.RESERVATION) }
    var imageUris by remember(initialKey) { mutableStateOf(initialEvent?.imageUris.orEmpty()) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        imageUris = (imageUris + uris).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialEvent == null) "일정 추가" else "일정 수정",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("일정 제목", fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("시간 (예: 14:00)", fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dayText, onValueChange = { dayText = it }, label = { Text("날짜 (일)", fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("상세 내역", fontSize = 13.sp) },
                    placeholder = { Text("상세 내용을 적어주세요.", fontSize = 13.sp) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("이미지", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                OutlinedButton(onClick = { imagePickerLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageUris.isEmpty()) "이미지 추가" else "이미지 추가 (${imageUris.size})")
                }
                if (imageUris.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        imageUris.take(3).forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEFEFEF)),
                            )
                        }
                    }
                }
                Text("분류", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleChip.values().forEach { chip ->
                        ScheduleFilterChip(
                            chip = chip,
                            selected = selectedChip == chip,
                            onClick = { selectedChip = chip },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PopupActionButton(
                    label = "취소",
                    type = PopupActionType.OUTLINE,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                PopupActionButton(
                    label = if (initialEvent == null) "추가" else "저장",
                    type = PopupActionType.FILL,
                    onClick = {
                        val day = dayText.toIntOrNull() ?: defaultDay
                        if (title.isNotBlank()) onConfirm(title, time.ifBlank { "00:00" }, day, description, selectedChip, imageUris)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
    )
}

@Composable
private fun ScheduleFilterChip(
    chip: ScheduleChip,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) AppColors.DeepBrown900 else Color.White,
        border = if (selected) null else BorderStroke(1.dp, AppColors.DeepBrown900),
    ) {
        Text(
            chip.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else AppColors.DeepBrown900,
                lineHeight = 16.sp,
            ),
        )
    }
}

private enum class PopupActionType { FILL, OUTLINE }

@Composable
private fun PopupActionButton(
    label: String,
    type: PopupActionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFill = type == PopupActionType.FILL
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (isFill) AppColors.DeepBrown900 else Color.White,
        border = if (isFill) null else BorderStroke(1.dp, AppColors.DeepBrown950),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = if (isFill) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFill) Color.White else AppColors.DeepBrown900,
                    lineHeight = 16.sp,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────
// 헬퍼
// ─────────────────────────────────────────────────────

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = TextStyle(fontSize = 11.sp, color = OnLightSub))
    }
}

private fun eventDotColor(type: EventType): Color = when (type) {
    EventType.AUTO -> AccentBlue
    EventType.MANUAL -> AppColors.DeepBrown900
}

private fun daysInMonth(year: Int, month: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, 1)
    return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
}

private fun firstDayOfWeek(year: Int, month: Int): Int {
    val cal = Calendar.getInstance()
    cal.set(year, month - 1, 1)
    return cal.get(Calendar.DAY_OF_WEEK) - 1
}
