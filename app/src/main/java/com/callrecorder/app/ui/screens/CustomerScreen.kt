package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.extractedInfoOrNull
import java.text.SimpleDateFormat
import java.util.*

/* ── 색상: 홈/통화 화면과 동일 톤 ── */
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

private enum class CustFilter { ALL, VIP, NEW, RECENT }

// ─────────────────────────────────────────────────────
// 고객 목록
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(vm: CustomerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CustFilter.ALL) }
    var selectedCustomer by remember { mutableStateOf<CustomerUiItem?>(null) }

    if (selectedCustomer != null) {
        CustomerDetailScreen(customer = selectedCustomer!!, onBack = { selectedCustomer = null })
        return
    }

    val all = state.customers
    val totalCount = all.size
    val vipCount = all.count { it.isVip }
    val newCount = all.count { it.callCount == 1 }

    val filtered = remember(all, filter, searchText.text) {
        all.filter { c ->
            when (filter) {
                CustFilter.ALL, CustFilter.RECENT -> true
                CustFilter.VIP -> c.isVip
                CustFilter.NEW -> c.callCount == 1
            }
        }.filter { c ->
            val q = searchText.text.trim()
            q.isBlank() || (c.name?.contains(q) == true) || c.phone.contains(q)
        }.let { list ->
            if (filter == CustFilter.RECENT)
                list.sortedByDescending { it.calls.maxOfOrNull { call -> call.createdAt ?: "" } ?: "" }
            else list
        }
    }

    Scaffold(containerColor = LightBg) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LightBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(0.dp),
        ) {
            // ═══ 다크 헤더 ═══
            item {
                Surface(color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.People, null, tint = OnDarkPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("고객 관리", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary))
                            }
                            Icon(Icons.Filled.NotificationsNone, "알림", tint = OnDarkPrimary, modifier = Modifier.size(22.dp))
                        }

                        Spacer(Modifier.height(10.dp))

                        // 검색바
                        Surface(color = SearchBarBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Search, null, tint = OnDarkSub, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f)) {
                                    if (searchText.text.isEmpty()) {
                                        Text("고객 이름 또는 전화번호 검색", style = TextStyle(fontSize = 14.sp, color = OnDarkSub))
                                    }
                                    BasicTextField(
                                        value = searchText, onValueChange = { searchText = it },
                                        textStyle = TextStyle(fontSize = 14.sp, color = OnDarkPrimary),
                                        cursorBrush = SolidColor(OnDarkPrimary), singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 필터 칩
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip("전체 $totalCount", filter == CustFilter.ALL) { filter = CustFilter.ALL }
                            FilterChip("VIP $vipCount", filter == CustFilter.VIP) { filter = CustFilter.VIP }
                            FilterChip("신규 $newCount", filter == CustFilter.NEW) { filter = CustFilter.NEW }
                            FilterChip("최근통화순", filter == CustFilter.RECENT) { filter = CustFilter.RECENT }
                        }
                    }
                }
            }

            // ═══ 본문 ═══
            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }
            }
            if (!state.loading && filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👤", style = TextStyle(fontSize = 40.sp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (searchText.text.isBlank()) "아직 고객 정보가 없어요" else "검색 결과가 없어요",
                            style = TextStyle(fontSize = 14.sp, color = OnLightMuted))
                    }
                }
            }
            items(filtered, key = { it.phone }) { customer ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    CustomerCard(customer = customer, onClick = { selectedCustomer = customer })
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

/* ── 필터 칩 (다크 헤더용) ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) OnDarkPrimary else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) OnDarkPrimary else OnDarkSub.copy(alpha = 0.4f)),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) DarkNavy else OnDarkSub))
    }
}

/* ── 고객 카드 ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerCard(customer: CustomerUiItem, onClick: () -> Unit) {
    val (avatarBg, avatarFg) = avatarColor(customer.phone)
    val displayName = customer.name ?: customer.phone
    val lastSummary = customer.calls.maxByOrNull { it.createdAt ?: "" }?.summary

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = WhiteCard, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(avatarBg), contentAlignment = Alignment.Center) {
                Text(displayName.take(1), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = avatarFg))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(displayName, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                    if (customer.isVip) GradeBadge("VIP", Color(0xFFFAEEDA), Color(0xFF854F0B))
                    else if (customer.callCount == 1) GradeBadge("신규", Color(0xFFE1F5EE), Color(0xFF0F6E56))
                    else if (customer.callCount >= 5) GradeBadge("단골", Color(0xFFEBE9FB), Color(0xFF5B4FC2))
                }
                Spacer(Modifier.height(3.dp))
                Text("${customer.phone} · ${customer.callCount}회 통화",
                    style = TextStyle(fontSize = 12.sp, color = OnLightSub))
                if (!lastSummary.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(lastSummary, style = TextStyle(fontSize = 12.sp, color = OnLightMuted, lineHeight = 16.sp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GradeBadge(label: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg))
    }
}

// ─────────────────────────────────────────────────────
// 고객 상세
// ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(customer: CustomerUiItem, onBack: () -> Unit) {
    val displayName = customer.name ?: customer.phone

    // 히스토리 항목 클릭 → 메모/사진 편집 화면으로 분기
    var editingCall by remember { mutableStateOf<Call?>(null) }

    // 통화별 사진 미리보기: callId -> 사진 URL 목록
    var photoMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val notesRepo = remember { CallRecorderApp.instance.container.notesRepo }
    // 화면 진입 시 + 편집 화면에서 돌아올 때(editingCall == null) 사진 다시 로드
    LaunchedEffect(customer.phone, editingCall == null) {
        if (editingCall == null) {
            val result = mutableMapOf<String, List<String>>()
            for (call in customer.calls) {
                notesRepo.getNote(call.id).onSuccess { note ->
                    if (note.photos.isNotEmpty()) {
                        result[call.id] = note.photos.map { it.url }
                    }
                }
            }
            photoMap = result
        }
    }

    if (editingCall != null) {
        val c = editingCall!!
        CallNoteEditScreen(
            callId = c.id,
            callTitle = (c.category ?: "통화") + " · " + formatCallDate(c.createdAt),
            onBack = { editingCall = null },
        )
        return
    }

    val thisMonthCount = customer.calls.count { isThisMonth(it.createdAt) }
    val reservationCount = customer.calls.count { it.category == "예약" }

    Scaffold(containerColor = LightBg) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(LightBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(0.dp),
        ) {
            // ═══ 다크 헤더 + 프로필 ═══
            item {
                Surface(color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
                        // 상단 바
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = OnDarkPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("고객 상세", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary))
                            }
                            Icon(Icons.Filled.NotificationsNone, "알림", tint = OnDarkPrimary, modifier = Modifier.size(22.dp))
                        }

                        Spacer(Modifier.height(12.dp))

                        // 프로필
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Text(displayName.take(1), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(displayName, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White))
                                    if (customer.isVip) {
                                        Surface(color = Color(0x33FAC775), shape = RoundedCornerShape(6.dp)) {
                                            Text("VIP", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFAC775)))
                                        }
                                    }
                                }
                                Text(customer.phone, style = TextStyle(fontSize = 13.sp, color = OnDarkSub))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 통계
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            StatItem("${customer.callCount}건", "총 통화")
                            StatDivider()
                            StatItem("${reservationCount}건", "예약 건수")
                            StatDivider()
                            StatItem("${thisMonthCount}건", "이번 달")
                        }
                    }
                }
            }

            // ═══ AI 고객 분석 ═══
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✦", style = TextStyle(fontSize = 13.sp, color = AccentBlue))
                                Spacer(Modifier.width(6.dp))
                                Text("AI 고객 분석", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                            }
                            Spacer(Modifier.height(8.dp))
                            val categories = customer.categories.joinToString(", ").ifBlank { "일반 문의" }
                            val vipText = if (customer.isVip) " VIP 고객으로 관리 우선순위가 높습니다." else ""
                            Text(
                                "${displayName} 고객은 주로 $categories 관련 통화가 많습니다. 총 ${customer.callCount}회 통화 기록이 있습니다.$vipText",
                                style = TextStyle(fontSize = 13.sp, color = OnLightSub, lineHeight = 20.sp),
                            )
                        }
                    }
                }
            }

            // ═══ 안내 문구 ═══
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Surface(color = Color(0xFFEFF4FC), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.EditNote, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "아래 통화 항목을 눌러 메모와 사진을 남길 수 있어요.",
                                style = TextStyle(fontSize = 13.sp, color = Color(0xFF2C4A6E), lineHeight = 18.sp),
                            )
                        }
                    }
                }
            }

            // ═══ 히스토리 ═══
            item {
                Text("히스토리", modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
            }
            if (customer.calls.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("통화 기록이 없어요", style = TextStyle(fontSize = 13.sp, color = OnLightMuted))
                    }
                }
            } else {
                items(customer.calls.sortedByDescending { it.createdAt }, key = { it.id }) { call ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        HistoryCard(
                            call = call,
                            photos = photoMap[call.id].orEmpty(),
                            onClick = { editingCall = call },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White))
        Spacer(Modifier.height(2.dp))
        Text(label, style = TextStyle(fontSize = 11.sp, color = OnDarkSub))
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(alpha = 0.15f)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryCard(call: Call, photos: List<String>, onClick: () -> Unit) {
    val info = call.extractedInfoOrNull()
    val dateLabel = formatCallDate(call.createdAt)

    Surface(onClick = onClick, color = WhiteCard, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(AccentBlue).align(Alignment.Top))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(dateLabel, style = TextStyle(fontSize = 11.sp, color = OnLightMuted))
                    call.category?.let {
                        Surface(color = Color(0xFFE3EEFB), shape = RoundedCornerShape(5.dp)) {
                            Text(it, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563B5)))
                        }
                    }
                }
                if (!call.summary.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(call.summary, style = TextStyle(fontSize = 13.sp, color = OnLightPrimary, lineHeight = 19.sp), maxLines = 2)
                }
                val keywords = buildList {
                    info?.time?.let { add(it) }
                    info?.partySize?.let { add("${it}인") }
                }
                if (keywords.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        keywords.take(3).forEach { kw ->
                            Surface(color = LightBg, shape = RoundedCornerShape(5.dp)) {
                                Text(kw, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = TextStyle(fontSize = 10.sp, color = OnLightSub))
                            }
                        }
                    }
                }

                // ═══ 사진 썸네일 ═══
                if (photos.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        photos.take(4).forEachIndexed { index, url ->
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(LightBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                // 4장 초과 시 마지막 칸에 +N 오버레이
                                if (index == 3 && photos.size > 4) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("+${photos.size - 4}", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EditNote, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (photos.isEmpty()) "메모 · 사진 추가" else "사진 ${photos.size}장 · 메모 보기",
                        style = TextStyle(fontSize = 11.sp, color = AccentBlue),
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, null, tint = OnLightMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// 유틸
// ─────────────────────────────────────────────────────
private val avatarColorPalette = listOf(
    Color(0xFFDBEAFE) to Color(0xFF1A56DB),
    Color(0xFFFAECE7) to Color(0xFF993C1D),
    Color(0xFFE1F5EE) to Color(0xFF0F6E56),
    Color(0xFFEEEDFE) to Color(0xFF534AB7),
    Color(0xFFFAEEDA) to Color(0xFF854F0B),
)

private fun avatarColor(key: String): Pair<Color, Color> =
    avatarColorPalette[key.hashCode().and(0x7FFFFFFF) % avatarColorPalette.size]

private fun formatCallDate(createdAt: String?): String {
    if (createdAt.isNullOrBlank()) return ""
    val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (fmt in fmts) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = sdf.parse(createdAt) ?: continue
            return SimpleDateFormat("yyyy.MM.dd · HH:mm", Locale.KOREAN).apply { timeZone = TimeZone.getDefault() }.format(date)
        } catch (_: Exception) {}
    }
    return createdAt
}

private fun isThisMonth(createdAt: String?): Boolean {
    if (createdAt.isNullOrBlank()) return false
    val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (fmt in fmts) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = sdf.parse(createdAt) ?: continue
            val cal = Calendar.getInstance().apply { time = date }
            val now = Calendar.getInstance()
            return cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
        } catch (_: Exception) {}
    }
    return false
}