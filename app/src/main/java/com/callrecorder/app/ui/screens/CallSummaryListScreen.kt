package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CallCategoryLabel
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import com.callrecorder.app.data.model.isAnalyzed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* ── 색상: 시안(통화 관리) 기준 ── */
private val ScreenBg       = Color(0xFF5F6071)   // 화면 전체 배경(헤더/검색 영역)
private val SheetBg        = Color(0xFFFFFFFF)   // 본문 흰 시트
private val TabInactiveBg  = Color(0xFFEEEEEE)   // 비활성 탭
private val Ink            = Color(0xFF343659)   // 진한 남보라 (타이틀/선택 칩)
private val SubInk         = Color(0xFF5A5D86)   // 보조 남보라
private val AccentBlue     = Color(0xFF2867E5)   // 시간/포인트 블루
private val OnDarkPrimary  = Color(0xFFFFFFFF)
private val PlaceholderGray = Color(0xFF99A1AF)
private val LabelGray      = Color(0xFF99A1AF)
private val GroupGray      = Color(0xFF737373)
private val SearchBorder   = Color(0xFFE8ECF2)

// 캘린더 등록 안내 박스
private val CalBoxBg = Color(0xFFE8F2FF)
private val CalBoxFg = Color(0xFF1C6BD4)

// 배지 (시안: 예약=Ink, 문의=SubInk, 기타=연회색)
private val BadgeResBg = Ink;             private val BadgeResFg = Color(0xFFFFFFFF)
private val BadgeInqBg = SubInk;          private val BadgeInqFg = Color(0xFFFFFFFF)
private val BadgeCnlBg = Color(0xFFC23B3B); private val BadgeCnlFg = Color(0xFFFFFFFF)
private val BadgeCmpBg = Color(0xFFC07818); private val BadgeCmpFg = Color(0xFFFFFFFF)
private val BadgeNeuBg = Color(0xFFECEEF6); private val BadgeNeuFg = SubInk

private enum class CallFilter { ALL, RESERVATION, INQUIRY, OTHER }
private enum class AnalysisTab { PENDING, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSummaryListScreen(
    onCallClick: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CallFilter.ALL) }
    var tab by remember { mutableStateOf(AnalysisTab.DONE) }

    val uniqueCalls = remember(state.recentCalls) { state.recentCalls.distinctBy { it.id } }

    // ① 분석 대기 / 완료 분리
    val tabCalls = remember(uniqueCalls, tab) {
        uniqueCalls.filter { call ->
            when (tab) {
                AnalysisTab.DONE -> call.isAnalyzed()
                AnalysisTab.PENDING -> !call.isAnalyzed()
            }
        }
    }

    // 카운트 (현재 탭 기준)
    val totalCount = tabCalls.size
    val resCount = tabCalls.count { it.category == CallCategoryLabel.RESERVATION }
    val inqCount = tabCalls.count { it.category == CallCategoryLabel.INQUIRY }
    val otherCount = tabCalls.count {
        it.category != CallCategoryLabel.RESERVATION && it.category != CallCategoryLabel.INQUIRY
    }

    // 필터 + 검색
    val filtered = remember(tabCalls, filter, searchText.text) {
        tabCalls
            .filter { call ->
                when (filter) {
                    CallFilter.ALL -> true
                    CallFilter.RESERVATION -> call.category == CallCategoryLabel.RESERVATION
                    CallFilter.INQUIRY -> call.category == CallCategoryLabel.INQUIRY
                    CallFilter.OTHER ->
                        call.category != CallCategoryLabel.RESERVATION &&
                                call.category != CallCategoryLabel.INQUIRY
                }
            }
            .filter { call ->
                val q = searchText.text.trim()
                if (q.isBlank()) true
                else {
                    val num = call.callerNumber ?: ""
                    val nm = call.callerName ?: ""
                    val sum = call.summary ?: ""
                    num.contains(q) || nm.contains(q) || sum.contains(q)
                }
            }
            .sortedByDescending { it.createdAt }
    }

    // 날짜별 그룹
    val grouped = remember(filtered) {
        filtered.groupBy { dateGroupLabel(it.createdAt) }
    }

    Scaffold(containerColor = ScreenBg) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(0.dp),
        ) {
            // ═══ 헤더 (ScreenBg 위) ═══
            item {
                Column(Modifier.fillMaxWidth()) {
                    // 타이틀 행
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Phone, null, tint = OnDarkPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("통화 관리", style = TextStyle(fontSize = 18.sp, color = OnDarkPrimary))
                        }
                        Icon(Icons.Filled.NotificationsNone, "알림", tint = OnDarkPrimary, modifier = Modifier.size(20.dp))
                    }

                    // 서브타이틀 + 검색바
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
                        Text(
                            "통화 분석 내역을 확인해보세요.",
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary),
                        )
                        Spacer(Modifier.height(16.dp))
                        // 흰색 pill 검색바
                        Surface(
                            color = SheetBg,
                            shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SearchBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, null, tint = PlaceholderGray, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (searchText.text.isEmpty()) {
                                        Text("전화번호 또는 요약 검색", style = TextStyle(fontSize = 14.sp, color = PlaceholderGray))
                                    }
                                    BasicTextField(
                                        value = searchText,
                                        onValueChange = { searchText = it },
                                        textStyle = TextStyle(fontSize = 14.sp, color = Ink),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═══ 분석 대기 / 완료 탭 (시트 상단, 라운드) ═══
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                ) {
                    AnalysisTabButton(
                        text = "분석 대기",
                        selected = tab == AnalysisTab.PENDING,
                        modifier = Modifier.weight(1f),
                    ) { tab = AnalysisTab.PENDING; filter = CallFilter.ALL }
                    AnalysisTabButton(
                        text = "분석 완료",
                        selected = tab == AnalysisTab.DONE,
                        modifier = Modifier.weight(1f),
                    ) { tab = AnalysisTab.DONE; filter = CallFilter.ALL }
                }
            }

            // ═══ 본문 (흰 시트) ═══
            // 필터 칩
            item {
                Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip("전체 $totalCount", filter == CallFilter.ALL) { filter = CallFilter.ALL }
                        FilterChip("예약 $resCount", filter == CallFilter.RESERVATION) { filter = CallFilter.RESERVATION }
                        FilterChip("문의 $inqCount", filter == CallFilter.INQUIRY) { filter = CallFilter.INQUIRY }
                        FilterChip("기타 $otherCount", filter == CallFilter.OTHER) { filter = CallFilter.OTHER }
                    }
                }
            }

            if (state.loading && filtered.isEmpty()) {
                item {
                    Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentBlue)
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (tab == AnalysisTab.PENDING) "분석 대기 중인 통화가 없어요" else "해당하는 통화가 없어요",
                                style = TextStyle(fontSize = 13.sp, color = PlaceholderGray),
                            )
                        }
                    }
                }
            } else {
                grouped.forEach { (dateLabel, calls) ->
                    item {
                        Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                dateLabel,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GroupGray),
                            )
                        }
                    }
                    items(calls, key = { it.id }) { call ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    vm.deleteCall(call.id)
                                    true
                                } else false
                            }
                        )
                        Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFFE53E3E)),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete, "삭제",
                                            tint = Color.White,
                                            modifier = Modifier.padding(end = 20.dp).size(24.dp),
                                        )
                                    }
                                },
                            ) {
                                Box(
                                    Modifier
                                        .background(SheetBg)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                ) {
                                    if (tab == AnalysisTab.PENDING) {
                                        PendingCallCard(
                                            call = call,
                                            onClick = { onCallClick(call.id) },
                                            onSave = { num, nm -> vm.updateCaller(call.id, num, nm) },
                                        )
                                    } else {
                                        CallListCard(
                                            call = call,
                                            onClick = { onCallClick(call.id) },
                                            onSave = { num, nm -> vm.updateCaller(call.id, num, nm) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(84.dp))
                }
            }
        }
    }
}

/* ── 분석 대기/완료 탭 버튼 ── */
@Composable
private fun AnalysisTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) SheetBg else TabInactiveBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
            )
        }
    }
}

/* ── 필터 칩 ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Ink else SheetBg,
        shape = RoundedCornerShape(999.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Ink),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) OnDarkPrimary else Ink,
            ),
        )
    }
}

/* ── 분석 대기 상태 매핑 ── */
private data class PendingPhase(
    val label: String,
    val bg: Color,
    val fg: Color,
    val showProgress: Boolean,
    val isError: Boolean = false,
)

/** 백엔드 status → 대기 카드 표시.
 *  uploaded → 업로드됨 / transcribed → 요약 중 / processing → 처리 중 / error|failed → 실패 */
private fun pendingPhaseOf(call: Call): PendingPhase {
    return when (call.status.lowercase()) {
        "uploaded" -> PendingPhase("업로드됨", Color(0xFFE8EBF3), SubInk, showProgress = false)
        "transcribed" -> PendingPhase("요약 중", Color(0xFFE3EEFB), AccentBlue, showProgress = true)
        "processing" -> PendingPhase("처리 중", Color(0xFFE3EEFB), AccentBlue, showProgress = true)
        "error", "failed" -> PendingPhase("분석 실패", Color(0xFFFBE3E3), Color(0xFFC23B3B), showProgress = false, isError = true)
        else -> PendingPhase("분석 대기", Color(0xFFE8EBF3), SubInk, showProgress = true)
    }
}

/* ── 분석 대기 카드 ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingCallCard(
    call: Call,
    onClick: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val callTime = formatTimeShort(call.createdAt)
    val durationText = formatDuration(call.duration)
    val phase = pendingPhaseOf(call)

    val name = call.callerName?.takeIf { it.isNotBlank() }
    val number = call.callerNumber?.takeIf { it.isNotBlank() }
    val primary = name ?: number ?: "발신번호 없음"

    var showEdit by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = SheetBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 왼쪽 원형 아이콘
                Surface(color = Color(0xFFF1F3F8), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Phone, null, tint = Ink, modifier = Modifier.size(18.dp))
                    }
                }

                Column(Modifier.weight(1f)) {
                    // 이름/번호 + 시간 + 상태 배지
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                primary,
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Edit, "수정",
                                tint = LabelGray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showEdit = true },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(callTime, style = TextStyle(fontSize = 13.sp, color = AccentBlue))
                        }
                        PhaseBadge(phase)
                    }

                    Spacer(Modifier.height(6.dp))

                    // 통화 길이
                    InfoRow("시간", durationText)
                    Spacer(Modifier.height(6.dp))

                    // 진행 안내 줄
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (phase.showProgress) {
                            CircularProgressIndicator(
                                color = AccentBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            if (phase.isError) "분석에 실패했어요. 다시 시도해 주세요."
                            else "분석이 끝나면 알려드릴게요.",
                            style = TextStyle(fontSize = 13.sp, color = if (phase.isError) Color(0xFFC23B3B) else LabelGray),
                        )
                    }
                }
            }
        }
    }

    if (showEdit) {
        CallerEditDialog(
            initialNumber = call.callerNumber ?: "",
            initialName = call.callerName ?: "",
            onDismiss = { showEdit = false },
            onSave = { num, nm ->
                onSave(num, nm)
                showEdit = false
            },
        )
    }
}

@Composable
private fun PhaseBadge(phase: PendingPhase) {
    Surface(color = phase.bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            phase.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = phase.fg),
        )
    }
}

/* ── 통화 카드 ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallListCard(
    call: Call,
    onClick: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val info = call.extractedInfoOrNull()
    val callTime = formatTimeShort(call.createdAt)
    val durationText = formatDuration(call.duration)

    val internalKw = remember(call.internalKeywordsRaw) { call.internalKeywordsMap() }

    val processResult = call.summary?.takeIf { it.isNotBlank() }
        ?: info?.specialNotes?.takeIf { it.isNotBlank() }
        ?: internalKw.entries.firstOrNull()?.value
        ?: "처리 결과 없음"

    val name = call.callerName?.takeIf { it.isNotBlank() }
    val number = call.callerNumber?.takeIf { it.isNotBlank() }
    val primary = name ?: number ?: "발신번호 없음"

    // ② 통화 방향 아이콘 (서버 방향 컬럼이 없으므로 현재는 통화 아이콘으로 통일.
    //    추후 Call.direction 들어오면 여기 분기만 바꾸면 됨)
    val directionIcon: ImageVector = Icons.Filled.Phone

    // ③ 캘린더 등록 안내 문구: 예약 + date·time 있을 때만
    val calendarText = remember(call.id, info) { buildCalendarNotice(call, info) }

    var showEdit by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = SheetBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 왼쪽 원형 통화 아이콘
                Surface(color = Color(0xFFF1F3F8), shape = CircleShape, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(directionIcon, null, tint = Ink, modifier = Modifier.size(18.dp))
                    }
                }

                Column(Modifier.weight(1f)) {
                    // 이름/번호 + 시간 + 배지
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                primary,
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Edit, "수정",
                                tint = LabelGray,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { showEdit = true },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(callTime, style = TextStyle(fontSize = 13.sp, color = AccentBlue))
                        }
                        CategoryBadge(call.category)
                    }

                    Spacer(Modifier.height(6.dp))

                    // 시간 / 내용
                    InfoRow("시간", durationText)
                    Spacer(Modifier.height(2.dp))
                    InfoRow("내용", processResult)
                }
            }

            // ③ 캘린더 등록 안내 박스
            if (calendarText != null) {
                Spacer(Modifier.height(8.dp))
                Surface(color = CalBoxBg, shape = RoundedCornerShape(999.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = CalBoxFg, modifier = Modifier.size(16.dp))
                        Text(
                            calendarText,
                            style = TextStyle(fontSize = 13.sp, color = CalBoxFg),
                        )
                    }
                }
            }
        }
    }

    if (showEdit) {
        CallerEditDialog(
            initialNumber = call.callerNumber ?: "",
            initialName = call.callerName ?: "",
            onDismiss = { showEdit = false },
            onSave = { num, nm ->
                onSave(num, nm)
                showEdit = false
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = TextStyle(fontSize = 13.sp, color = LabelGray))
        Text(
            value,
            style = TextStyle(fontSize = 13.sp, color = Ink),
            maxLines = 1,
        )
    }
}

/* ── 발신자 정보 수정 다이얼로그 ── */
@Composable
private fun CallerEditDialog(
    initialNumber: String,
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var number by remember { mutableStateOf(initialNumber) }
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("발신자 정보 수정", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("전화번호") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(number, name) }) {
                Text("저장", style = TextStyle(color = AccentBlue, fontWeight = FontWeight.Bold))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", style = TextStyle(color = SubInk))
            }
        },
    )
}

@Composable
private fun CategoryBadge(category: String?) {
    val (label, bg, fg) = when (category) {
        CallCategoryLabel.RESERVATION -> Triple("예약", BadgeResBg, BadgeResFg)
        CallCategoryLabel.CANCEL      -> Triple("취소", BadgeCnlBg, BadgeCnlFg)
        CallCategoryLabel.COMPLAINT   -> Triple("불만", BadgeCmpBg, BadgeCmpFg)
        CallCategoryLabel.INQUIRY     -> Triple("문의", BadgeInqBg, BadgeInqFg)
        else                          -> Triple("기타", BadgeNeuBg, BadgeNeuFg)
    }
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg),
        )
    }
}

/* ── 유틸 ── */

/** ③ 캘린더 등록 안내 문구 합성. 예약 + 날짜·시간 있을 때만 반환, 아니면 null */
private fun buildCalendarNotice(call: Call, info: com.callrecorder.app.data.model.ExtractedInfo?): String? {
    if (call.category != CallCategoryLabel.RESERVATION) return null
    if (info == null) return null
    val d = info.date?.takeIf { it.isNotBlank() } ?: return null
    val t = info.time?.takeIf { it.isNotBlank() } ?: return null

    val dayPart = dayLabel(d)            // "18일"
    val timePart = timeLabel(t)          // "저녁 7시"
    val what = info.specialNotes?.takeIf { it.isNotBlank() }
        ?: info.menu.firstOrNull()
        ?: "예약"
    return "$dayPart $timePart, ${what}을(를) 캘린더에 등록했어요."
}

/** "2026-06-18" → "18일" */
private fun dayLabel(date: String): String {
    val day = date.substringAfterLast("-").toIntOrNull() ?: return date
    return "${day}일"
}

/** "19:00" → "저녁 7시" (간단 변환) */
private fun timeLabel(time: String): String {
    val hm = time.split(":")
    val h = hm.getOrNull(0)?.toIntOrNull() ?: return time
    val m = hm.getOrNull(1)?.toIntOrNull() ?: 0
    val period = when (h) {
        in 0..5 -> "새벽"
        in 6..11 -> "오전"
        in 12..17 -> "오후"
        else -> "저녁"
    }
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return if (m == 0) "$period ${h12}시" else "$period ${h12}시 ${m}분"
}

/** 통화 길이(초) → "3분 24초" */
private fun formatDuration(seconds: Int?): String {
    val s = seconds ?: return "-"
    if (s <= 0) return "-"
    val m = s / 60
    val sec = s % 60
    return if (m > 0) "${m}분 ${sec}초" else "${sec}초"
}

private fun parseTime(s: String?): Date? {
    if (s.isNullOrBlank()) return null
    val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    for (fmt in fmts) {
        try {
            return SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s) ?: continue
        } catch (_: Exception) {}
    }
    return null
}

/** 날짜 그룹 라벨: "오늘" / "어제" / "2026. 06. 08" */
private fun dateGroupLabel(serverTime: String?): String {
    val date = parseTime(serverTime) ?: return "기타"
    val cal = java.util.Calendar.getInstance().apply { time = date }
    val now = java.util.Calendar.getInstance()
    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }

    return when {
        cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) -> "오늘"
        cal.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
                cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR) -> "어제"
        else -> SimpleDateFormat("yyyy. MM. dd", Locale.KOREAN).format(date)
    }
}

/** "HH:mm" */
private fun formatTimeShort(serverTime: String?): String {
    val date = parseTime(serverTime) ?: return ""
    return SimpleDateFormat("HH:mm", Locale.KOREAN).format(date)
}