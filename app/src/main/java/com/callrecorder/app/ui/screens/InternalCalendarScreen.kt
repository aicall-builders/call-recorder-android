package com.callrecorder.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.CalendarEvent as ApiEvent
import java.util.Calendar

// ─────────────────────────────────────────────────────
// 다크 2-tone 색상
// ─────────────────────────────────────────────────────
private val DarkNavy = Color(0xFF3D4D6B)
private val LightBg = Color(0xFFF0F2F5)
private val WhiteCard = Color(0xFFFFFFFF)
private val AccentBlue = Color(0xFF3B7DD8)
private val OnDarkPrimary = Color(0xFFFFFFFF)
private val OnLightPrimary = Color(0xFF1F2937)
private val OnLightSub = Color(0xFF6B7280)
private val DividerLight = Color(0xFFE5E7EB)

// ─────────────────────────────────────────────────────
// 화면 전용 모델 (API 모델과 별개 — 캘린더 그리드/카드 표시용)
// ─────────────────────────────────────────────────────

enum class EventType { AUTO, MANUAL, PHOTO }

data class CalEvent(
    val id: String,
    val title: String,
    val date: Int,           // 해당 월의 일(day)
    val time: String,        // "19:00"
    val type: EventType,
    val subTitle: String = "",
    val hasPhoto: Boolean = false,
)

/** API CalendarEvent → 화면용 CalEvent 변환 */
private fun ApiEvent.toCalEvent(): CalEvent? {
    val day = dayOfMonth ?: return null
    return CalEvent(
        id = id,
        title = title,
        date = day,
        time = time,
        type = EventType.AUTO,   // 캘린더 API에서 온 건 통화 자동등록
        subTitle = description.ifBlank { "통화에서 자동 등록" },
    )
}

// ─────────────────────────────────────────────────────
// 내부 캘린더 메인 화면 (다크 헤더 "일정관리", 탭 없음)
// ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalCalendarScreen(
    calVm: CalendarViewModel = viewModel(),
) {
    val calState by calVm.state.collectAsState()

    val nowCal = remember { Calendar.getInstance() }
    var currentYear by remember { mutableStateOf(nowCal.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(nowCal.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableStateOf(nowCal.get(Calendar.DAY_OF_MONTH)) }

    var showAddDialog by remember { mutableStateOf(false) }

    // 수동 추가 일정 (로컬 보관)
    val manualEvents = remember { mutableStateListOf<CalEvent>() }

    // API 일정 + 수동 일정 병합
    val apiEvents = remember(calState.events) {
        calState.events.mapNotNull { it.toCalEvent() }
    }
    val allEvents = remember(apiEvents, manualEvents.toList()) {
        apiEvents + manualEvents
    }

    Scaffold(
        containerColor = LightBg,
        topBar = {
            TopAppBar(
                title = {
                    Text("일정관리", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary))
                },
                actions = {
                    // 우측 동그란 + 버튼
                    Surface(
                        onClick = { showAddDialog = true },
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.padding(end = 12.dp).size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, "일정 추가", tint = OnDarkPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkNavy),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                onAddClick = { showAddDialog = true },
            )
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            defaultDay = selectedDay,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, time, day ->
                manualEvents.add(
                    CalEvent(
                        id = System.currentTimeMillis().toString(),
                        title = title,
                        date = day,
                        time = time,
                        type = EventType.MANUAL,
                        subTitle = "직접 추가",
                    ),
                )
                showAddDialog = false
            },
        )
    }
}

// ─────────────────────────────────────────────────────
// 캘린더 본문
// ─────────────────────────────────────────────────────

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
) {
    val daysInMonth = daysInMonth(year, month)
    val firstDayOfWeek = firstDayOfWeek(year, month)

    val selectedEvents = events.filter { it.date == selectedDay }.sortedBy { it.time }
    val eventDays = events.groupBy { it.date }

    val today = remember { Calendar.getInstance() }
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
        // 월 네비게이션
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.Filled.ChevronLeft, "이전 달", tint = OnLightSub)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${year}년 ${month}월", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                    if (loading) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentBlue)
                    }
                }
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Filled.ChevronRight, "다음 달", tint = OnLightSub)
                }
            }
        }

        // 캘린더 그리드
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = WhiteCard,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { idx, day ->
                            Text(
                                day, Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = TextStyle(
                                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    color = when (idx) { 0 -> Color(0xFFEF4444); 6 -> AccentBlue; else -> OnLightSub },
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))

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
                                val dayEvents = if (isValid) eventDays[day] else null

                                Box(Modifier.weight(1f).padding(vertical = 2.dp), contentAlignment = Alignment.TopCenter) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.clip(CircleShape).then(
                                            if (isValid) Modifier.clickable { onDaySelect(day) } else Modifier
                                        ),
                                    ) {
                                        Box(
                                            Modifier.size(30.dp).clip(CircleShape).background(
                                                when {
                                                    isSelected -> AccentBlue
                                                    isToday -> AccentBlue.copy(alpha = 0.12f)
                                                    else -> Color.Transparent
                                                }
                                            ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (isValid) {
                                                Text(
                                                    day.toString(),
                                                    style = TextStyle(
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = when {
                                                            isSelected -> Color.White
                                                            col == 0 -> Color(0xFFEF4444)
                                                            col == 6 -> AccentBlue
                                                            else -> OnLightPrimary
                                                        },
                                                    ),
                                                )
                                            }
                                        }
                                        if (!dayEvents.isNullOrEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 1.dp)) {
                                                dayEvents.take(3).forEach { ev ->
                                                    Box(Modifier.size(4.dp).clip(CircleShape).background(eventDotColor(ev.type)))
                                                }
                                            }
                                        } else {
                                            Spacer(Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 범례 (통화 자동등록 · 수동 등록)
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendItem(AccentBlue, "통화 자동등록")
                LegendItem(Color(0xFF059669), "수동 등록")
            }
        }

        // 선택된 날 일정 헤더
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${month}월 ${selectedDay}일 일정", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                TextButton(onClick = onAddClick) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = AccentBlue)
                    Spacer(Modifier.width(2.dp))
                    Text("일정 추가", style = TextStyle(fontSize = 12.sp, color = AccentBlue))
                }
            }
        }

        if (selectedEvents.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📅", style = TextStyle(fontSize = 32.sp))
                        Spacer(Modifier.height(8.dp))
                        Text("등록된 일정이 없어요", style = TextStyle(fontSize = 13.sp, color = OnLightSub))
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(onClick = onAddClick, border = androidx.compose.foundation.BorderStroke(1.dp, DividerLight)) {
                            Text("일정 직접 추가", style = TextStyle(fontSize = 12.sp, color = OnLightSub))
                        }
                    }
                }
            }
        } else {
            items(selectedEvents, key = { it.id }) { event ->
                EventCard(event = event, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, DividerLight, RoundedCornerShape(12.dp))
                        .clickable { onAddClick() }.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = OnLightSub)
                        Spacer(Modifier.width(6.dp))
                        Text("일정 직접 추가", style = TextStyle(fontSize = 13.sp, color = OnLightSub))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// 이벤트 카드
// ─────────────────────────────────────────────────────

@Composable
private fun EventCard(event: CalEvent, modifier: Modifier = Modifier) {
    val barColor = eventDotColor(event.type)
    val (badgeText, badgeBg, badgeFg) = when (event.type) {
        EventType.AUTO -> Triple("🤖 통화 자동등록", Color(0xFFEFF6FF), AccentBlue)
        EventType.MANUAL -> Triple("✏️ 수동 등록", Color(0xFFECFDF5), Color(0xFF059669))
        EventType.PHOTO -> Triple("📷 사진 첨부", Color(0xFFFFFBEB), Color(0xFFD97706))
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WhiteCard,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(14.dp)) {
            Box(Modifier.width(4.dp).height(60.dp).clip(RoundedCornerShape(2.dp)).background(barColor))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnLightPrimary))
                Spacer(Modifier.height(3.dp))
                Text(event.subTitle, style = TextStyle(fontSize = 11.sp, color = OnLightSub))
                Spacer(Modifier.height(6.dp))
                Surface(color = badgeBg, shape = RoundedCornerShape(20.dp)) {
                    Text(badgeText, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = badgeFg))
                }
            }
            Text(event.time, style = TextStyle(fontSize = 12.sp, color = OnLightSub))
        }
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
    onDismiss: () -> Unit,
    onConfirm: (title: String, time: String, day: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var dayText by remember { mutableStateOf(defaultDay.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정 추가", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("일정 제목", fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("시간 (예: 14:00)", fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dayText, onValueChange = { dayText = it }, label = { Text("날짜 (일)", fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val day = dayText.toIntOrNull() ?: defaultDay
                    if (title.isNotBlank()) onConfirm(title, time.ifBlank { "00:00" }, day)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            ) { Text("추가") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
        containerColor = Color.White,
    )
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
    EventType.AUTO -> Color(0xFF1A56DB)
    EventType.MANUAL -> Color(0xFF059669)
    EventType.PHOTO -> Color(0xFFF59E0B)
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