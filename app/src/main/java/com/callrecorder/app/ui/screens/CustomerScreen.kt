package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.keywordsList
import com.callrecorder.app.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

/* ── 색상: FIANO 0705 디자인 시스템 ── */
private val ScreenBg   = AppColors.DeepBrown700
private val SheetBg    = Color(0xFFFFFFFF)
private val TabOffBg   = AppColors.DeepBrown50
private val Ink        = AppColors.DeepBrown950
private val SubInk     = AppColors.DeepBrown700
private val AccentBlue = AppColors.SignalRed500
private val PlaceholderGray = AppColors.DeepBrown400
private val LabelGray  = AppColors.DeepBrown500
private val Divider    = AppColors.DeepBrown200
private val AvatarBg    = AppColors.DeepBrown50
private val AvatarFg    = AppColors.DeepBrown700
private val SearchBorder = AppColors.DeepBrown100
private val PhoneGray   = AppColors.DeepBrown600

// 히스토리 시안 색
private val TimelineLine = AppColors.DeepBrown200
private val ChipBlueBg   = AppColors.SignalRed50
private val ChipBlueFg   = AppColors.SignalRed700
private val ChipGreenBg  = AppColors.DeepBrown50
private val ChipGreenFg  = AppColors.DeepBrown700
private val PhotoBg      = AppColors.DeepBrown50
private val LinkBlue     = AppColors.SignalRed600

// 등급 배지 색 (시안)
private fun gradeBadgeColors(grade: CustomerGrade): Triple<Color, Color, String> = when (grade) {
    CustomerGrade.VIP     -> Triple(Ink, Color.White, "VIP")
    CustomerGrade.REGULAR -> Triple(SubInk, Color.White, "단골")
    CustomerGrade.NORMAL  -> Triple(AppColors.DeepBrown500, Color.White, "일반")
    CustomerGrade.NEW     -> Triple(AppColors.DeepBrown100, Ink, "신규")
}

private enum class CustFilter { ALL, VIP, REGULAR, NORMAL, NEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(vm: CustomerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CustFilter.ALL) }
    var selectedCustomer by remember { mutableStateOf<CustomerUiItem?>(null) }

    if (selectedCustomer != null) {
        CustomerDetailScreen(customer = selectedCustomer!!, vm = vm, onBack = { selectedCustomer = null })
        return
    }

    val all = state.customers
    val totalCount = all.size
    val vipCount = all.count { it.grade == CustomerGrade.VIP }
    val regCount = all.count { it.grade == CustomerGrade.REGULAR }
    val normalCount = all.count { it.grade == CustomerGrade.NORMAL }
    val newCount = all.count { it.grade == CustomerGrade.NEW }

    val filtered = remember(all, filter, searchText.text) {
        all.filter { c ->
            when (filter) {
                CustFilter.ALL -> true
                CustFilter.VIP -> c.grade == CustomerGrade.VIP
                CustFilter.REGULAR -> c.grade == CustomerGrade.REGULAR
                CustFilter.NORMAL -> c.grade == CustomerGrade.NORMAL
                CustFilter.NEW -> c.grade == CustomerGrade.NEW
            }
        }.filter { c ->
            val q = searchText.text.trim()
            q.isBlank() || (c.name?.contains(q) == true) || c.phone.contains(q)
        }
    }

    Scaffold(containerColor = ScreenBg) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(ScreenBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(0.dp),
        ) {
            // ═══ 헤더 (ScreenBg) ═══
            item {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.People, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("고객관리", style = TextStyle(fontSize = 18.sp, color = Color.White))
                        }
                        Icon(Icons.Filled.NotificationsNone, "알림", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
                        Text("고객 데이터를 정리해두었어요.",
                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        Spacer(Modifier.height(16.dp))
                        // 흰색 pill 검색바
                        Surface(color = SheetBg, shape = RoundedCornerShape(999.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SearchBorder),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Search, null, tint = PlaceholderGray, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (searchText.text.isEmpty()) {
                                        Text("전화번호 또는 요약 검색", style = TextStyle(fontSize = 14.sp, color = PlaceholderGray))
                                    }
                                    BasicTextField(
                                        value = searchText, onValueChange = { searchText = it },
                                        textStyle = TextStyle(fontSize = 14.sp, color = Ink),
                                        cursorBrush = SolidColor(Ink), singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═══ 흰 시트 헤더 (라운드) + 필터 칩 ═══
            item {
                Surface(color = SheetBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GradeFilterChip("전체 $totalCount", filter == CustFilter.ALL) { filter = CustFilter.ALL }
                        GradeFilterChip("VIP $vipCount", filter == CustFilter.VIP) { filter = CustFilter.VIP }
                        GradeFilterChip("단골 $regCount", filter == CustFilter.REGULAR) { filter = CustFilter.REGULAR }
                        GradeFilterChip("일반 $normalCount", filter == CustFilter.NORMAL) { filter = CustFilter.NORMAL }
                        GradeFilterChip("신규 $newCount", filter == CustFilter.NEW) { filter = CustFilter.NEW }
                    }
                }
            }

            // ═══ 본문 ═══
            if (state.loading) {
                item {
                    Surface(color = SheetBg, modifier = Modifier.fillParentMaxHeight().fillMaxWidth()) {
                        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.TopCenter) {
                            CircularProgressIndicator(color = AccentBlue)
                        }
                    }
                }
            }
            if (!state.loading && filtered.isEmpty()) {
                item {
                    Surface(color = SheetBg, modifier = Modifier.fillParentMaxHeight().fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxSize().padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("아직 고객 정보가 없어요", style = TextStyle(fontSize = 14.sp, color = PlaceholderGray))
                        }
                    }
                }
            }
            items(filtered, key = { it.phone }) { customer ->
                Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        CustomerCard(customer = customer, onClick = { selectedCustomer = customer })
                    }
                }
            }
            item { Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) { Spacer(Modifier.height(80.dp)) } }
        }
    }
}

/* ── 필터 칩 (흰 시트용) ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Ink else SheetBg,
        shape = RoundedCornerShape(999.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Ink),
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else Ink))
    }
}

/* ── 고객 카드 (시안: 아바타 + 이름·통화수 + 등급배지 + 번호 + AI 한줄) ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerCard(customer: CustomerUiItem, onClick: () -> Unit) {
    val displayName = customer.name ?: customer.phone
    val (badgeBg, badgeFg, badgeLabel) = gradeBadgeColors(customer.grade)
    val aiLine = customer.lastSummary

    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = SheetBg) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(AvatarBg), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AvatarFg))
                }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(displayName, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101828)))
                            Icon(Icons.Filled.Call, null, tint = Ink, modifier = Modifier.size(12.dp))
                            Text("${customer.callCount}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink))
                        }
                        Surface(color = badgeBg, shape = RoundedCornerShape(999.dp)) {
                            Text(badgeLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = TextStyle(fontSize = 10.sp, color = badgeFg))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(customer.phone, style = TextStyle(fontSize = 14.sp, color = PhoneGray))
                    if (!aiLine.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(14.dp).background(AccentBlue))
                            Text(aiLine, style = TextStyle(fontSize = 13.sp, color = Ink, lineHeight = 20.sp), maxLines = 1)
                        }
                    }
                }
            }
            HorizontalDivider(color = Divider, thickness = 0.7.dp)
        }
    }
}

// ─────────────────────────────────────────────────────
// 고객 상세 (탭: 고객 분석 / 히스토리)
// ─────────────────────────────────────────────────────
private enum class CustDetailTab { ANALYSIS, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(customer: CustomerUiItem, vm: CustomerViewModel, onBack: () -> Unit) {
    val detail by vm.detail.collectAsState()
    var tab by remember { mutableStateOf(CustDetailTab.ANALYSIS) }
    var editingCall by remember { mutableStateOf<Call?>(null) }

    LaunchedEffect(customer.phone) { vm.enterDetail(customer) }

    // 메모/사진 편집 화면으로 분기
    if (editingCall != null) {
        val c = editingCall!!
        val (title, _) = callTitleAndBody(c)
        CallNoteEditScreen(
            callId = c.id,
            callTitle = title.ifBlank { c.category ?: "통화 메모" },
            onBack = {
                editingCall = null
                vm.enterDetail(customer)   // 편집 결과(사진/메모) 갱신
            },
        )
        return
    }

    val displayName = customer.name ?: customer.phone
    val (badgeBg, badgeFg, badgeLabel) = gradeBadgeColors(customer.grade)
    val reservationCount = customer.calls.count { it.category == "예약" }
    val relatedSchedule = customer.calls.count {
        val info = it.extractedInfoOrNull(); info?.date != null
    }

    Scaffold(containerColor = ScreenBg) { padding ->
        Column(Modifier.fillMaxSize().background(ScreenBg)
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {

            // 상단 바
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onBack() })
                    Spacer(Modifier.width(8.dp))
                    Text("고객 상세", style = TextStyle(fontSize = 18.sp, color = Color.White))
                }
            }

            // 프로필 헤더 (아바타 + 이름 + 배지 + 번호 + 통계 3개)
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF94A3C0)), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(displayName, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                    Surface(color = badgeBg, shape = RoundedCornerShape(999.dp)) {
                        Text(badgeLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = TextStyle(fontSize = 11.sp, color = badgeFg))
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(customer.phone, style = TextStyle(fontSize = 12.sp, color = Color(0xFFB8C4D9)))

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("${customer.callCount}회", "총 통화")
                    StatItem("${reservationCount}건", "예약 완료")
                    StatItem("${relatedSchedule}건", "관련 일정")
                }
            }

            // 탭
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
                CustTabButton("고객 분석", tab == CustDetailTab.ANALYSIS, Modifier.weight(1f)) { tab = CustDetailTab.ANALYSIS }
                CustTabButton("히스토리", tab == CustDetailTab.HISTORY, Modifier.weight(1f)) { tab = CustDetailTab.HISTORY }
            }

            // 본문
            Column(Modifier.fillMaxSize().background(SheetBg).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 24.dp, bottom = 80.dp)) {
                when (tab) {
                    CustDetailTab.ANALYSIS -> AnalysisTab(customer = customer, detail = detail, vm = vm)
                    CustDetailTab.HISTORY -> HistoryTab(customer = customer, detail = detail, onEditCall = { editingCall = it })
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
        Spacer(Modifier.height(1.dp))
        Text(label, style = TextStyle(fontSize = 10.sp, color = Color(0xFFA8B8D1)))
    }
}

@Composable
private fun CustTabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(color = if (selected) SheetBg else TabOffBg,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink))
        }
    }
}

/* ── 고객 분석 탭: AI 분석 카드 + 주요 정보(편집) ── */
@Composable
private fun AnalysisTab(customer: CustomerUiItem, detail: CustomerDetailState, vm: CustomerViewModel) {
    val profile = detail.profile

    var editing by remember { mutableStateOf(false) }
    var email by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var tendency by remember(profile) { mutableStateOf(profile?.tendency ?: "") }
    var medical by remember(profile) { mutableStateOf(profile?.medical ?: "") }
    var special by remember(profile) { mutableStateOf(profile?.specialNotes ?: "") }

    // AI 분석 카드
    Surface(color = Color(0xFFF8F8F8), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("✦ AI 고객 분석", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink))
            Spacer(Modifier.height(8.dp))
            val text = detail.analysis?.analysis
            Text(
                if (!text.isNullOrBlank()) text else "아직 분석이 준비되지 않았어요. 통화가 쌓이면 자동으로 생성돼요.",
                style = TextStyle(fontSize = 12.sp, color = if (text.isNullOrBlank()) PlaceholderGray else Ink, lineHeight = 17.sp),
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    // 주요 정보 (편집)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("주요 정보", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink))
        Text(
            if (editing) "저장" else "편집",
            style = TextStyle(fontSize = 12.sp, color = LinkBlue),
            modifier = Modifier.clickable {
                if (editing) {
                    vm.saveProfile(customer.phone, email, tendency, medical, special)
                }
                editing = !editing
            },
        )
    }
    Spacer(Modifier.height(8.dp))

    if (editing) {
        EditableField("이메일", email) { email = it }
        EditableField("고객성향", tendency) { tendency = it }
        EditableField("병력", medical) { medical = it }
        EditableField("특이사항", special) { special = it }
    } else {
        ReadOnlyField("이메일", email)
        ReadOnlyField("고객성향", tendency)
        ReadOnlyField("병력", medical)
        ReadOnlyField("특이사항", special)
    }

    detail.saveMessage?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = TextStyle(fontSize = 12.sp, color = SubInk))
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(62.dp), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LabelGray))
        Text(value.ifBlank { "-" }, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditableField(label: String, value: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(62.dp), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = LabelGray))
        Box(Modifier.weight(1f).background(Color(0xFFF4F4F6), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (value.isEmpty()) Text("입력", style = TextStyle(fontSize = 14.sp, color = PlaceholderGray))
            BasicTextField(value = value, onValueChange = onChange,
                textStyle = TextStyle(fontSize = 14.sp, color = Ink), cursorBrush = SolidColor(Ink),
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

/* ── 히스토리 탭: 통화 타임라인 (시안 반영) ── */
@Composable
private fun HistoryTab(
    customer: CustomerUiItem,
    detail: CustomerDetailState,
    onEditCall: (Call) -> Unit,
) {
    if (customer.calls.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            Text("통화 기록이 없어요", style = TextStyle(fontSize = 13.sp, color = PlaceholderGray))
        }
        return
    }
    customer.calls.forEachIndexed { idx, call ->
        HistoryRow(
            call = call,
            note = detail.notes[call.id],
            isFirst = idx == 0,
            isLast = idx == customer.calls.lastIndex,
            onEditClick = { onEditCall(call) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(
    call: Call,
    note: CustomerCallNote?,
    isFirst: Boolean,
    isLast: Boolean,
    onEditClick: () -> Unit,
) {
    val (title, body) = callTitleAndBody(call)
    val (dateStr, timeStr) = formatDateTime(call.createdAt)
    val chips = call.keywordsList().filter { it.isNotBlank() }.take(4)
    val photos = note?.photoUrls.orEmpty()
    val memo = note?.memo?.trim().orEmpty()
    val hasUserContent = memo.isNotBlank() || photos.isNotEmpty()

    Row(Modifier.fillMaxWidth()) {
        // 타임라인 점 + 선
        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AccentBlue))
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(TimelineLine))
        }

        Column(
            Modifier.weight(1f).padding(start = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (isFirst) {
                // ── 강조 카드: 다크 배지 + 세로 배치 ──
                call.category?.let { cat ->
                    Surface(color = Ink, shape = RoundedCornerShape(999.dp)) {
                        Text(cat, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 20.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dateStr, style = TextStyle(fontSize = 12.sp, color = PlaceholderGray))
                        Text(timeStr, style = TextStyle(fontSize = 12.sp, color = PlaceholderGray))
                    }
                }
            } else {
                // ── 일반 카드: 작은 파란 배지 + 가로 배치, 우측 날짜 ──
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        call.category?.let { cat ->
                            Surface(color = ChipBlueBg, shape = RoundedCornerShape(999.dp)) {
                                Text(cat, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = TextStyle(fontSize = 11.sp, color = ChipBlueFg))
                            }
                        }
                        Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink), maxLines = 1)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(dateStr, style = TextStyle(fontSize = 11.sp, color = PlaceholderGray))
                        Text(timeStr, style = TextStyle(fontSize = 11.sp, color = PlaceholderGray))
                    }
                }
            }

            // 본문
            if (!body.isNullOrBlank()) {
                Text(body, style = TextStyle(
                    fontSize = if (isFirst) 12.sp else 11.sp,
                    color = PhoneGray,
                    lineHeight = if (isFirst) 16.sp else 17.sp,
                ))
            }

            // 키워드 칩 (초록)
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    chips.forEach { kw ->
                        Surface(color = ChipGreenBg, shape = RoundedCornerShape(999.dp)) {
                            Text(kw, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = TextStyle(fontSize = 11.sp, color = ChipGreenFg))
                        }
                    }
                }
            }

            // 메모 (사용자 작성)
            if (memo.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFFF4F4F6), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("메모", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LinkBlue))
                    Text(memo, style = TextStyle(fontSize = 11.sp, color = Ink, lineHeight = 16.sp))
                }
            }

            // 사진 썸네일
            if (photos.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    photos.take(3).forEach { url ->
                        Box(
                            Modifier.size(100.dp).clip(RoundedCornerShape(14.dp))
                                .background(PhotoBg).clickable { onEditClick() },
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "통화 사진",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // 메모 / 이미지 추가·편집
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEditClick() }) {
                Text(
                    if (hasUserContent) "메모 / 이미지 편집하기" else "메모 / 이미지 추가하기+",
                    style = TextStyle(fontSize = 12.sp, color = LinkBlue),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────
// 유틸
// ─────────────────────────────────────────────────────

/** 요약을 제목 + 본문으로 분리. 첫 줄(or 짧은 요약)을 제목으로. */
private fun callTitleAndBody(call: Call): Pair<String, String?> {
    val s = call.summary?.trim().orEmpty()
    if (s.isEmpty()) return (call.category ?: "통화") to null
    val parts = s.split("\n").map { it.trim() }.filter { it.isNotBlank() }
    return if (parts.size >= 2) {
        parts.first().take(40) to parts.drop(1).joinToString(" ")
    } else {
        if (s.length <= 24) s to null else s.take(20) to s
    }
}

/** createdAt → (날짜 "yyyy.MM.dd", 시간 "HH:mm") */
private fun formatDateTime(createdAt: String?): Pair<String, String> {
    if (createdAt.isNullOrBlank()) return "" to ""
    val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (fmt in fmts) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val date = sdf.parse(createdAt) ?: continue
            val tz = TimeZone.getDefault()
            val d = SimpleDateFormat("yyyy.MM.dd", Locale.KOREAN).apply { timeZone = tz }.format(date)
            val t = SimpleDateFormat("HH:mm", Locale.KOREAN).apply { timeZone = tz }.format(date)
            return d to t
        } catch (_: Exception) {}
    }
    return createdAt to ""
}
