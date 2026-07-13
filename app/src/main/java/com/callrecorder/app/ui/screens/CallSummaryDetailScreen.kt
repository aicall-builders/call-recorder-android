package com.callrecorder.app.ui.screens

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.callrecorder.app.R
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CallCategoryLabel
import com.callrecorder.app.data.model.ExtractedInfo
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsString
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.util.SttMessage
import com.callrecorder.app.util.SttParser
import com.callrecorder.app.util.SttSpeaker
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/* ── 색상: FIANO 0705 디자인 시스템 ── */
private val ScreenBg   = AppColors.DeepBrown900
private val SheetBg    = Color(0xFFFFFFFF)   // 본문 흰 시트
private val TabOffBg   = Color(0xFFEEEEEE)
private val Ink        = AppColors.DeepBrown950
private val SummaryBoxBg = AppColors.DeepBrown50
private val LabelGray  = AppColors.DeepBrown400
private val LabelGrayActive = AppColors.DeepBrown600
private val BubbleBot  = AppColors.DeepBrown700
private val BubbleCustomer = AppColors.DeepBrown50
private val SpeakerLabel = AppColors.DeepBrown600
private val TrackEmpty  = AppColors.DeepBrown300

private enum class DetailTab { ANALYSIS, TRANSCRIPT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSummaryDetailScreen(
    callId: String,
    onBack: () -> Unit,
    onManualScheduleRequest: () -> Unit = {},
    onCustomerClick: (String) -> Unit = {},
    onOpenRegisteredSchedule: (String?) -> Unit = {},
    initialCall: Call? = null,
    vm: CallSummaryDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(callId) { vm.load(callId, initialCall) }

    Scaffold(containerColor = ScreenBg) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            when {
                state.loading && state.detail == null ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
                state.error != null ->
                    Text(
                        "불러오기 실패: ${state.error}",
                        modifier = Modifier.align(Alignment.Center).padding(20.dp),
                        style = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f)),
                    )
                state.detail != null -> DetailBody(
                    call = state.detail!!.call,
                    audioUrl = state.audioUrl,
                    calendarLoading = state.calendarLoading,
                    calendarMessage = state.calendarMessage,
                    internalCalendarRegistered = state.internalCalendarRegistered,
                    internalCalendarDate = state.internalCalendarDate,
                    summarySaving = state.summarySaving,
                    summaryMessage = state.summaryMessage,
                    connectedCalendars = state.connectedCalendars,
                    showCalendarPicker = state.showCalendarPicker,
                    originalSummary = state.originalSummary,
                    originalKeywordRows = state.originalKeywordRows,
                    transcript = state.detail!!.transcript,
                    onBack = onBack,
                    onToggleCalendarPicker = { vm.toggleCalendarPicker(callId) },
                    onAddToCalendar = { provider -> vm.addToCalendar(callId, provider) },
                    onSummarySave = { summary, rows -> vm.updateSummaryAndKeywords(callId, summary, rows) },
                    onSummaryEditStateReset = { vm.clearSummaryMessage() },
                    onCustomerClick = onCustomerClick,
                    onOpenRegisteredSchedule = { onOpenRegisteredSchedule(state.internalCalendarDate) },
                )
            }
        }
    }

    if (state.showMissingScheduleDialog) {
        FianoConfirmDialog(
            title = "일정 등록",
            message = "등록할 일정을 찾지못했어요.\n직접 등록하시겠어요?",
            onDismiss = { vm.dismissMissingScheduleDialog() },
            onConfirm = {
                vm.dismissMissingScheduleDialog()
                onManualScheduleRequest()
            },
        )
    }
}

@Composable
private fun DetailBody(
    call: Call,
    audioUrl: String?,
    calendarLoading: Boolean,
    calendarMessage: String?,
    internalCalendarRegistered: Boolean,
    internalCalendarDate: String?,
    summarySaving: Boolean,
    summaryMessage: String?,
    connectedCalendars: List<String>,
    showCalendarPicker: Boolean,
    originalSummary: String,
    originalKeywordRows: List<Pair<String, String>>,
    transcript: String?,
    onBack: () -> Unit,
    onToggleCalendarPicker: () -> Unit,
    onAddToCalendar: (String) -> Unit,
    onSummarySave: (String, List<Pair<String, String>>) -> Unit,
    onSummaryEditStateReset: () -> Unit,
    onCustomerClick: (String) -> Unit,
    onOpenRegisteredSchedule: () -> Unit,
) {
    val info = call.extractedInfoOrNull()
    val transcriptText = transcript?.takeIf { it.isNotBlank() } ?: call.sttResult
    val messages = remember(transcriptText) { SttParser.parse(transcriptText) }
    var tab by remember { mutableStateOf(DetailTab.ANALYSIS) }

    Column(Modifier.fillMaxSize()) {
        // ═══ 상단 다크 영역 (헤더 + 발신자 + 플레이어) — 고정 ═══
        DetailTopBar(onBack = onBack)
        DetailHero(call = call, info = info, audioUrl = audioUrl, onCustomerClick = onCustomerClick)

        // ═══ 탭 (시트 상단 라운드) ═══
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        ) {
            DetailTabButton("통화 분석", tab == DetailTab.ANALYSIS, Modifier.weight(1f)) { tab = DetailTab.ANALYSIS }
            DetailTabButton("전문", tab == DetailTab.TRANSCRIPT, Modifier.weight(1f)) { tab = DetailTab.TRANSCRIPT }
        }

        // ═══ 본문 (흰 시트, 스크롤) ═══
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SheetBg)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 84.dp),
        ) {
            when (tab) {
                DetailTab.ANALYSIS -> AnalysisTabContent(
                    call = call,
                    info = info,
                    connectedCalendars = connectedCalendars,
                    calendarLoading = calendarLoading,
                    calendarMessage = calendarMessage,
                    internalCalendarRegistered = internalCalendarRegistered,
                    internalCalendarDate = internalCalendarDate,
                    summarySaving = summarySaving,
                    summaryMessage = summaryMessage,
                    showCalendarPicker = showCalendarPicker,
                    originalSummary = originalSummary,
                    originalKeywordRows = originalKeywordRows,
                    onToggleCalendarPicker = onToggleCalendarPicker,
                    onAddToCalendar = onAddToCalendar,
                    onSummarySave = onSummarySave,
                    onSummaryEditStateReset = onSummaryEditStateReset,
                    onOpenRegisteredSchedule = onOpenRegisteredSchedule,
                )
                DetailTab.TRANSCRIPT -> TranscriptTabContent(messages = messages, fullText = transcriptText)
            }
        }
    }
}

/* ─────────────── 상단 바 ─────────────── */
@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.detail_icon_back),
                contentDescription = "뒤로",
                modifier = Modifier.size(32.dp).clickable { onBack() },
            )
            Spacer(Modifier.width(8.dp))
            Text("통화 상세", style = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, color = Color.White))
        }
        FianoHeaderAlarmButton()
    }
}

@Composable
private fun DetailHero(
    call: Call,
    info: ExtractedInfo?,
    audioUrl: String?,
    onCustomerClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContactBlock(call = call, info = info, audioUrl = audioUrl, onCustomerClick = onCustomerClick)
        AudioPlayerDark(audioUrl = audioUrl)
    }
}

/* ─────────────── 발신자 정보 ─────────────── */
@Composable
private fun ContactBlock(
    call: Call,
    info: ExtractedInfo?,
    audioUrl: String?,
    onCustomerClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val displayTitle = detailCallTitle(call, info)
    val customerPhone = call.callerNumber?.takeIf { it.isNotBlank() }
        ?: info?.phone?.takeIf { it.isNotBlank() }
    val timeLabel = formatCallDateTime(call.createdAt)
    val durationLabel = formatDuration(call.duration)

    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(detailCallTypeIconRes(call)),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                displayTitle,
                modifier = Modifier.then(
                    if (customerPhone != null) Modifier.clickable { onCustomerClick(customerPhone) } else Modifier,
                ),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(timeLabel, style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)))
                if (durationLabel != null) {
                    Text("·", style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f)))
                    Text(durationLabel, style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f)))
                }
            }
        }
        Image(
            painter = painterResource(R.drawable.detail_icon_download),
            contentDescription = "다운로드",
            modifier = Modifier
                .size(24.dp)
                .clickable {
                    downloadCallAudio(
                        context = context,
                        audioUrl = audioUrl,
                        fileName = "${displayTitle.toSafeDownloadFileName()}_${call.id}.m4a",
                    )
                },
        )
    }
}

private fun downloadCallAudio(context: android.content.Context, audioUrl: String?, fileName: String) {
    if (audioUrl.isNullOrBlank()) {
        Toast.makeText(context, "다운로드할 음성 파일이 없어요.", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val request = DownloadManager.Request(Uri.parse(audioUrl))
            .setTitle(fileName)
            .setDescription("FIANO 통화 음성 파일")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
    }.fold(
        onSuccess = {
            Toast.makeText(context, "다운로드를 시작했어요.", Toast.LENGTH_SHORT).show()
        },
        onFailure = {
            Toast.makeText(context, "다운로드를 시작하지 못했어요.", Toast.LENGTH_SHORT).show()
        },
    )
}

private fun String.toSafeDownloadFileName(): String =
    replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "fiano_call_audio" }

private fun detailCallTitle(call: Call, info: ExtractedInfo?): String {
    return call.callerName?.takeIf { it.isNotBlank() }
        ?: call.callerNumber?.takeIf { it.isNotBlank() }
        ?: info?.phone?.takeIf { it.isNotBlank() }
        ?: call.s3Key?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
        ?: "업로드 음성 파일"
}

private fun detailCallTypeIconRes(call: Call): Int {
    return when (call.direction?.lowercase()) {
        "inbound", "incoming", "received", "receive", "reception", "수신" -> R.drawable.icon_reception_white
        "outbound", "outgoing", "sent", "send", "발신" -> R.drawable.icon_outgoing_white
        "manual", "upload", "uploaded", "file" -> R.drawable.icon_call_up_white
        else -> {
            if (call.callerName.isNullOrBlank() && call.callerNumber.isNullOrBlank() && !call.s3Key.isNullOrBlank()) {
                R.drawable.icon_call_up_white
            } else {
                R.drawable.icon_reception_white
            }
        }
    }
}

/* ─────────────── 다크 오디오 플레이어 ─────────────── */
@Composable
private fun AudioPlayerDark(audioUrl: String?) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = false } }
    var isPlaying by remember { mutableStateOf(false) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var totalMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(audioUrl) {
        if (!audioUrl.isNullOrBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(audioUrl)))
            exoPlayer.prepare()
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(100L)
        }
    }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { isPlaying = false; exoPlayer.seekTo(0); currentMs = 0L }
                if (state == Player.STATE_READY && totalMs == 0L) totalMs = exoPlayer.duration.coerceAtLeast(0L)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    val enabled = !audioUrl.isNullOrBlank()

    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 재생 버튼 (흰 원)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.4f))
                .clickable(enabled = enabled) {
                    if (isPlaying) { exoPlayer.pause(); isPlaying = false }
                    else { exoPlayer.play(); isPlaying = true }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp),
            )
        }

        // 진행바 + 시간
        Column(Modifier.weight(1f)) {
            DarkSeekBar(
                progress = if (totalMs > 0) currentMs.toFloat() / totalMs else 0f,
                enabled = enabled && totalMs > 0,
                onSeek = { ratio ->
                    val seekTo = (totalMs * ratio).toLong()
                    exoPlayer.seekTo(seekTo); currentMs = seekTo
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(msToTime(currentMs), style = TextStyle(fontSize = 11.sp, color = Color.White))
                Text(msToTime(totalMs), style = TextStyle(fontSize = 11.sp, color = Color.White))
            }
        }
    }
}

@Composable
private fun DarkSeekBar(progress: Float, enabled: Boolean, onSeek: (Float) -> Unit) {
    var widthPx by remember { mutableStateOf(1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { offset -> onSeek((offset.x / widthPx).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 트랙
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(TrackEmpty),
        )
        // 진행
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White),
        )
        // 핸들
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Ink)
                    .border(2.dp, Color.White, CircleShape),
            )
        }
    }
}

/* ─────────────── 탭 버튼 ─────────────── */
@Composable
private fun DetailTabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) Color.White else TabOffBg)
            .clickable { onClick() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 14.dp, end = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center),
                maxLines = 2,
            )
        }
    }
}

/* ─────────────── 통화 분석 탭 ─────────────── */
@Composable
private fun AnalysisTabContent(
    call: Call,
    info: ExtractedInfo?,
    connectedCalendars: List<String>,
    calendarLoading: Boolean,
    calendarMessage: String?,
    internalCalendarRegistered: Boolean,
    internalCalendarDate: String?,
    summarySaving: Boolean,
    summaryMessage: String?,
    showCalendarPicker: Boolean,
    originalSummary: String,
    originalKeywordRows: List<Pair<String, String>>,
    onToggleCalendarPicker: () -> Unit,
    onAddToCalendar: (String) -> Unit,
    onSummarySave: (String, List<Pair<String, String>>) -> Unit,
    onSummaryEditStateReset: () -> Unit,
    onOpenRegisteredSchedule: () -> Unit,
) {
    var editingSummary by remember(call.id, call.summary) { mutableStateOf(false) }
    var summaryDraft by remember(call.id, call.summary) { mutableStateOf(formatDateText(call.summary.orEmpty())) }

    // internal_keywords → 라벨/값 줄
    val keywordRows: List<Pair<String, String>> = remember(call.internalKeywordsRaw) {
        val raw = call.internalKeywordsString()
        if (raw.isNullOrBlank()) return@remember emptyList()
        try {
            val json = org.json.JSONObject(raw)
            json.keys().asSequence()
                .filter { !it.startsWith("_") }
                .mapNotNull { key ->
                    when (val v = json.opt(key)) {
                        is String -> if (v.isNotBlank()) key to v else null
                        is Int, is Long, is Double -> key to v.toString()
                        else -> null
                    }
                }.toList()
        } catch (_: Exception) { emptyList() }
    }
    var keywordDraftRows by remember(call.id, keywordRows) { mutableStateOf(keywordRows) }
    LaunchedEffect(editingSummary) {
        if (editingSummary) {
            summaryDraft = formatDateText(call.summary.orEmpty())
            keywordDraftRows = keywordRows.map { (label, value) -> label to formatSummaryKeywordValue(label, value) }
        }
    }
    LaunchedEffect(summarySaving, summaryMessage) {
        if (editingSummary && !summarySaving && summaryMessage == "요약을 저장했어요.") {
            editingSummary = false
        }
    }

    Surface(color = SummaryBoxBg, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            // 헤더 + 액션 버튼
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✦ AI 요약", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink))
                    if ((!call.summary.isNullOrBlank() || keywordRows.isNotEmpty()) && !editingSummary) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable {
                                    onSummaryEditStateReset()
                                    summaryDraft = formatDateText(call.summary.orEmpty())
                                    keywordDraftRows = keywordRows.map { (label, value) -> label to formatSummaryKeywordValue(label, value) }
                                    editingSummary = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.call_icon_edit),
                                contentDescription = "수정",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (editingSummary) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(end = 8.dp)
                            .clickable(enabled = !summarySaving) {
                                onSummaryEditStateReset()
                                summaryDraft = formatDateText(originalSummary)
                                keywordDraftRows = originalKeywordRows.map { (label, value) -> label to formatSummaryKeywordValue(label, value) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "원본복원",
                            style = TextStyle(
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = if (summarySaving) LabelGray else LabelGrayActive,
                            ),
                        )
                    }
                } else {
                    ActionCircle(
                        iconRes = if (internalCalendarRegistered) {
                            R.drawable.detail_icon_calendar_completed
                        } else {
                            R.drawable.detail_icon_calendar_plus
                        },
                        loading = calendarLoading,
                        enabled = !calendarLoading,
                    ) {
                        if (internalCalendarRegistered) {
                            onOpenRegisteredSchedule()
                        } else {
                            onToggleCalendarPicker()
                        }
                    }
                }
            }

            // 캘린더 선택 시트
            if (!editingSummary && showCalendarPicker && connectedCalendars.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(4.dp)) {
                        val labels = mapOf(
                            "google" to "Google 캘린더",
                            "kakao" to "카카오 캘린더",
                            "naver" to "네이버 캘린더",
                        )
                        connectedCalendars.forEach { provider ->
                            Text(
                                labels[provider] ?: provider,
                                modifier = Modifier.fillMaxWidth().clickable { onAddToCalendar(provider) }.padding(12.dp),
                                style = TextStyle(fontSize = 13.sp, color = Ink),
                            )
                        }
                    }
                }
            }
            calendarMessage?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = TextStyle(fontSize = 11.sp, color = LabelGrayActive),
                )
            }

            // 키워드 줄 (라벨 / 값)
            if (keywordRows.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    if (editingSummary) {
                        keywordDraftRows.forEachIndexed { index, (label, value) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .height(22.dp),
                                ) {
                                    InlineSummaryTextField(
                                        value = label,
                                        onValueChange = { next ->
                                            keywordDraftRows = keywordDraftRows.toMutableList().also {
                                                it[index] = next.takeSummaryLabelChars(6) to it[index].second
                                            }
                                        },
                                        textStyle = TextStyle(fontSize = 12.sp, color = LabelGrayActive),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .height(0.8.dp)
                                            .background(AppColors.DeepBrown200),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(22.dp),
                                ) {
                                    InlineSummaryTextField(
                                        value = value,
                                        onValueChange = { next ->
                                            keywordDraftRows = keywordDraftRows.toMutableList().also {
                                                it[index] = it[index].first to next
                                            }
                                        },
                                        textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink),
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomStart)
                                            .fillMaxWidth()
                                            .height(0.8.dp)
                                            .background(AppColors.DeepBrown200),
                                    )
                                }
                            }
                        }
                    } else {
                        keywordRows.forEach { (label, value) ->
                            val displayValue = formatSummaryKeywordValue(label, value)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .height(22.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        label,
                                        style = TextStyle(fontSize = 12.sp, color = LabelGrayActive),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(22.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(displayValue, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink))
                                }
                            }
                        }
                    }
                }
            }

            if (editingSummary) summaryMessage?.let {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = TextStyle(fontSize = 11.sp, color = LabelGrayActive),
                )
            }

            if (editingSummary) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        InlineSummaryTextField(
                            value = summaryDraft,
                            onValueChange = { summaryDraft = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = Ink, lineHeight = 21.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 132.dp)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            singleLine = false,
                            placeholder = {
                                Text("요약 내용을 입력해 주세요.", style = TextStyle(fontSize = 14.sp, color = LabelGray))
                            },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SummaryEditButton(
                            label = "취소",
                            filled = false,
                            enabled = !summarySaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            onSummaryEditStateReset()
                            summaryDraft = formatDateText(call.summary.orEmpty())
                            keywordDraftRows = keywordRows.map { (label, value) -> label to formatSummaryKeywordValue(label, value) }
                            editingSummary = false
                        }
                        SummaryEditButton(
                            label = if (summarySaving) "저장 중" else "저장",
                            filled = true,
                            enabled = !summarySaving,
                            modifier = Modifier.weight(1f),
                        ) {
                            onSummarySave(summaryDraft, keywordDraftRows)
                        }
                    }
                }
            } else if (!call.summary.isNullOrBlank()) {
                Text(
                    formatDateText(call.summary!!),
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
                    style = TextStyle(fontSize = 14.sp, color = Ink, lineHeight = 21.sp),
                )
            }
        }
    }

    if (keywordRows.isEmpty() && call.summary.isNullOrBlank()) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            Text("분석 결과가 아직 없어요.", style = TextStyle(fontSize = 13.sp, color = LabelGray))
        }
    }
}

private fun formatSummaryKeywordValue(label: String, value: String): String {
    val shouldFormatDate = listOf("일정", "날짜", "방문").any { label.contains(it) }
    if (!shouldFormatDate) return value
    return formatDateText(value)
}

private fun String.takeSummaryLabelChars(max: Int): String =
    codePoints().limit(max.toLong()).toArray().let { points ->
        String(points, 0, points.size)
    }

private fun formatDateText(value: String): String {
    fun paddedDate(year: String, month: String, day: String): String {
        val mm = month.toIntOrNull()?.toString() ?: month
        val dd = day.toIntOrNull()?.toString() ?: day
        return "${year}년 ${mm}월 ${dd}일"
    }

    return value
        .replace(Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")) { match ->
            paddedDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        .replace(Regex("""\b(\d{4})\s*\.\s*(\d{1,2})\s*\.\s*(\d{1,2})\.?""")) { match ->
            paddedDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
        .replace(Regex("""\b(\d{4})년\s*(\d{1,2})월\s*(\d{1,2})일\b""")) { match ->
            paddedDate(match.groupValues[1], match.groupValues[2], match.groupValues[3])
        }
}

@Composable
private fun ActionCircle(
    iconRes: Int,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Ink)
            .clickable(enabled = enabled && !loading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Image(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun InlineSummaryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    placeholder: (@Composable () -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = textStyle,
        decorationBox = { innerTextField ->
            Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isBlank() && placeholder != null) placeholder()
                innerTextField()
            }
        },
    )
}

@Composable
private fun SummaryEditButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (filled) Ink else Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = if (filled) null else BorderStroke(1.dp, Ink),
        modifier = modifier.heightIn(min = 44.dp),
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

/* ─────────────── 전문 탭 ─────────────── */
@Composable
private fun TranscriptTabContent(messages: List<SttMessage>, fullText: String?) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.detail_icon_copy),
                contentDescription = "복사",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { if (!fullText.isNullOrBlank()) clipboard.setText(AnnotatedString(fullText)) },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (messages.isEmpty()) {
                Text(
                    "통화 원문이 아직 준비되지 않았습니다.",
                    style = TextStyle(fontSize = 13.sp, color = LabelGray),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            } else {
                messages.forEach { msg ->
                    MessageBubble(msg)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SttMessage) {
    // 화자1(BOT) = 수신자(우측 다크) / 화자2(CUSTOMER)·UNKNOWN = 발신자(좌측 회색)
    val isReceiver = message.speaker == SttSpeaker.BOT
    val label = if (isReceiver) "수신자" else "발신자"

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isReceiver) Alignment.End else Alignment.Start,
    ) {
        Text(label, style = TextStyle(fontSize = 11.sp, color = SpeakerLabel))
        Spacer(Modifier.height(4.dp))
        Surface(
            color = if (isReceiver) BubbleBot else BubbleCustomer,
            shape = if (isReceiver)
                RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
            else
                RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            modifier = Modifier.padding(start = if (isReceiver) 24.dp else 0.dp, end = if (isReceiver) 0.dp else 24.dp),
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = TextStyle(
                    fontSize = 13.sp,
                    color = if (isReceiver) Color.White else Ink,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}

/* ─────────────── 유틸 ─────────────── */
private fun msToTime(ms: Long): String {
    val s = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(s / 60, s % 60)
}

/** 통화 길이(초) → "3분 24초" / "45초", 없으면 null */
private fun formatDuration(seconds: Int?): String? {
    val s = seconds ?: return null
    if (s <= 0) return null
    val m = s / 60
    val sec = s % 60
    return if (m > 0) "${m}분 ${sec}초" else "${sec}초"
}

/** "2026. 06. 04  17:57" */
private fun formatCallDateTime(serverTime: String?): String {
    if (serverTime.isNullOrBlank()) return ""
    val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    for (fmt in fmts) {
        try {
            val d = SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(serverTime)
            if (d != null) return SimpleDateFormat("yyyy. MM. dd  HH:mm", Locale.KOREAN).format(d)
        } catch (_: Exception) {}
    }
    return serverTime
}
