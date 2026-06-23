package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.callrecorder.app.data.model.DtoJson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* ── 색상: 홈 화면과 동일 톤 ── */
private val DarkNavy       = Color(0xFF3D4D6B)
private val LightBg        = Color(0xFFF0F2F5)
private val WhiteCard      = Color(0xFFFFFFFF)
private val AccentBlue     = Color(0xFF3B7DD8)
private val OnDarkPrimary  = Color(0xFFFFFFFF)
private val OnDarkSub      = Color(0xFFC5D0E0)
private val OnLightPrimary = Color(0xFF1F2A3D)
private val OnLightSub     = Color(0xFF6B7889)
private val OnLightMuted   = Color(0xFF9AA5B5)
private val SearchBarBg    = Color(0xFF4A5A78)

// 배지
private val BadgeResBg = Color(0xFFE3EEFB); private val BadgeResFg = Color(0xFF2563B5)
private val BadgeCnlBg = Color(0xFFFBE3E3); private val BadgeCnlFg = Color(0xFFC23B3B)
private val BadgeCmpBg = Color(0xFFFBF0E0); private val BadgeCmpFg = Color(0xFFC07818)
private val BadgeInqBg = Color(0xFFEBE9FB); private val BadgeInqFg = Color(0xFF5B4FC2)
private val BadgeNeuBg = Color(0xFFE8EBF0); private val BadgeNeuFg = Color(0xFF6B7889)

private enum class CallFilter { ALL, RESERVATION, INQUIRY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSummaryListScreen(
    onCallClick: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CallFilter.ALL) }

    val uniqueCalls = remember(state.recentCalls) { state.recentCalls.distinctBy { it.id } }

    // 카운트
    val totalCount = uniqueCalls.size
    val resCount = uniqueCalls.count { it.category == CallCategoryLabel.RESERVATION }
    val inqCount = uniqueCalls.count { it.category == CallCategoryLabel.INQUIRY }

    // 필터 + 검색
    val filtered = remember(uniqueCalls, filter, searchText.text) {
        uniqueCalls
            .filter { call ->
                when (filter) {
                    CallFilter.ALL -> true
                    CallFilter.RESERVATION -> call.category == CallCategoryLabel.RESERVATION
                    CallFilter.INQUIRY -> call.category == CallCategoryLabel.INQUIRY
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

    Scaffold(containerColor = LightBg) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(0.dp),
        ) {
            // ═══ 다크 헤더 ═══
            item {
                Surface(color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
                        // 타이틀 행
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Phone, null, tint = OnDarkPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("통화 목록", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary))
                            }
                            Icon(Icons.Filled.NotificationsNone, "알림", tint = OnDarkPrimary, modifier = Modifier.size(22.dp))
                        }

                        Spacer(Modifier.height(10.dp))

                        // 검색바
                        Surface(color = SearchBarBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Search, null, tint = OnDarkSub, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f)) {
                                    if (searchText.text.isEmpty()) {
                                        Text("전화번호 또는 요약 검색", style = TextStyle(fontSize = 14.sp, color = OnDarkSub))
                                    }
                                    BasicTextField(
                                        value = searchText,
                                        onValueChange = { searchText = it },
                                        textStyle = TextStyle(fontSize = 14.sp, color = OnDarkPrimary),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(OnDarkPrimary),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 필터 칩
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip("날짜 $totalCount", filter == CallFilter.ALL) { filter = CallFilter.ALL }
                            FilterChip("예약 $resCount", filter == CallFilter.RESERVATION) { filter = CallFilter.RESERVATION }
                            FilterChip("문의 $inqCount", filter == CallFilter.INQUIRY) { filter = CallFilter.INQUIRY }
                        }
                    }
                }
            }

            // ═══ 본문 ═══
            if (state.loading && filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("해당하는 통화가 없어요", style = TextStyle(fontSize = 13.sp, color = OnLightMuted))
                    }
                }
            } else {
                grouped.forEach { (dateLabel, calls) ->
                    item {
                        Text(
                            dateLabel,
                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnLightSub),
                        )
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
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
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
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
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

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/* ── 필터 칩 ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) OnDarkPrimary else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) OnDarkPrimary else OnDarkSub.copy(alpha = 0.4f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) DarkNavy else OnDarkSub,
            ),
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

    val internalKw: Map<String, String> = remember(call.internalKeywordsRaw) {
        call.internalKeywordsMap()
    }

    val processResult = call.summary?.takeIf { it.isNotBlank() }
        ?: info?.specialNotes?.takeIf { it.isNotBlank() }
        ?: internalKw.entries.firstOrNull()?.value
        ?: "처리 결과 없음"

    // 표시 우선순위: 수정된 이름 > 번호(연락처 매칭값) > 없음
    val name = call.callerName?.takeIf { it.isNotBlank() }
    val number = call.callerNumber?.takeIf { it.isNotBlank() }
    val primary = name ?: number ?: "발신번호 없음"

    var showEdit by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = WhiteCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            // 이름/번호 + 수정 + 배지
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        primary,
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary),
                    )
                    // 이름과 번호가 둘 다 있으면 번호를 아래에 작게 표시
                    if (name != null && number != null) {
                        Text(
                            number,
                            style = TextStyle(fontSize = 12.sp, color = OnLightSub),
                        )
                    }
                }
                Icon(
                    Icons.Filled.Edit,
                    "수정",
                    tint = OnLightMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { showEdit = true },
                )
                Spacer(Modifier.width(10.dp))
                CategoryBadge(call.category)
            }

            Spacer(Modifier.height(8.dp))

            // 통화 시간
            Row {
                Text("통화 시간", style = TextStyle(fontSize = 12.sp, color = OnLightSub), modifier = Modifier.width(64.dp))
                Text(callTime, style = TextStyle(fontSize = 12.sp, color = OnLightPrimary, fontWeight = FontWeight.Medium))
            }
            Spacer(Modifier.height(4.dp))
            // 처리 결과
            Row {
                Text("처리 결과", style = TextStyle(fontSize = 12.sp, color = OnLightSub), modifier = Modifier.width(64.dp))
                Text(processResult, style = TextStyle(fontSize = 12.sp, color = OnLightPrimary, fontWeight = FontWeight.Medium), maxLines = 1)
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
            Text("발신자 정보 수정", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
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
                Text("취소", style = TextStyle(color = OnLightSub))
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
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg))
    }
}

/* ── 유틸 ── */
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

/** 날짜 그룹 라벨: "오늘" / "어제" / "2026.06.08" */
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
        else -> SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN).format(date)
    }
}

/** "HH:mm" 또는 "MM/dd HH:mm" */
private fun formatTimeShort(serverTime: String?): String {
    val date = parseTime(serverTime) ?: return ""
    return SimpleDateFormat("HH:mm", Locale.KOREAN).format(date)
}