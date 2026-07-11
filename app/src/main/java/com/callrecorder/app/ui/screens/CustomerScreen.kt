package com.callrecorder.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    onCustomerPinnedChanged: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CustFilter.ALL) }
    var selectedCustomer by remember { mutableStateOf<CustomerUiItem?>(null) }
    var importingContact by remember { mutableStateOf(false) }
    var showContactSheet by remember { mutableStateOf(false) }
    var contactChoices by remember { mutableStateOf<List<ContactChoice>>(emptyList()) }
    var selectedContactKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun openContactSheet() {
        importingContact = true
        scope.launch {
            contactChoices = withContext(Dispatchers.IO) { readDeviceContacts(context) }
            selectedContactKeys = emptySet()
            importingContact = false
            if (contactChoices.isEmpty()) {
                Toast.makeText(context, "선택할 연락처가 없어요", Toast.LENGTH_SHORT).show()
            } else {
                showContactSheet = true
            }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            openContactSheet()
        } else {
            Toast.makeText(context, "연락처 권한이 필요해요", Toast.LENGTH_SHORT).show()
        }
    }

    fun startContactImport() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            openContactSheet()
        } else {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    if (selectedCustomer != null) {
        CustomerDetailScreen(
            customer = selectedCustomer!!,
            vm = vm,
            onBack = { selectedCustomer = null },
            onCallDetailClick = onCallDetailClick,
            onPinnedChanged = { updated -> selectedCustomer = updated },
            onPinnedSaved = onCustomerPinnedChanged,
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

    Scaffold(containerColor = SheetBg) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(SheetBg)
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScreenBg),
                ) {
                    Surface(
                        color = SheetBg,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp)) {
                            Row(
                                Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GradeFilterChip("전체$totalCount", filter == CustFilter.ALL) { filter = CustFilter.ALL }
                                GradeFilterChip("VIP$vipCount", filter == CustFilter.VIP) { filter = CustFilter.VIP }
                                GradeFilterChip("단골$regCount", filter == CustFilter.REGULAR) { filter = CustFilter.REGULAR }
                                GradeFilterChip("일반$normalCount", filter == CustFilter.NORMAL) { filter = CustFilter.NORMAL }
                                GradeFilterChip("신규$newCount", filter == CustFilter.NEW) { filter = CustFilter.NEW }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AddCustomerFromContactButton(
                                    loading = importingContact,
                                    onClick = { if (!importingContact) startContactImport() },
                                )
                            }
                        }
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
                    SwipeRevealDeleteBox(
                        onDelete = { vm.deleteCustomer(customer) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .background(SheetBg)
                                .fillMaxWidth(),
                        ) {
                            CustomerCard(customer = customer, onClick = { selectedCustomer = customer })
                        }
                    }
                }
            }
            item { Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) { Spacer(Modifier.height(80.dp)) } }
        }
    }

    if (showContactSheet) {
        ContactMultiSelectBottomSheet(
            contacts = contactChoices,
            selectedKeys = selectedContactKeys,
            onSelectedKeysChange = { selectedContactKeys = it },
            onDismiss = { showContactSheet = false },
            onConfirm = {
                val selected = contactChoices.filter { it.key in selectedContactKeys }
                if (selected.isEmpty()) {
                    Toast.makeText(context, "추가할 연락처를 선택해주세요", Toast.LENGTH_SHORT).show()
                    return@ContactMultiSelectBottomSheet
                }
                showContactSheet = false
                importingContact = true
                vm.addCustomersFromContacts(
                    contacts = selected.map { CustomerContactInput(name = it.name, phone = it.phone) },
                ) { success, message ->
                    importingContact = false
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    if (success) {
                        searchText = TextFieldValue("")
                        filter = CustFilter.ALL
                    }
                }
            },
        )
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

@Composable
private fun AddCustomerFromContactButton(
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        color = Ink,
        shape = RoundedCornerShape(999.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 40.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text("+", style = TextStyle(fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
            }
            Text(
                if (loading) "추가 중" else "고객 추가",
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactMultiSelectBottomSheet(
    contacts: List<ContactChoice>,
    selectedKeys: Set<String>,
    onSelectedKeysChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetMaxWidth = Dp.Unspecified,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text(
                "연락처에서 고객 추가",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${selectedKeys.size}명 선택됨",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = MutedText),
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(contacts, key = { it.key }) { contact ->
                    val selected = contact.key in selectedKeys
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectedKeysChange(
                                    if (selected) selectedKeys - contact.key else selectedKeys + contact.key,
                                )
                            }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = {
                                onSelectedKeysChange(
                                    if (selected) selectedKeys - contact.key else selectedKeys + contact.key,
                                )
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Ink,
                                uncheckedColor = AppColors.DeepBrown300,
                                checkmarkColor = Color.White,
                            ),
                        )
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(AvatarBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                contact.name.take(1).ifBlank { "#" },
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AvatarFg),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                contact.name.ifBlank { contact.phone },
                                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink),
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                contact.phone,
                                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = PhoneGray),
                                maxLines = 1,
                            )
                        }
                    }
                    HorizontalDivider(color = Divider, thickness = 0.7.dp)
                }
            }
            ContactSheetButtonBar(
                onCancel = onDismiss,
                onConfirm = onConfirm,
                confirmEnabled = selectedKeys.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun ContactSheetButtonBar(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FianoSheetActionButton(
                label = "취소",
                primary = false,
                enabled = true,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            FianoSheetActionButton(
                label = "추가",
                primary = true,
                enabled = confirmEnabled,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FianoSheetActionButton(
    label: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = when {
            primary && enabled -> Ink
            primary -> AppColors.DeepBrown200
            else -> Color.White
        },
        shape = RoundedCornerShape(12.dp),
        border = if (primary) null else androidx.compose.foundation.BorderStroke(1.dp, Divider),
        modifier = modifier.height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (primary) Color.White else Ink,
                ),
            )
        }
    }
}

private data class ContactChoice(
    val key: String,
    val name: String,
    val phone: String,
)

private fun readDeviceContacts(context: Context): List<ContactChoice> {
    val choices = mutableListOf<ContactChoice>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIndex).orEmpty()
            val rawPhone = cursor.getString(phoneIndex).orEmpty()
            val normalized = rawPhone.filter { it.isDigit() || it == '+' }
            val key = normalized.filter { it.isDigit() }
            if (key.length >= 7) {
                choices += ContactChoice(
                    key = key,
                    name = name,
                    phone = normalized,
                )
            }
        }
    }
    return choices.distinctBy { it.key }
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
    onPinnedChanged: (CustomerUiItem) -> Unit = {},
    onPinnedSaved: () -> Unit = {},
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
                FianoHeaderAlarmButton()
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
                            CustomerBookmarkButton(
                                pinned = customer.isPinned,
                                onClick = {
                                    val updated = customer.copy(isPinned = !customer.isPinned)
                                    onPinnedChanged(updated)
                                    vm.setPinned(customer, updated.isPinned, onSuccess = onPinnedSaved)
                                },
                            )
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
                    val text = remember(detail.profile, detail.manualHistory, detail.analysis, customer.lastSummary) {
                        customerComprehensiveAnalysis(detail, customer)
                    }
                    Text(
                        text,
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
                    .imePadding()
                    .padding(top = 24.dp, bottom = 120.dp),
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
private fun CustomerBookmarkButton(
    pinned: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(if (pinned) R.drawable.icon_customer_bookmark_on else R.drawable.icon_customer_bookmark_off),
            contentDescription = if (pinned) "주요 관리 고객 해제" else "주요 관리 고객 등록",
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit,
        )
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
private data class CustomerInfoEditRow(
    val id: String,
    val label: String,
    val value: String,
    val removable: Boolean,
)

@Composable
private fun AnalysisTab(customer: CustomerUiItem, detail: CustomerDetailState, vm: CustomerViewModel) {
    val profile = detail.profile
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf(false) }
    var imageEditing by remember { mutableStateOf(false) }
    var additionalInfoEditing by remember { mutableStateOf(false) }
    val customFieldMap = remember(profile) { profile.customFieldMap() }
    var infoRows by remember(profile) {
        mutableStateOf(
            buildCustomerInfoRows(profile, customFieldMap)
        )
    }
    var additionalMemo by remember(detail.manualHistory) {
        mutableStateOf(detail.manualHistory.combinedAdditionalMemo())
    }
    val relatedPhotos = detail.manualPhotos
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val localUrl = withContext(Dispatchers.IO) {
                PhotoUtils.copyUriToCustomerImageFile(
                    context = context,
                    uri = uri,
                    fileName = "customer_${System.currentTimeMillis()}.jpg",
                )
            }
            if (localUrl == null) {
                Toast.makeText(context, "이미지를 불러올 수 없어요", Toast.LENGTH_SHORT).show()
                return@launch
            }
            vm.addLocalRelatedPhoto(customer, localUrl)
            val bytes = withContext(Dispatchers.IO) {
                PhotoUtils.uriToCompressedBytes(context, uri)
            }
            if (bytes == null) {
                Toast.makeText(context, "이미지 업로드용 변환에 실패했어요", Toast.LENGTH_SHORT).show()
                return@launch
            }
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
                    val byId = infoRows.associateBy { it.id }
                    val extraFields = infoRows
                        .filter { it.removable && it.label.isNotBlank() }
                        .associate { it.label to it.value }
                    val customFields = buildMap {
                        put("label_email", byId["email"]?.label.orEmpty().ifBlank { "이메일" })
                        put("label_tendency", byId["tendency"]?.label.orEmpty().ifBlank { "고객성향" })
                        put("label_special", byId["special"]?.label.orEmpty().ifBlank { "특이사항" })
                        putAll(extraFields)
                    }
                    vm.saveProfile(
                        phone = customer.phone,
                        email = byId["email"]?.value.orEmpty(),
                        tendency = byId["tendency"]?.value.orEmpty(),
                        medical = "",
                        specialNotes = byId["special"]?.value.orEmpty(),
                        customFields = customFields,
                    )
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
        infoRows.forEachIndexed { index, row ->
            CustomerInfoField(
                label = row.label,
                value = row.value,
                editing = editing,
                removable = row.removable,
                onLabelChange = { label ->
                    infoRows = infoRows.toMutableList().also { it[index] = it[index].copy(label = label) }
                },
                onChange = { value ->
                    infoRows = infoRows.toMutableList().also { it[index] = it[index].copy(value = value) }
                },
                onClear = {
                    infoRows = infoRows.toMutableList().also { it[index] = it[index].copy(value = "") }
                },
                onRemove = {
                    infoRows = infoRows.filterIndexed { rowIndex, _ -> rowIndex != index }
                },
            )
        }
        if (editing) {
            FigmaAddFieldRow(
                text = "필드추가",
                enabled = true,
                onClick = {
                    infoRows = infoRows + CustomerInfoEditRow(
                        id = "custom_${System.currentTimeMillis()}",
                        label = "",
                        value = "",
                        removable = true,
                    )
                },
                horizontalPadding = 0.dp,
            )
        }
    }

    SectionStatusMessage(
        message = detail.saveMessage.takeIf { detail.saveMessageSection == CustomerDetailMessageSection.PROFILE },
    )

    RelatedImagesSection(
        photos = relatedPhotos,
        editing = imageEditing,
        saving = detail.saving,
        message = detail.saveMessage.takeIf { detail.saveMessageSection == CustomerDetailMessageSection.RELATED_IMAGES },
        onToggleEditing = { imageEditing = !imageEditing },
        onAddClick = { imagePicker.launch("image/*") },
        onDeleteClick = { photo -> vm.deleteManualPhoto(customer, photo) },
    )

    ManualMemoHistoryBlock(
        items = detail.manualHistory,
        editing = additionalInfoEditing,
        saving = detail.saving,
        onToggleEditing = { additionalInfoEditing = !additionalInfoEditing },
        additionalMemo = additionalMemo,
        onAdditionalMemoChange = { additionalMemo = it },
        onSaveMemo = {
            vm.saveAdditionalInfoMemo(customer, additionalMemo)
            additionalInfoEditing = false
        },
        message = detail.saveMessage.takeIf { detail.saveMessageSection == CustomerDetailMessageSection.ADDITIONAL_INFO },
        onAddImageClick = { additionalInfoImagePicker.launch("image/*") },
        onDeleteImageClick = { photo -> vm.deleteAdditionalInfoPhoto(customer, photo) },
    )
}

private fun List<CustomerHistoryItem>.combinedAdditionalMemo(): String {
    val primaryMemo = firstOrNull { it.id == "local_memo_primary" }
        ?.memo
        .cleanCustomerAnalysisPart()
        .takeIf(String::isNotBlank)
    if (primaryMemo != null) return primaryMemo

    return mapNotNull { it.memo.cleanCustomerAnalysisPart().takeIf(String::isNotBlank) }
        .distinct()
        .joinToString("\n")
}

data class AdditionalInfoPhotoItem(
    val memoId: String,
    val photoId: String,
    val url: String,
)

@Composable
private fun SectionStatusMessage(message: String?) {
    if (message.isNullOrBlank()) return
    Text(
        message,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        style = TextStyle(fontSize = 12.sp, color = SubInk),
    )
}

private fun buildCustomerInfoRows(
    profile: CustomerProfile?,
    customFields: Map<String, String>,
): List<CustomerInfoEditRow> {
    val defaultRows = listOf(
        CustomerInfoEditRow("email", customFields["label_email"] ?: "이메일", profile?.email.orEmpty(), removable = false),
        CustomerInfoEditRow("tendency", customFields["label_tendency"] ?: "고객성향", profile?.tendency.orEmpty(), removable = false),
        CustomerInfoEditRow("special", customFields["label_special"] ?: "특이사항", profile?.specialNotes.orEmpty(), removable = false),
    )
    val extraRows = customFields
        .filterKeys { !it.startsWith("label_") }
        .map { (label, value) ->
            CustomerInfoEditRow(
                id = "custom_$label",
                label = label,
                value = value,
                removable = true,
            )
        }
    return defaultRows + extraRows
}

private fun CustomerProfile?.customFieldMap(): Map<String, String> {
    val objectValue = this?.customFields as? JsonObject ?: return emptyMap()
    return objectValue.mapValues { (_, value) ->
        value.jsonPrimitive.contentOrNull.orEmpty()
    }
}

private fun customerComprehensiveAnalysis(detail: CustomerDetailState, customer: CustomerUiItem): String {
    val profile = detail.profile
    val customFields = profile.customFieldMap()
    val serverAnalysis = detail.analysis?.analysis
        .cleanCustomerAnalysisPart()
        .stripCustomerAnalysisName(customer)
    val latestSummary = customer.lastSummary
        .cleanCustomerAnalysisPart()
        .stripCustomerAnalysisName(customer)
    val supportingSignals = buildList {
        add(profile?.tendency.cleanCustomerAnalysisPart())
        add(profile?.specialNotes.cleanCustomerAnalysisPart())
        addAll(
            customFields
                .filterKeys { !it.startsWith("label_") }
                .filterValues { it.cleanCustomerAnalysisPart().isNotBlank() }
                .values
                .map { it.cleanCustomerAnalysisPart() }
        )
        addAll(
            detail.manualHistory
                .mapNotNull { it.memo?.cleanCustomerAnalysisPart()?.takeIf(String::isNotBlank) }
        )
        addAll(customer.calls.analysisContextParts())
        add(latestSummary)
    }
        .map { it.stripCustomerAnalysisName(customer) }
        .filter { it.isNotBlank() }
        .distinct()

    if (serverAnalysis.isNotBlank()) {
        val support = supportingSignals
            .filterNot { serverAnalysis.contains(it) || it.contains(serverAnalysis) }
            .firstOrNull()
        return if (support.isNullOrBlank() || serverAnalysis.length >= 72) {
            serverAnalysis.limitCustomerAnalysisText()
        } else {
            "$serverAnalysis ${support.toFollowUpSentence()}".limitCustomerAnalysisText()
        }
    }

    if (latestSummary.isNotBlank()) {
        return latestSummary.limitCustomerAnalysisText()
    }

    if (supportingSignals.isNotEmpty()) {
        return supportingSignals.first().toFollowUpSentence().limitCustomerAnalysisText()
    }

    return "누적통화 0건의 고객입니다. 지금부터 고객님의 히스토리를 정리할 수 있어요."
}

private fun List<Call>.analysisContextParts(): List<String> {
    val analyzedCalls = filter { !it.summary.isNullOrBlank() || !it.category.isNullOrBlank() }
    val recentSummary = analyzedCalls
        .sortedByDescending { it.createdAt.orEmpty() }
        .mapNotNull { it.summary.cleanCustomerAnalysisPart().takeIf(String::isNotBlank) }
        .firstOrNull()
    val scheduleCategories = analyzedCalls
        .mapNotNull { it.category.cleanCustomerAnalysisPart().takeIf(String::isNotBlank) }
        .filter { it.contains("예약") || it.contains("일정") || it.contains("방문") || it.contains("계약") }
        .distinct()
        .take(2)
    val actionCount = analyzedCalls.count { it.actionRequired == 1 }

    return buildList {
        if (!recentSummary.isNullOrBlank()) add(recentSummary)
        if (scheduleCategories.isNotEmpty()) add("${scheduleCategories.joinToString(", ")} 관련 일정이 있습니다")
        if (actionCount > 0) add("후속 확인이 필요한 통화가 ${actionCount}건 있습니다")
    }
}

private fun String?.cleanCustomerAnalysisPart(): String =
    this.orEmpty()
        .replace("\n", " ")
        .replace(Regex("^\\s*\\[[^\\]]+\\]\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.stripCustomerAnalysisName(customer: CustomerUiItem): String {
    val names = listOfNotNull(customer.name, customer.phone)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    return names.fold(this) { text, name ->
        text
            .replace(Regex("^${Regex.escape(name)}\\s*(고객님|고객|님)?(은|는|이|가)?\\s*"), "")
            .replace(Regex("\\b${Regex.escape(name)}\\s*(고객님|고객|님)?\\b"), "")
    }
        .replace(Regex("\\s+"), " ")
        .trimStart(',', '.', ' ')
        .trim()
}

private fun String.toFollowUpSentence(): String {
    val text = trimEnd('.', ',', ' ')
    return when {
        text.endsWith("필요") || text.endsWith("필요함") -> "$text."
        text.contains("일정") || text.contains("예약") || text.contains("방문") || text.contains("계약") ->
            "$text 후속 일정을 함께 챙겨야 합니다."
        else -> "$text 내용을 참고해 다음 응대 방향을 정리해야 합니다."
    }
}

private fun String.limitCustomerAnalysisText(maxLength: Int = 96): String =
    if (length <= maxLength) this else take(maxLength).trimEnd() + "..."

@Composable
private fun RelatedImagesSection(
    photos: List<CustomerManualPhoto>,
    editing: Boolean,
    saving: Boolean,
    message: String?,
    onToggleEditing: () -> Unit,
    onAddClick: () -> Unit,
    onDeleteClick: (CustomerManualPhoto) -> Unit,
) {
    val inspectionMode = LocalInspectionMode.current
    var fullImageUrl by remember { mutableStateOf<String?>(null) }

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
                    .background(Color(0xFFEFEFEF))
                    .clickable { fullImageUrl = photo.url },
            ) {
                CustomerImageThumbnail(
                    url = photo.url,
                    contentDescription = "관련 이미지",
                    modifier = Modifier.fillMaxSize(),
                )
                if (editing) {
                    CustomerImageDeleteButton(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        inspectionMode = inspectionMode,
                        onClick = { onDeleteClick(photo) },
                    )
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

    SectionStatusMessage(message)

    if (editing) {
        Spacer(Modifier.height(8.dp))
        FigmaAddFieldRow(
            text = "이미지 추가",
            enabled = !saving,
            onClick = onAddClick,
        )
    }

    FullImageDialog(
        url = fullImageUrl,
        onDismiss = { fullImageUrl = null },
    )
}

@Composable
private fun CustomerImageDeleteButton(
    modifier: Modifier = Modifier,
    inspectionMode: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (inspectionMode) {
            Text("x", style = TextStyle(fontSize = 14.sp, lineHeight = 14.sp, color = Ink))
        } else {
            Image(
                painter = painterResource(R.drawable.customer_icon_cancel),
                contentDescription = "이미지 삭제",
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualMemoHistoryBlock(
    items: List<CustomerHistoryItem>,
    editing: Boolean,
    saving: Boolean,
    onToggleEditing: () -> Unit,
    additionalMemo: String,
    onAdditionalMemoChange: (String) -> Unit,
    onSaveMemo: () -> Unit,
    message: String?,
    onAddImageClick: () -> Unit,
    onDeleteImageClick: (AdditionalInfoPhotoItem) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val editBlockRequester = remember { BringIntoViewRequester() }
    val memos = items.filter { !it.memo.isNullOrBlank() || it.photos.isNotEmpty() }.take(5)
    val combinedMemo = memos.combinedAdditionalMemo()
    val memoPhotos = memos.flatMap { item ->
        val memoId = item.id.orEmpty()
        item.photos.mapNotNull { photo ->
            val photoId = photo.id ?: return@mapNotNull null
            val url = photo.url ?: return@mapNotNull null
            AdditionalInfoPhotoItem(memoId = memoId, photoId = photoId, url = url)
        }
    }.distinctBy { it.url }

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
            modifier = Modifier.clickable {
                if (editing) onSaveMemo() else onToggleEditing()
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (combinedMemo.isBlank() && memoPhotos.isEmpty() && !editing) {
            AdditionalInfoTextRow("추가된 정보가 없어요", color = PlaceholderGray)
        }
        if ((!editing && combinedMemo.isNotBlank()) || memoPhotos.isNotEmpty()) {
            Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (!editing && combinedMemo.isNotBlank()) {
                        AdditionalInfoTextRow(combinedMemo)
                    }
                    if (memoPhotos.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            memoPhotos.forEach { photo ->
                                Box {
                                    CustomerImageThumbnail(
                                        url = photo.url,
                                        contentDescription = "추가정보 이미지",
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(PhotoBg),
                                    )
                                    if (editing) {
                                        CustomerImageDeleteButton(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                            inspectionMode = false,
                                            onClick = { onDeleteImageClick(photo) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (editing) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(editBlockRequester),
            ) {
                AdditionalInfoInputRow(
                    value = additionalMemo,
                    onValueChange = onAdditionalMemoChange,
                    onFocused = {
                        scope.launch {
                            delay(350)
                            editBlockRequester.bringIntoView()
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                FigmaAddFieldRow(
                    text = "이미지 추가",
                    enabled = !saving,
                    onClick = onAddImageClick,
                    horizontalPadding = 0.dp,
                )
            }
        }
    }
    SectionStatusMessage(message)
}

@Composable
private fun AdditionalInfoInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "ㆍ",
            style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = Ink),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = Ink),
            cursorBrush = SolidColor(Ink),
            modifier = Modifier
                .weight(1f)
                .onFocusEvent { state ->
                    if (state.isFocused) {
                        onFocused()
                    }
                },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isBlank()) {
                        Text(
                            "추가정보를 적어주세요.",
                            style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = PlaceholderGray),
                        )
                    }
                    inner()
                }
            },
        )
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
    removable: Boolean = false,
    onLabelChange: (String) -> Unit = {},
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    onRemove: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (editing) 22.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editing) {
            BasicTextField(
                value = label,
                onValueChange = onLabelChange,
                textStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = PhoneGray),
                cursorBrush = SolidColor(Ink),
                singleLine = true,
                modifier = Modifier.width(72.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (label.isBlank()) {
                            Text(
                                "항목",
                                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = PlaceholderGray),
                            )
                        }
                        inner()
                    }
                },
            )
        } else {
            Text(
                label,
                modifier = Modifier.width(72.dp),
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = PhoneGray),
            )
        }
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
            if (removable) {
                Image(
                    painter = painterResource(R.drawable.customer_icon_cancel),
                    contentDescription = "행 삭제",
                    modifier = Modifier.size(22.dp).clickable { onRemove() },
                    contentScale = ContentScale.Fit,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.customer_icon_cancel),
                    contentDescription = "내용 지우기",
                    modifier = Modifier.size(22.dp).clickable { onClear() },
                    contentScale = ContentScale.Fit,
                )
            }
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

@Composable
private fun FullImageDialog(
    url: String?,
    onDismiss: () -> Unit,
) {
    if (url.isNullOrBlank()) return

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 620.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = "전체 이미지",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Text("x", style = TextStyle(fontSize = 16.sp, lineHeight = 16.sp, color = Color.White))
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
            FianoHeaderAlarmButton()
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
        CustomerInfoField("특이사항", profile.specialNotes.orEmpty(), editing = false, onChange = {}, onClear = {})
    }

    RelatedImagesSection(
        photos = detail.manualPhotos,
        editing = true,
        saving = false,
        message = null,
        onToggleEditing = {},
        onAddClick = {},
        onDeleteClick = {},
    )

    ManualMemoHistoryBlock(
        items = detail.manualHistory,
        editing = true,
        saving = false,
        onToggleEditing = {},
        additionalMemo = "",
        onAdditionalMemoChange = {},
        onSaveMemo = {},
        message = null,
        onAddImageClick = {},
        onDeleteImageClick = {},
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
        medical = null,
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
