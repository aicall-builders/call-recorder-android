package com.callrecorder.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.R
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CallCategoryLabel
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import com.callrecorder.app.data.model.isAnalyzed
import com.callrecorder.app.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/* ── 색상: FIANO 0705 디자인 시스템 ── */
private val ScreenBg       = AppColors.DeepBrown900
private val SheetBg        = Color(0xFFFFFFFF)   // 본문 흰 시트
private val TabInactiveBg  = Color(0xFFEEEEEE)
private val Ink            = AppColors.DeepBrown950
private val SubInk         = AppColors.DeepBrown700
private val AccentBlue     = AppColors.SignalRed500
private val OnDarkPrimary  = Color(0xFFFFFFFF)
private val PlaceholderGray = AppColors.DeepBrown500
private val LabelGray      = AppColors.DeepBrown400
private val GroupGray      = AppColors.DeepBrown600

// 캘린더 등록 안내 박스
private val CalBoxBg = AppColors.SignalRed50
private val CalBoxFg = AppColors.SignalRed700

// 배지 (시안: 예약=Ink, 문의=SubInk, 기타=연회색)
private val BadgeResBg = Ink;             private val BadgeResFg = Color(0xFFFFFFFF)
private val BadgeInqBg = SubInk;          private val BadgeInqFg = Color(0xFFFFFFFF)
private val BadgeCnlBg = AppColors.SignalRed700; private val BadgeCnlFg = Color(0xFFFFFFFF)
private val BadgeCmpBg = AppColors.DeepBrown600; private val BadgeCmpFg = Color(0xFFFFFFFF)
private val BadgeNeuBg = AppColors.DeepBrown100; private val BadgeNeuFg = SubInk

private enum class CallFilter { ALL, RESERVATION, INQUIRY, OTHER }
private enum class AnalysisTab { PENDING, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSummaryListScreen(
    onCallClick: (String) -> Unit,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
    startOnPendingTab: Boolean = false,
    vm: HomeViewModel = viewModel(),
    approvalVm: PendingApprovalViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val approvalState by approvalVm.state.collectAsState()
    CallSummaryListContent(
        state = state,
        approvalState = approvalState,
        onCallClick = onCallClick,
        onDeleteCall = { vm.deleteCall(it) },
        onDeleteUpload = { vm.deleteUpload(it) },
        onRetryUpload = { vm.retryUpload(it) },
        onApproveRecording = { id ->
            approvalVm.approveOne(id)
            vm.refresh(silent = true)
        },
        onDeleteRecording = { id ->
            approvalVm.rejectOne(id)
            vm.refresh(silent = true)
        },
        onPendingTabVisible = { approvalVm.load() },
        initialTab = if (startOnPendingTab) AnalysisTab.PENDING else AnalysisTab.DONE,
        startOnPendingTab = startOnPendingTab,
        onNotificationClick = onNotificationClick,
        hasNotification = hasNotification,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallSummaryListContent(
    state: HomeUiState,
    approvalState: PendingApprovalUiState,
    onCallClick: (String) -> Unit,
    onDeleteCall: (String) -> Unit,
    onDeleteUpload: (Long) -> Unit,
    onRetryUpload: (Long) -> Unit,
    onApproveRecording: (Long) -> Unit,
    onDeleteRecording: (Long) -> Unit,
    onPendingTabVisible: () -> Unit = {},
    initialTab: AnalysisTab = AnalysisTab.DONE,
    startOnPendingTab: Boolean = false,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var filter by remember { mutableStateOf(CallFilter.ALL) }
    var tab by remember { mutableStateOf(initialTab) }
    var completionBaselineIds by remember { mutableStateOf<Set<String>?>(null) }
    var pendingDeleteCallId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(startOnPendingTab) {
        if (startOnPendingTab) {
            tab = AnalysisTab.PENDING
            filter = CallFilter.ALL
        }
    }

    val uniqueCalls = remember(state.recentCalls) { state.recentCalls.distinctBy { it.id } }
    val doneCallIds = remember(uniqueCalls) {
        uniqueCalls.filter { it.isAnalyzed() }.map { it.id }.toSet()
    }

    LaunchedEffect(tab, state.uploadingCount, doneCallIds) {
        if (tab != AnalysisTab.PENDING) {
            completionBaselineIds = null
            return@LaunchedEffect
        }
        if (state.uploadingCount > 0 && completionBaselineIds == null) {
            completionBaselineIds = doneCallIds
        }
        val baseline = completionBaselineIds
        if (state.uploadingCount == 0 && baseline != null && doneCallIds.any { it !in baseline }) {
            tab = AnalysisTab.DONE
            filter = CallFilter.ALL
            completionBaselineIds = null
        }
    }

    // ① 분석 대기 / 완료 분리
    val tabCalls = remember(uniqueCalls, tab) {
        uniqueCalls.filter { call ->
            when (tab) {
                AnalysisTab.DONE -> call.isAnalyzed()
                AnalysisTab.PENDING -> !call.isAnalyzed()
            }
        }
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
    val awaitingApprovals = remember(approvalState.recordings, searchText.text, tab) {
        if (tab != AnalysisTab.PENDING) {
            emptyList()
        } else {
            val q = searchText.text.trim()
            approvalState.recordings.filter { recording ->
                q.isBlank() ||
                    recording.fileName.contains(q, ignoreCase = true) ||
                    recording.counterpartNumber.orEmpty().contains(q)
            }
        }
    }
    val activeUploads = remember(state.activeUploads, searchText.text, tab) {
        if (tab != AnalysisTab.PENDING) {
            emptyList()
        } else {
            val q = searchText.text.trim()
            state.activeUploads.filter { upload ->
                q.isBlank() || upload.name.contains(q, ignoreCase = true) || upload.phase.contains(q, ignoreCase = true)
            }
        }
    }
    // 카운트 (현재 탭 기준)
    val totalCount = tabCalls.size + if (tab == AnalysisTab.PENDING) awaitingApprovals.size + activeUploads.size else 0
    val resCount = tabCalls.count { it.category == CallCategoryLabel.RESERVATION }
    val inqCount = tabCalls.count { it.category == CallCategoryLabel.INQUIRY }
    val otherCount = tabCalls.count {
        it.category != CallCategoryLabel.RESERVATION && it.category != CallCategoryLabel.INQUIRY
    }

    // 날짜별 그룹
    val grouped = remember(filtered) {
        filtered.groupBy { dateGroupLabel(it.createdAt) }
    }

    LaunchedEffect(tab) {
        if (tab == AnalysisTab.PENDING) onPendingTabVisible()
    }

    pendingDeleteCallId?.let { callId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCallId = null },
            text = {
                Text(
                    "통화 분석 데이터를 정말 삭제하시겠습니까?",
                    style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Ink),
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCallId = null }) {
                    Text("취소", color = SubInk)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteCallId = null
                        onDeleteCall(callId)
                    },
                ) {
                    Text("확인", color = AppColors.SignalRed500, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(18.dp),
        )
    }

    Scaffold(containerColor = ScreenBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            // ═══ 헤더 (ScreenBg 위) ═══
            FianoListHeroHeader(
                title = "통화 분석 내역을 확인해보세요.",
                searchText = searchText,
                onSearchTextChange = { searchText = it },
                onNotificationClick = onNotificationClick,
                hasNotification = hasNotification,
            )

            // ═══ 분석 대기 / 완료 탭 (시트 상단, 라운드) ═══
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SheetBg),
                contentPadding = PaddingValues(0.dp),
            ) {
                // ═══ 본문 (흰 시트) ═══
                // 필터 칩
                if (tab == AnalysisTab.DONE) {
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
                }

                if (state.loading && filtered.isEmpty() && awaitingApprovals.isEmpty() && activeUploads.isEmpty()) {
                    item {
                        Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AccentBlue)
                            }
                        }
                    }
                } else if (filtered.isEmpty() && awaitingApprovals.isEmpty() && activeUploads.isEmpty()) {
                    item {
                        Surface(color = SheetBg, modifier = Modifier.fillParentMaxHeight().fillMaxWidth()) {
                            Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.TopCenter) {
                                Text(
                                    if (tab == AnalysisTab.PENDING) "분석 대기 중인 통화가 없어요" else "해당하는 통화가 없어요",
                                    modifier = Modifier.padding(top = 40.dp),
                                    style = TextStyle(fontSize = 13.sp, color = PlaceholderGray),
                                )
                            }
                        }
                    }
                } else {
                    if (tab == AnalysisTab.PENDING && awaitingApprovals.isNotEmpty()) {
                        item {
                            Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "업로드 승인 대기",
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GroupGray),
                                )
                            }
                        }
                        items(awaitingApprovals, key = { "approval-${it.id}" }) { recording ->
                            Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier
                                        .background(SheetBg)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                ) {
                                    PendingApprovalCallCard(
                                        recording = recording,
                                        isDuplicate = recording.id in approvalState.duplicateIds,
                                        onApprove = { onApproveRecording(recording.id) },
                                        onDelete = { onDeleteRecording(recording.id) },
                                    )
                                }
                            }
                        }
                    }
                    if (tab == AnalysisTab.PENDING && activeUploads.isNotEmpty()) {
                        item {
                            Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "업로드·분석 중",
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GroupGray),
                                )
                            }
                        }
                        items(activeUploads, key = { "active-upload-${it.id}" }) { upload ->
                            Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                                SwipeRevealDeleteBox(
                                    onDelete = { onDeleteUpload(upload.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SheetBg),
                                ) {
                                    Box(
                                        Modifier
                                            .background(SheetBg)
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                    ) {
                                        ActiveUploadCallCard(
                                            upload = upload,
                                            onRetry = { onRetryUpload(upload.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                            Surface(color = SheetBg, modifier = Modifier.fillMaxWidth()) {
                                SwipeRevealDeleteBox(
                                    onDelete = {
                                        if (tab == AnalysisTab.DONE) {
                                            pendingDeleteCallId = call.id
                                        } else {
                                            onDeleteCall(call.id)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .background(SheetBg)
                                            .fillMaxWidth(),
                                    ) {
                                        if (tab == AnalysisTab.PENDING) {
                                            PendingCallCard(
                                                call = call,
                                                onClick = { onCallClick(call.id) },
                                                onEditContact = { openContactEditor(context, call.callerNumber, call.callerName) },
                                            )
                                        } else {
                                            CallListCard(
                                                call = call,
                                                onClick = { onCallClick(call.id) },
                                                onEditContact = { openContactEditor(context, call.callerNumber, call.callerName) },
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
}

@Preview(name = "Call Management - Pending With Upload Approvals", widthDp = 360, heightDp = 918, showBackground = false)
@Composable
private fun CallSummaryPendingWithApprovalsPreview() {
    CallSummaryListContent(
        state = HomeUiState(
            loading = false,
            recentCalls = listOf(
                Call(
                    id = "pending-1",
                    callerNumber = "010-2488-1122",
                    callerName = "박서윤",
                    direction = "incoming",
                    duration = 188,
                    status = "processing",
                    createdAt = "2026-07-08T09:20:00Z",
                    summary = null,
                    category = CallCategoryLabel.INQUIRY,
                ),
                Call(
                    id = "pending-2",
                    callerNumber = "02-778-9012",
                    callerName = "한빛부동산",
                    direction = "outgoing",
                    duration = 82,
                    status = "uploaded",
                    createdAt = "2026-07-08T08:44:00Z",
                    summary = null,
                    category = CallCategoryLabel.RESERVATION,
                ),
                Call(
                    id = "done-1",
                    callerNumber = "010-9988-7766",
                    callerName = "김민준",
                    direction = "incoming",
                    duration = 214,
                    status = "completed",
                    createdAt = "2026-07-07T13:10:00Z",
                    summary = "7월 10일 오전 방문 가능 여부와 준비 서류를 문의했습니다.",
                    category = CallCategoryLabel.RESERVATION,
                ),
            ),
        ),
        approvalState = PendingApprovalUiState(
            recordings = listOf(
                RecordingEntity(
                    id = 1001L,
                    filePath = "/preview/call_0708_0930.m4a",
                    fileName = "통화녹음_박서윤_0708_0930.m4a",
                    fileSize = 2_480_000L,
                    durationSeconds = 156,
                    callStartedAtMillis = 1_783_488_600_000L,
                    counterpartNumber = "010-2488-1122",
                    storeId = "preview-store",
                    status = "AWAITING_APPROVAL",
                ),
                RecordingEntity(
                    id = 1002L,
                    filePath = "/preview/call_0708_0912.m4a",
                    fileName = "통화녹음_중복파일_0708_0912.m4a",
                    fileSize = 1_920_000L,
                    durationSeconds = 96,
                    callStartedAtMillis = 1_783_487_920_000L,
                    counterpartNumber = "02-778-9012",
                    storeId = "preview-store",
                    status = "AWAITING_APPROVAL",
                ),
            ),
            duplicateIds = setOf(1002L),
            loading = false,
        ),
        onCallClick = {},
        onDeleteCall = {},
        onDeleteUpload = {},
        onRetryUpload = {},
        onApproveRecording = {},
        onDeleteRecording = {},
        initialTab = AnalysisTab.PENDING,
    )
}

/* ── 분석 대기/완료 탭 버튼 ── */
@Composable
private fun AnalysisTabButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) Color.White else TabInactiveBg)
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
        "transcribed" -> PendingPhase("요약 중", Color(0xFFE3EEFB), AccentBlue, showProgress = false)
        "processing" -> PendingPhase("서버 분석 중", Color(0xFFE3EEFB), AccentBlue, showProgress = false)
        "error", "failed" -> PendingPhase("분석 실패", Color(0xFFFBE3E3), Color(0xFFC23B3B), showProgress = false, isError = true)
        else -> PendingPhase("분석 대기", Color(0xFFE8EBF3), SubInk, showProgress = true)
    }
}

/* ── 업로드 승인 대기 카드 ── */
@Composable
private fun PendingApprovalCallCard(
    recording: RecordingEntity,
    isDuplicate: Boolean,
    onApprove: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = SheetBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.icon_call_up),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )

                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            recording.fileName,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
                            maxLines = 1,
                        )
                        Surface(
                            color = Color(0xFFE8EBF3),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text(
                                "승인 대기",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = SubInk),
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    InfoRow("시간", formatDuration(recording.durationSeconds))
                    Spacer(Modifier.height(2.dp))
                    InfoRow("파일", formatFileSize(recording.fileSize))
                    if (isDuplicate) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "중복파일",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.SignalRed500),
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PendingApprovalActionButton(
                            label = "삭제",
                            filled = false,
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                        )
                        PendingApprovalActionButton(
                            label = "승인",
                            filled = true,
                            onClick = onApprove,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingApprovalActionButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = if (filled) Ink else SheetBg,
        shape = RoundedCornerShape(999.dp),
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, Ink),
        modifier = modifier.height(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (filled) Color.White else Ink,
                ),
            )
        }
    }
}

@Composable
private fun ActiveUploadCallCard(
    upload: UploadItem,
    onRetry: () -> Unit,
) {
    Surface(
        color = SheetBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.icon_call_up),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )

                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            upload.name,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
                            maxLines = 1,
                        )
                        if (upload.canRetry) {
                            RetryUploadButton(onClick = onRetry)
                        } else {
                            PhaseBadge(upload.phase.toPendingPhase())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (upload.phase != "실패") {
                            CircularProgressIndicator(
                                color = AccentBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            when (upload.phase) {
                                "대기중" -> "업로드를 준비하고 있어요."
                                "업로드중" -> "파일을 업로드하고 있어요."
                                "실패" -> upload.errorMessage?.takeIf { it.isNotBlank() }
                                    ?: "업로드에 실패했어요. 재시도 버튼을 눌러 다시 시도해 주세요."
                                else -> "분석이 끝나면 알려드릴게요."
                            },
                            style = TextStyle(fontSize = 13.sp, color = if (upload.phase == "실패") AppColors.SignalRed500 else LabelGray),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryUploadButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.SignalRed500),
    ) {
        Text(
            "재시도",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.SignalRed500,
            ),
        )
    }
}

/* ── 분석 대기 카드 ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingCallCard(
    call: Call,
    onClick: () -> Unit,
    onEditContact: () -> Unit,
) {
    val callTime = formatTimeShort(call.createdAt)
    val durationText = formatDuration(call.duration)
    val phase = pendingPhaseOf(call)

    val name = call.callerName?.takeIf { it.isNotBlank() }
    val number = call.callerNumber?.takeIf { it.isNotBlank() }
    val primary = name ?: number ?: "발신번호 없음"

    Surface(
        onClick = onClick,
        color = SheetBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 왼쪽 원형 아이콘
                CallTypeIcon(call)

                Column(Modifier.weight(1f)) {
                    // 이름/번호 + 시간 + 상태 배지
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                primary,
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
                            )
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(id = R.drawable.call_icon_edit),
                                contentDescription = "연락처 수정",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onEditContact() },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(callTime, style = TextStyle(fontSize = 13.sp, color = AccentBlue))
                        }
                        PhaseBadge(phase)
                    }

                    Spacer(Modifier.height(6.dp))

                    // 통화 길이
                    InfoRow("시간", durationText)
                    Spacer(Modifier.height(2.dp))
                    InfoRow("방향", callDirectionLabel(call))
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
                            when {
                                phase.isError && call.errorMessage.orEmpty().contains("일별 한도") ->
                                    "CLOVA 일별 한도에 도달해 분석이 중단됐어요."
                                phase.isError -> "분석에 실패했어요. 다시 시도해 주세요."
                                else -> "서버에서 분석 중이에요. 완료되면 알려드릴게요."
                            },
                            style = TextStyle(fontSize = 13.sp, color = if (phase.isError) Color(0xFFC23B3B) else LabelGray),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseBadge(phase: PendingPhase) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, phase.fg),
    ) {
        Text(
            phase.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = phase.fg),
        )
    }
}

private fun String.toPendingPhase(): PendingPhase {
    return when (this) {
        "대기중" -> PendingPhase("대기중", Color(0xFFE8EBF3), SubInk, showProgress = true)
        "업로드중" -> PendingPhase("업로드중", Color(0xFFE3EEFB), AccentBlue, showProgress = true)
        "실패" -> PendingPhase("실패", Color(0xFFFBE3E3), AppColors.SignalRed500, showProgress = false, isError = true)
        else -> PendingPhase("서버 분석 중", Color(0xFFE3EEFB), AccentBlue, showProgress = false)
    }
}

/* ── 통화 카드 ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallListCard(
    call: Call,
    onClick: () -> Unit,
    onEditContact: () -> Unit,
) {
    val info = call.extractedInfoOrNull()
    val callTime = formatTimeShort(call.createdAt)
    val durationText = formatDuration(call.duration)

    val internalKw = remember(call.internalKeywordsRaw) { call.internalKeywordsMap() }

    val processResult = remember(call.summary, call.extractedInfoRaw, call.internalKeywordsRaw) {
        compactCallListSummary(call, info, internalKw)
    }

    val name = call.callerName?.takeIf { it.isNotBlank() }
    val number = call.callerNumber?.takeIf { it.isNotBlank() }
    val primary = name ?: number ?: "발신번호 없음"

    // ③ 캘린더 등록 안내 문구: 예약 + date·time 있을 때만
    val calendarText = remember(call.id, info) { buildCalendarNotice(call, info) }

    Surface(
        onClick = onClick,
        color = SheetBg,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 왼쪽 원형 통화 아이콘
                CallTypeIcon(call)

                Column(Modifier.weight(1f)) {
                    // 이름/번호 + 시간 + 배지
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                primary,
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink),
                            )
                            Spacer(Modifier.width(4.dp))
                            Image(
                                painter = painterResource(id = R.drawable.call_icon_edit),
                                contentDescription = "연락처 수정",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onEditContact() },
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
                    InfoRow("방향", callDirectionLabel(call))
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
                        Image(
                            painter = painterResource(id = R.drawable.call_icon_timeline_marker),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            calendarText,
                            style = TextStyle(fontSize = 13.sp, color = CalBoxFg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallTypeIcon(call: Call) {
    Image(
        painter = painterResource(id = callTypeIconRes(call)),
        contentDescription = null,
        modifier = Modifier.size(36.dp),
    )
}

private fun callDirectionLabel(call: Call): String {
    if (isManualUploadCall(call)) return "수동"
    return when (call.direction?.lowercase()) {
        "inbound", "incoming" -> "수신"
        "outbound", "outgoing" -> "발신"
        "manual" -> "수동"
        else -> "미상"
    }
}

private fun callTypeIconRes(call: Call): Int {
    if (isManualUploadCall(call)) return R.drawable.icon_call_up
    return when (call.direction?.lowercase()) {
        "inbound", "incoming" -> R.drawable.icon_reception
        "outbound", "outgoing" -> R.drawable.icon_outgoing
        "manual", "upload", "uploaded" -> R.drawable.icon_call_up
        else -> R.drawable.call_icon_type_default
    }
}

private fun isManualUploadCall(call: Call): Boolean {
    val direction = call.direction?.lowercase()
    return direction in setOf("manual", "upload", "uploaded") ||
        (call.callerName.isNullOrBlank() && call.callerNumber.isNullOrBlank() && !call.s3Key.isNullOrBlank())
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

private fun compactCallListSummary(
    call: Call,
    info: com.callrecorder.app.data.model.ExtractedInfo?,
    internalKeywords: Map<String, String>,
): String {
    val candidate = call.summary?.takeIf { it.isNotBlank() }
        ?: info?.specialNotes?.takeIf { it.isNotBlank() }
        ?: internalKeywords.entries.firstOrNull()?.value
        ?: "처리 결과 없음"

    val normalized = candidate
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^(AI\\s*)?요약\\s*[:：]\\s*"), "")
        .trim()

    val firstClause = normalized
        .split(Regex("(?<=[.!?。！？])\\s+|[。.!?]\\s+"))
        .firstOrNull()
        ?.trim()
        .orEmpty()
        .ifBlank { normalized }

    return if (firstClause.length <= 34) firstClause else firstClause.take(33).trimEnd() + "…"
}

private fun openContactEditor(context: Context, number: String?, name: String?) {
    val cleanNumber = number?.trim().orEmpty()
    val cleanName = name?.trim().orEmpty()
    val contactUri = findContactUri(context, cleanNumber)

    val intent = if (contactUri != null) {
        Intent(Intent.ACTION_EDIT, contactUri).apply {
            putExtra("finishActivityOnSaveCompleted", true)
        }
    } else {
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            if (cleanNumber.isNotBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, cleanNumber)
            if (cleanName.isNotBlank()) putExtra(ContactsContract.Intents.Insert.NAME, cleanName)
        }
    }

    runCatching { context.startActivity(intent) }
}

private fun findContactUri(context: Context, number: String): Uri? {
    if (number.isBlank()) return null
    return runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.LOOKUP_KEY),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lookupKey = cursor.getString(0)
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
            } else {
                null
            }
        }
    }.getOrNull()
}

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

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "-"
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
        String.format(Locale.KOREAN, "%.1fKB", kb)
    } else {
        String.format(Locale.KOREAN, "%.1fMB", kb / 1024.0)
    }
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
