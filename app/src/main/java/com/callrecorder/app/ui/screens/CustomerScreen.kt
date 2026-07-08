package com.callrecorder.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.callrecorder.app.R
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CustomerAnalysis
import com.callrecorder.app.data.model.CustomerHistoryItem
import com.callrecorder.app.data.model.CustomerHistoryPhoto
import com.callrecorder.app.data.model.CustomerProfile
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.keywordsList
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.util.PhotoUtils
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/* ── 색상: FIANO 0705 디자인 시스템 ── */
private val ScreenBg   = AppColors.DeepBrown900
private val SheetBg    = Color(0xFFFFFFFF)
private val TabOffBg   = Color(0xFFEEEEEE)
private val Ink        = AppColors.DeepBrown900
private val TitleInk   = AppColors.DeepBrown950
private val SubInk     = AppColors.DeepBrown700
private val AccentBlue = AppColors.SignalRed500
private val PlaceholderGray = AppColors.DeepBrown500
private val LabelGray  = AppColors.DeepBrown500
private val Divider    = AppColors.DeepBrown200
private val AvatarBg    = AppColors.DeepBrown50
private val AvatarFg    = AppColors.DeepBrown700
private val PhoneGray   = AppColors.DeepBrown600
private val MutedText   = AppColors.DeepBrown400
private val DetailLinkRed = AppColors.SignalRed700
private val AiSummaryBg = AppColors.SignalRed50

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
fun CustomerScreen(
    vm: CustomerViewModel = viewModel(),
    onCallDetailClick: (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    val state by vm.state.collectAsState()
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CustFilter.ALL) }
    var selectedCustomer by remember { mutableStateOf<CustomerUiItem?>(null) }

    if (selectedCustomer != null) {
        CustomerDetailScreen(
            customer = selectedCustomer!!,
            vm = vm,
            onBack = { selectedCustomer = null },
            onCallDetailClick = onCallDetailClick,
        )
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
                FianoListHeroHeader(
                    title = "고객 데이터를 정리해두었어요.",
                    searchText = searchText,
                    onSearchTextChange = { searchText = it },
                    onNotificationClick = onNotificationClick,
                    hasNotification = hasNotification,
                )
            }

            // ═══ 흰 시트 헤더 (라운드) + 필터 칩 ═══
            item {
                Surface(
                    color = SheetBg,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GradeFilterChip("전체$totalCount", filter == CustFilter.ALL) { filter = CustFilter.ALL }
                        GradeFilterChip("VIP$vipCount", filter == CustFilter.VIP) { filter = CustFilter.VIP }
                        GradeFilterChip("단골$regCount", filter == CustFilter.REGULAR) { filter = CustFilter.REGULAR }
                        GradeFilterChip("일반$normalCount", filter == CustFilter.NORMAL) { filter = CustFilter.NORMAL }
                        GradeFilterChip("신규$newCount", filter == CustFilter.NEW) { filter = CustFilter.NEW }
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
                            Text(displayName, style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = TitleInk))
                            Image(
                                painter = painterResource(R.drawable.customer_icon_call_count),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Text("${customer.callCount}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (customer.isPinned) {
                                Text("★", style = TextStyle(fontSize = 12.sp, color = Ink))
                            }
                            Surface(color = badgeBg, shape = RoundedCornerShape(999.dp)) {
                                Text(badgeLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = TextStyle(fontSize = 10.sp, color = badgeFg))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(customer.phone, style = TextStyle(fontSize = 14.sp, color = PhoneGray))
                    if (!aiLine.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Image(
                                painter = painterResource(R.drawable.customer_icon_timeline_marker),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Text(aiLine, style = TextStyle(fontSize = 13.sp, color = Ink, lineHeight = 20.sp), maxLines = 2)
                        }
                    }
                }
            }
            HorizontalDivider(color = Divider, thickness = 0.7.dp)
        }
    }
}

// ─────────────────────────────────────────────────────
// 고객 상세 (탭: 고객 정보 / 히스토리)
// ─────────────────────────────────────────────────────
private enum class CustDetailTab { ANALYSIS, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customer: CustomerUiItem,
    vm: CustomerViewModel,
    onBack: () -> Unit,
    onCallDetailClick: (String) -> Unit = {},
) {
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
    Scaffold(containerColor = ScreenBg) { padding ->
        Column(Modifier.fillMaxSize().background(ScreenBg)
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {

            // 상단 바
            Row(
                Modifier.fillMaxWidth()
                    .height(60.dp)
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(R.drawable.detail_icon_back),
                        contentDescription = "뒤로",
                        modifier = Modifier.size(32.dp).clickable { onBack() },
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("고객 상세", style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = Color.White))
                }
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.call_icon_alarm),
                        contentDescription = "알림",
                        modifier = Modifier.size(32.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .height(184.dp)
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(AppColors.DeepBrown400), contentAlignment = Alignment.Center) {
                        Text(displayName.take(1), style = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(displayName, style = TextStyle(fontSize = 20.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Color.White))
                                Surface(color = Color.White, shape = RoundedCornerShape(999.dp)) {
                                    Text(badgeLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = Ink))
                                }
                            }
                        }
                        Text(customer.phone, style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, color = AppColors.DeepBrown300))
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("✦ AI 고객 분석", style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                    val text = detail.analysis?.analysis ?: customer.lastSummary
                    Text(
                        if (!text.isNullOrBlank()) text else "통화가 쌓이면 고객 분석을 자동으로 정리해드려요.",
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = AppColors.DeepBrown300),
                        maxLines = 2,
                    )
                }
            }

            // 탭
            CustomerDetailTabs(
                selectedTab = tab,
                onAnalysisClick = { tab = CustDetailTab.ANALYSIS },
                onHistoryClick = { tab = CustDetailTab.HISTORY },
            )

            // 본문
            Column(
                Modifier
                    .fillMaxSize()
                    .background(SheetBg)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 24.dp, bottom = 80.dp),
            ) {
                when (tab) {
                    CustDetailTab.ANALYSIS -> AnalysisTab(customer = customer, detail = detail, vm = vm)
                    CustDetailTab.HISTORY -> Column(Modifier.padding(horizontal = 16.dp)) {
                        HistoryTab(
                            customer = customer,
                            detail = detail,
                            onEditCall = { editingCall = it },
                            onCallDetailClick = onCallDetailClick,
                        )
                    }
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
private fun CustomerDetailTabs(
    selectedTab: CustDetailTab,
    onAnalysisClick: () -> Unit,
    onHistoryClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))) {
        CustTabButton("고객 정보", selectedTab == CustDetailTab.ANALYSIS, Modifier.weight(1f), onAnalysisClick)
        CustTabButton("히스토리", selectedTab == CustDetailTab.HISTORY, Modifier.weight(1f), onHistoryClick)
    }
}

@Composable
private fun CustTabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) Color.White else TabOffBg)
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink),
            )
        }
    }
}

/* ── 고객 정보 탭: 주요 정보 + 관련 이미지 + 추가 정보 ── */
@Composable
private fun AnalysisTab(customer: CustomerUiItem, detail: CustomerDetailState, vm: CustomerViewModel) {
    val profile = detail.profile
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf(false) }
    var imageEditing by remember { mutableStateOf(false) }
    var additionalInfoEditing by remember { mutableStateOf(false) }
    var email by remember(profile) { mutableStateOf(profile?.email ?: "") }
    var tendency by remember(profile) { mutableStateOf(profile?.tendency ?: "") }
    var medical by remember(profile) { mutableStateOf(profile?.medical ?: "") }
    var special by remember(profile) { mutableStateOf(profile?.specialNotes ?: "") }
    val relatedPhotos = detail.manualPhotos
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                PhotoUtils.uriToCompressedBytes(context, uri)
            } ?: return@launch
            vm.uploadManualPhoto(
                customer = customer,
                fileName = "customer_${System.currentTimeMillis()}.jpg",
                bytes = bytes,
            )
        }
    }
    val additionalInfoImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                PhotoUtils.uriToCompressedBytes(context, uri)
            } ?: return@launch
            vm.uploadAdditionalInfoPhoto(
                customer = customer,
                fileName = "customer_info_${System.currentTimeMillis()}.jpg",
                bytes = bytes,
            )
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("주요 정보", style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink))
        Text(
            if (editing) "저장" else "편집",
            style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = PhoneGray),
            modifier = Modifier.clickable {
                if (editing) {
                    vm.saveProfile(customer.phone, email, tendency, medical, special)
                }
                editing = !editing
            },
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CustomerInfoField("이메일", email, editing, onChange = { email = it }, onClear = { email = "" })
        CustomerInfoField("고객성향", tendency, editing, onChange = { tendency = it }, onClear = { tendency = "" })
        CustomerInfoField("병력", medical, editing, onChange = { medical = it }, onClear = { medical = "" })
        CustomerInfoField("특이사항", special, editing, onChange = { special = it }, onClear = { special = "" })
    }

    detail.saveMessage?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = TextStyle(fontSize = 12.sp, color = SubInk))
    }

    RelatedImagesSection(
        photos = relatedPhotos,
        editing = imageEditing,
        saving = detail.saving,
        onToggleEditing = { imageEditing = !imageEditing },
        onAddClick = { imagePicker.launch("image/*") },
        onDeleteClick = { photo -> vm.deleteManualPhoto(customer, photo) },
    )

    ManualMemoHistoryBlock(
        items = detail.manualHistory,
        editing = additionalInfoEditing,
        saving = detail.saving,
        onToggleEditing = { additionalInfoEditing = !additionalInfoEditing },
        onAddImageClick = { additionalInfoImagePicker.launch("image/*") },
    )
}

@Composable
private fun RelatedImagesSection(
    photos: List<CustomerManualPhoto>,
    editing: Boolean,
    saving: Boolean,
    onToggleEditing: () -> Unit,
    onAddClick: () -> Unit,
    onDeleteClick: (CustomerManualPhoto) -> Unit,
) {
    val inspectionMode = LocalInspectionMode.current

    Spacer(Modifier.height(24.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "관련 이미지",
            style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink),
        )
        Text(
            if (editing) "저장" else "편집",
            style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = PhoneGray),
            modifier = Modifier.clickable { onToggleEditing() },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photos.forEach { photo ->
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEFEFEF)),
            ) {
                CustomerImageThumbnail(
                    url = photo.url,
                    contentDescription = "관련 이미지",
                    modifier = Modifier.fillMaxSize(),
                )
                if (editing) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC101418))
                            .clickable { onDeleteClick(photo) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (inspectionMode) {
                            Text("x", style = TextStyle(fontSize = 14.sp, lineHeight = 14.sp, color = Color.White))
                        } else {
                            Image(
                                painter = painterResource(R.drawable.customer_icon_cancel),
                                contentDescription = "이미지 삭제",
                                modifier = Modifier.size(18.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }
            }
        }

        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .size(width = 212.dp, height = 100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEFEFEF)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "등록된 이미지가 없어요",
                    style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = PlaceholderGray),
                )
            }
        }
    }

    if (editing) {
        Spacer(Modifier.height(8.dp))
        FigmaAddFieldRow(
            text = if (saving) "처리 중" else "이미지 추가",
            enabled = !saving,
            onClick = onAddClick,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualMemoHistoryBlock(
    items: List<CustomerHistoryItem>,
    editing: Boolean,
    saving: Boolean,
    onToggleEditing: () -> Unit,
    onAddImageClick: () -> Unit,
) {
    val memos = items.filter { !it.memo.isNullOrBlank() || it.photos.isNotEmpty() }.take(5)

    Spacer(Modifier.height(24.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "추가 정보",
            style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink),
        )
        Text(
            if (editing) "저장" else "편집",
            style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = PhoneGray),
            modifier = Modifier.clickable { onToggleEditing() },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (memos.isEmpty() && !editing) {
            AdditionalInfoTextRow("추가된 정보가 없어요", color = PlaceholderGray)
        }
        memos.forEach { item ->
            Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Column {
                    item.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                        AdditionalInfoTextRow(memo)
                    }
                    val photos = item.photos.mapNotNull { it.url }
                    if (photos.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            photos.forEach { url ->
                                CustomerImageThumbnail(
                                    url = url,
                                    contentDescription = "추가정보 이미지",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(PhotoBg),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (editing) {
            FigmaAddFieldRow(
                text = if (saving) "처리 중" else "이미지 추가",
                enabled = !saving,
                onClick = onAddImageClick,
                horizontalPadding = 0.dp,
            )
        }
    }
}

@Composable
private fun AdditionalInfoTextRow(
    text: String,
    color: Color = Ink,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, color = color),
        )
    }
}

@Composable
private fun FigmaAddFieldRow(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    horizontalPadding: Dp = 24.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = horizontalPadding)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.customer_icon_add_field_figma),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Text(
            text,
            style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = PhoneGray),
        )
    }
}

@Composable
private fun CustomerInfoField(
    label: String,
    value: String,
    editing: Boolean,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (editing) 22.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(56.dp),
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = PhoneGray),
        )
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, color = Ink),
                cursorBrush = SolidColor(Ink),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isBlank()) Text("내용", style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, color = PlaceholderGray))
                        inner()
                    }
                },
            )
            Image(
                painter = painterResource(R.drawable.customer_icon_cancel),
                contentDescription = "내용 지우기",
                modifier = Modifier.size(22.dp).clickable { onClear() },
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                value.ifBlank { "-" },
                modifier = Modifier.weight(1f),
                style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, color = Ink),
            )
        }
    }
}

/* ── 히스토리 탭: 통화 타임라인 (시안 반영) ── */
@Composable
private fun HistoryTab(
    customer: CustomerUiItem,
    detail: CustomerDetailState,
    onEditCall: (Call) -> Unit,
    onCallDetailClick: (String) -> Unit,
) {
    val reservationCount = customer.calls.count { it.category == "예약" }
    val relatedSchedule = customer.calls.count {
        val info = it.extractedInfoOrNull(); info?.date != null
    }
    MetricsRow(
        totalCalls = customer.callCount,
        reservationCount = reservationCount,
        relatedSchedule = relatedSchedule,
    )
    Spacer(Modifier.height(24.dp))

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
            onCallDetailClick = { onCallDetailClick(call.id) },
        )
    }
}

@Composable
private fun MetricsRow(totalCalls: Int, reservationCount: Int, relatedSchedule: Int) {
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetricItem("${totalCalls}회", "총 통화")
        Box(Modifier.width(1.dp).height(20.dp).background(AppColors.DeepBrown200))
        MetricItem("${reservationCount}건", "예약 완료")
        Box(Modifier.width(1.dp).height(20.dp).background(AppColors.DeepBrown200))
        MetricItem("${relatedSchedule}건", "관련 일정")
    }
}

@Composable
private fun MetricItem(value: String, label: String) {
    Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink))
        Text(label, style = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, color = MutedText))
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
    onCallDetailClick: () -> Unit,
) {
    val (title, body) = callTitleAndBody(call)
    val (dateStr, timeStr) = formatDateTime(call.createdAt)
    val chips = call.keywordsList().filter { it.isNotBlank() }.take(4)
    val photos = note?.photos.orEmpty().map { it.url }
    val memo = note?.memo?.trim().orEmpty()
    val hasUserContent = memo.isNotBlank() || photos.isNotEmpty()

    val lineColor = TimelineLine
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 270.dp)
            .drawBehind {
                if (!isLast) {
                    val x = 12.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 24.dp.toPx()),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            },
    ) {
        // 타임라인 점 + 선
        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.customer_icon_dot_as),
                    contentDescription = null,
                    modifier = Modifier.size(8.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Column(
            Modifier.weight(1f).padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            call.category?.let { cat ->
                Surface(color = Ink, shape = RoundedCornerShape(999.dp)) {
                    Text(cat, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = Color.White))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dateStr, style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink))
                    Text(
                        "통화상세",
                        style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = DetailLinkRed),
                        modifier = Modifier.clickable { onCallDetailClick() },
                    )
                }
                if (timeStr.isNotBlank()) {
                    Text(timeStr, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = PlaceholderGray))
                }
            }

            // 본문
            Text(
                body ?: title,
                style = TextStyle(fontSize = 12.sp, color = PhoneGray, lineHeight = 16.sp),
                maxLines = 2,
            )

            // 키워드 칩 (초록)
            if (chips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    chips.forEach { kw ->
                        Surface(color = ChipGreenBg, shape = RoundedCornerShape(999.dp)) {
                            Text(kw, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, color = PlaceholderGray))
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
                            CustomerImageThumbnail(
                                url = url,
                                contentDescription = "통화 사진",
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // 메모 / 이미지 추가·편집
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onEditClick() }) {
                Text(
                    if (hasUserContent) "+ 수정하기" else "+ 수정하기",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = DetailLinkRed),
                )
            }
        }
    }
}

@Composable
private fun CustomerImageThumbnail(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val inspectionMode = LocalInspectionMode.current
    if (inspectionMode || url.isBlank()) {
        Box(
            modifier = modifier.background(Color(0xFFEFEFEF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "이미지",
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = PlaceholderGray),
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
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

@Preview(name = "Customer Detail Full - Info", widthDp = 360, heightDp = 918, showBackground = false)
@Composable
private fun CustomerDetailFullAnalysisPreview() {
    CustomerDetailFullPreview(tab = CustDetailTab.ANALYSIS)
}

@Preview(name = "Customer Detail Full - History", widthDp = 360, heightDp = 918, showBackground = false)
@Composable
private fun CustomerDetailFullHistoryPreview() {
    CustomerDetailFullPreview(tab = CustDetailTab.HISTORY)
}

@Preview(name = "Customer Detail Related Images", widthDp = 360, heightDp = 918, showBackground = false)
@Composable
private fun CustomerDetailRelatedImagesPreview() {
    CustomerDetailFullPreview(tab = CustDetailTab.ANALYSIS)
}

@Composable
private fun CustomerDetailFullPreview(tab: CustDetailTab) {
    val customer = previewCustomer()
    val detail = previewCustomerDetail()
    val displayName = customer.name ?: customer.phone
    val (_, _, badgeLabel) = gradeBadgeColors(customer.grade)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(R.drawable.detail_icon_back),
                    contentDescription = "뒤로",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(8.dp))
                Text("고객 상세", style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = Color.White))
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.call_icon_alarm),
                    contentDescription = "알림",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .height(184.dp)
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(AppColors.DeepBrown400), contentAlignment = Alignment.Center) {
                    Text(displayName.take(1), style = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(displayName, style = TextStyle(fontSize = 20.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        Surface(color = Color.White, shape = RoundedCornerShape(999.dp)) {
                            Text(badgeLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, color = Ink))
                        }
                    }
                    Text(customer.phone, style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, color = AppColors.DeepBrown300))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("✦ AI 고객 분석", style = TextStyle(fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                Text(
                    detail.analysis?.analysis ?: "김민준 고객님은 매물 확인과 일정 조율에 관심이 높습니다.",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = AppColors.DeepBrown300),
                    maxLines = 2,
                )
            }
        }

        CustomerDetailTabs(
            selectedTab = tab,
            onAnalysisClick = {},
            onHistoryClick = {},
        )

        Column(
            Modifier
                .fillMaxSize()
                .background(SheetBg)
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 80.dp),
        ) {
            when (tab) {
                CustDetailTab.ANALYSIS -> CustomerDetailAnalysisPreviewContent(detail = detail)
                CustDetailTab.HISTORY -> Column(Modifier.padding(horizontal = 16.dp)) {
                    HistoryTab(
                        customer = customer,
                        detail = detail,
                        onEditCall = {},
                        onCallDetailClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomerDetailAnalysisPreviewContent(detail: CustomerDetailState) {
    val profile = detail.profile ?: CustomerProfile()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("주요 정보", style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink))
        Text("편집", style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = PhoneGray))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CustomerInfoField("이메일", profile.email.orEmpty(), editing = false, onChange = {}, onClear = {})
        CustomerInfoField("고객성향", profile.tendency.orEmpty(), editing = false, onChange = {}, onClear = {})
        CustomerInfoField("병력", profile.medical.orEmpty(), editing = false, onChange = {}, onClear = {})
        CustomerInfoField("특이사항", profile.specialNotes.orEmpty(), editing = false, onChange = {}, onClear = {})
    }

    RelatedImagesSection(
        photos = detail.manualPhotos,
        editing = true,
        saving = false,
        onToggleEditing = {},
        onAddClick = {},
        onDeleteClick = {},
    )

    ManualMemoHistoryBlock(
        items = detail.manualHistory,
        editing = true,
        saving = false,
        onToggleEditing = {},
        onAddImageClick = {},
    )
}

private fun previewCustomer() = CustomerUiItem(
    phone = "010-1234-5678",
    name = "김민준",
    callCount = 8,
    lastCallAt = "2026-07-08T10:30:00",
    lastSummary = "매물 확인과 계약 일정 조율이 필요한 고객입니다.",
    categories = listOf("예약", "문의", "서류"),
    calls = listOf(
        previewCall(
            id = "preview-call-1",
            createdAt = "2026-07-08T10:30:00",
            summary = "매물 확인 일정 조율\n7월 10일 오전 방문 가능 여부와 준비 서류를 문의했습니다.",
            category = "예약",
            keywords = "[\"매물확인\",\"일정조율\",\"서류\"]",
        ),
        previewCall(
            id = "preview-call-2",
            createdAt = "2026-07-06T15:20:00",
            summary = "계약 조건 문의\n보증금 조정 가능성과 입주 가능일을 확인했습니다.",
            category = "문의",
            keywords = "[\"계약조건\",\"보증금\",\"입주일\"]",
        ),
        previewCall(
            id = "preview-call-3",
            createdAt = "2026-07-04T09:10:00",
            summary = "서류 준비 안내\n등기부등본과 신분증 사본 준비를 안내했습니다.",
            category = "서류",
            keywords = "[\"서류\",\"등기부등본\",\"신분증\"]",
        ),
    ),
    grade = CustomerGrade.VIP,
)

private fun previewCustomerDetail() = CustomerDetailState(
    profile = CustomerProfile(
        email = "minjun.kim@example.com",
        tendency = "일정 확정 전 세부 조건을 꼼꼼히 확인",
        medical = "해당 없음",
        specialNotes = "오전 통화를 선호하며 계약서 초안 사전 공유 요청",
    ),
    analysis = CustomerAnalysis(
        analysis = "김민준 고객님은 매물 확인과 일정 조율에 관심이 높고, 계약 전 서류와 조건 확인을 중요하게 봅니다.",
        callCount = 8,
        generatedAt = "2026-07-08T10:40:00",
    ),
    notes = mapOf(
        "preview-call-1" to CustomerCallNote(
            memo = "방문 전 주차 가능 여부를 함께 안내하기",
            photos = listOf(
                CustomerRelatedPhoto("note-photo-1", "preview-call-1", ""),
                CustomerRelatedPhoto("note-photo-2", "preview-call-1", ""),
            ),
        ),
        "preview-call-2" to CustomerCallNote(
            memo = "보증금 조정 가능한 대안 매물 전달",
            photos = emptyList(),
        ),
    ),
    manualHistory = listOf(
        CustomerHistoryItem(
            type = "manual_memo",
            id = "memo-1",
            createdAt = "2026-07-08T11:00:00",
            memo = "매물 요청을 하였고 매물 준비 완료.",
            photos = listOf(
                CustomerHistoryPhoto(id = "memo-photo-1", url = "", caption = "매물 사진"),
                CustomerHistoryPhoto(id = "memo-photo-2", url = "", caption = "서류 사진"),
            ),
        ),
        CustomerHistoryItem(
            type = "manual_memo",
            id = "memo-2",
            createdAt = "2026-07-07T14:00:00",
            memo = "서류는 등기부등본과 계약서 초안 확인 필요.",
            photos = emptyList(),
        ),
    ),
    manualPhotos = listOf(
        CustomerManualPhoto("manual-photo-1", "memo-1", ""),
        CustomerManualPhoto("manual-photo-2", "memo-1", ""),
        CustomerManualPhoto("manual-photo-3", "memo-2", ""),
    ),
)

private fun previewCall(
    id: String,
    createdAt: String,
    summary: String,
    category: String,
    keywords: String,
) = Call(
    id = id,
    callerNumber = "010-1234-5678",
    callerName = "김민준",
    direction = "incoming",
    duration = 240,
    status = "completed",
    createdAt = createdAt,
    summary = summary,
    category = category,
    sentiment = "positive",
    keywords = kotlinx.serialization.json.JsonPrimitive(keywords),
)
