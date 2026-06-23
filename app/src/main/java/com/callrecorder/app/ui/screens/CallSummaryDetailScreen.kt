package com.callrecorder.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSummaryDetailScreen(
    callId: String,
    onBack: () -> Unit,
    vm: CallSummaryDetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(callId) { vm.load(callId) }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "통화 상세",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Filled.NotificationsNone, "알림", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HeaderBg),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding),
        ) {
            when {
                state.loading && state.detail == null -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AppColors.BrandBlue,
                )
                state.error != null -> Text(
                    text = "불러오기 실패: ${state.error}",
                    modifier = Modifier.align(Alignment.Center).padding(20.dp),
                    style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary),
                )
                state.detail != null -> DetailBody(
                    call = state.detail!!.call,
                    audioUrl = state.audioUrl,
                    calendarLoading = state.calendarLoading,
                    calendarMessage = state.calendarMessage,
                    connectedCalendars = state.connectedCalendars,
                    showCalendarPicker = state.showCalendarPicker,
                    onToggleCalendarPicker = { vm.toggleCalendarPicker() },
                    onAddToCalendar = { provider -> vm.addToCalendar(callId, provider) },
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    call: Call,
    audioUrl: String?,
    calendarLoading: Boolean = false,
    calendarMessage: String? = null,
    connectedCalendars: List<String> = emptyList(),
    showCalendarPicker: Boolean = false,
    onToggleCalendarPicker: () -> Unit = {},
    onAddToCalendar: (String) -> Unit = {},
) {
    val info = call.extractedInfoOrNull()
    val messages = remember(call.sttResult) { SttParser.parse(call.sttResult) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 1) 헤더 (연한 그레이)
        ContactHeaderCard(call = call, info = info)

        // 2) AI 요약
        if (!call.summary.isNullOrBlank() || call.internalKeywordsRaw != null) {
            AiSummarySection(
                summary = call.summary,
                internalKeywordsRaw = call.internalKeywordsString(),
                connectedCalendars = connectedCalendars,
                calendarLoading = calendarLoading,
                calendarMessage = calendarMessage,
                showCalendarPicker = showCalendarPicker,
                onToggleCalendarPicker = onToggleCalendarPicker,
                onAddToCalendar = onAddToCalendar,
            )
        }

        HorizontalDivider(color = SectionDivider, thickness = 8.dp)

        // 3) 음성
        AudioSection(audioUrl = audioUrl)

        HorizontalDivider(color = SectionDivider, thickness = 8.dp)

        // 4) 전문
        TranscriptSection(messages = messages, fullText = call.sttResult)

        Spacer(Modifier.height(40.dp))
    }
}

/* ─────────────────────────────────────────────────────
 * 1. 헤더 (연한 그레이 배경)
 * ───────────────────────────────────────────────────── */
@Composable
private fun ContactHeaderCard(call: Call, info: ExtractedInfo?) {
    val displayPhone = call.callerNumber ?: info?.phone ?: "발신번호 없음"
    val timeLabel = formatCallDateTime(call.createdAt)
    val durationLabel = formatDuration(call.duration)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = displayPhone,
                    style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White),
                )
                CategoryChip(category = call.category)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(timeLabel, style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f)))
                Text("·", style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f)))
                Text(durationLabel, style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f)))
                Text("·", style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f)))
                Text("수신", style = TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f)))
            }
        }
    }
}

@Composable
private fun CategoryChip(category: String?) {
    val (label, bg, fg) = when (category) {
        CallCategoryLabel.RESERVATION -> Triple("예약", Color(0xFFE5DEF7), Color(0xFF5B4FB6))
        CallCategoryLabel.CANCEL -> Triple("취소", Color(0xFFFEE7E7), Color(0xFFC44545))
        CallCategoryLabel.COMPLAINT -> Triple("불만", Color(0xFFFFF1E0), Color(0xFFD97706))
        CallCategoryLabel.INQUIRY -> Triple("문의", Color(0xFFEEF2FF), Color(0xFF4F46E5))
        else -> Triple("기타", Color(0xFFEAEAEF), Color(0xFF6B7280))
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg),
        )
    }
}

/* ─────────────────────────────────────────────────────
 * 2. AI 요약 (줄 방식)
 * ───────────────────────────────────────────────────── */
@Composable
private fun AiSummarySection(
    summary: String?,
    internalKeywordsRaw: String?,
    connectedCalendars: List<String>,
    calendarLoading: Boolean,
    calendarMessage: String?,
    showCalendarPicker: Boolean,
    onToggleCalendarPicker: () -> Unit,
    onAddToCalendar: (String) -> Unit,
) {
    val keywordRows: List<Pair<String, String>> = remember(internalKeywordsRaw) {
        if (internalKeywordsRaw.isNullOrBlank()) return@remember emptyList()
        try {
            val json = org.json.JSONObject(internalKeywordsRaw)
            json.keys().asSequence()
                .filter { key -> !key.startsWith("_") }  // 내부 디버그 키 제거
                .mapNotNull { key ->
                    val value = json.opt(key)
                    // 문자열/숫자만 허용, 객체/배열 제외
                    when (value) {
                        is String -> if (value.isNotBlank()) Pair(key, value) else null
                        is Int, is Long, is Double -> Pair(key, value.toString())
                        else -> null
                    }
                }
                .toList()
        } catch (e: Exception) { emptyList() }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        // 헤더 + 액션 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.BrandBlue))
                Spacer(Modifier.width(6.dp))
                Text("AI 요약", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(AppColors.BrandBlue.copy(alpha = 0.08f)).clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Sms, "문자 전송", tint = AppColors.BrandBlue, modifier = Modifier.size(18.dp))
                }
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .background(AppColors.BrandBlue.copy(alpha = 0.08f)).clickable { onToggleCalendarPicker() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (calendarLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.BrandBlue)
                    } else {
                        Icon(Icons.Filled.CalendarMonth, "캘린더", tint = AppColors.BrandBlue, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 캘린더 선택
        if (showCalendarPicker && connectedCalendars.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AppColors.Background,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    val labels = mapOf("google" to "📅 Google 캘린더", "kakao" to "💛 카카오 캘린더", "naver" to "💚 네이버 캘린더")
                    connectedCalendars.forEach { provider ->
                        Text(
                            text = labels[provider] ?: provider,
                            modifier = Modifier.fillMaxWidth().clickable { onAddToCalendar(provider) }.padding(12.dp),
                            style = TextStyle(fontSize = 13.sp, color = AppColors.TextPrimary),
                        )
                    }
                }
            }
        }

        calendarMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = TextStyle(fontSize = 11.sp, color = AppColors.TextSecondary))
        }

        Spacer(Modifier.height(16.dp))

        // 키워드 테이블
        if (keywordRows.isNotEmpty()) {
            keywordRows.forEachIndexed { idx, (label, value) ->
                if (idx > 0) Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary),
                        modifier = Modifier.width(80.dp),
                    )
                    Text(
                        text = value,
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = AppColors.Divider)
                Spacer(Modifier.height(14.dp))
            }
        }

        // 요약 본문
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                style = TextStyle(fontSize = 13.sp, color = AppColors.TextPrimary, lineHeight = 21.sp),
            )
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 3. 음성 섹션
 * ───────────────────────────────────────────────────── */
@Composable
private fun AudioSection(audioUrl: String?) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var totalMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

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

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("음성", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary))
            IconButton(
                onClick = {
                    if (!audioUrl.isNullOrBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(audioUrl)))
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Filled.Download, "다운로드", tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(if (audioUrl.isNullOrBlank()) AppColors.Divider else AppColors.BrandBlue),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = {
                        if (audioUrl.isNullOrBlank()) return@IconButton
                        if (isPlaying) { exoPlayer.pause(); isPlaying = false }
                        else { exoPlayer.setPlaybackSpeed(playbackSpeed); exoPlayer.play(); isPlaying = true }
                    },
                    enabled = !audioUrl.isNullOrBlank(),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                SeekableSlider(
                    progress = if (totalMs > 0) currentMs.toFloat() / totalMs else 0f,
                    enabled = !audioUrl.isNullOrBlank() && totalMs > 0,
                    onSeek = { ratio ->
                        val seekTo = (totalMs * ratio).toLong()
                        exoPlayer.seekTo(seekTo); currentMs = seekTo
                    },
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(msToTime(currentMs), style = TextStyle(fontSize = 11.sp, color = AppColors.TextSecondary))
                    Text(msToTime(totalMs), style = TextStyle(fontSize = 11.sp, color = AppColors.TextSecondary))
                }
            }
        }

        if (!audioUrl.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                SmallChip(label = "${formatSpeed(playbackSpeed)}×") {
                    playbackSpeed = nextSpeed(playbackSpeed)
                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                }
            }
        }
    }
}

@Composable
private fun SeekableSlider(progress: Float, enabled: Boolean, onSeek: (Float) -> Unit) {
    var widthPx by remember { mutableIntStateOf(1) }
    val capped = progress.coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxWidth().height(24.dp)
            .onSizeChanged { widthPx = it.width }
            .then(if (enabled) Modifier.pointerInput(Unit) {
                detectTapGestures { onSeek((it.x / widthPx).coerceIn(0f, 1f)) }
            } else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(AppColors.Divider))
        Box(modifier = Modifier.fillMaxWidth(capped).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (enabled) AppColors.BrandBlue else AppColors.Divider))
        if (enabled) {
            Box(
                modifier = Modifier
                    .offset(x = ((capped * widthPx).toInt() - 7).coerceAtLeast(0).dp)
                    .size(14.dp).clip(CircleShape).background(AppColors.BrandBlue),
            )
        }
    }
}

@Composable
private fun SmallChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onClick() }.border(1.dp, AppColors.Divider, RoundedCornerShape(6.dp)),
        color = AppColors.Background,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary))
    }
}

/* ─────────────────────────────────────────────────────
 * 4. 전문 섹션 (어두운 보라색 말풍선)
 * ───────────────────────────────────────────────────── */
@Composable
private fun TranscriptSection(messages: List<SttMessage>, fullText: String?) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }  // ← 접기/펼치기 상태

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },  // ← 헤더 전체 터치로 토글
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("전문", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (expanded) {
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFF4F4F8))
                            .clickable { if (!fullText.isNullOrBlank()) clipboard.setText(AnnotatedString(fullText)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ContentCopy, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "접기" else "펼쳐보기",
                        style = TextStyle(fontSize = 12.sp, color = AppColors.BrandBlue, fontWeight = FontWeight.Medium),
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppColors.BrandBlue,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // 펼쳐졌을 때만 내용 표시
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            if (messages.isEmpty()) {
                Text(
                    text = "통화 원문이 아직 준비되지 않았습니다.",
                    style = TextStyle(fontSize = 12.sp, color = AppColors.TextSecondary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            } else {
                messages.forEachIndexed { idx, msg ->
                    if (idx > 0) Spacer(Modifier.height(10.dp))
                    MessageBubble(message = msg)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SttMessage) {
    val isCustomer = message.speaker == SttSpeaker.CUSTOMER || message.speaker == SttSpeaker.UNKNOWN
    val alignment = if (isCustomer) Alignment.Start else Alignment.End
    // 수신자: 어두운 보라색 / 발신자: 연한 그레이
    val bubbleColor = if (isCustomer) BubbleCustomer else BubbleOwner
    val textColor = if (isCustomer) AppColors.TextPrimary else Color.White
    val speakerLabel = when (message.speaker) {
        SttSpeaker.CUSTOMER -> "발신자"
        SttSpeaker.BOT -> "수신자"
        SttSpeaker.UNKNOWN -> ""
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        if (speakerLabel.isNotBlank()) {
            Text(speakerLabel, style = TextStyle(fontSize = 10.sp, color = AppColors.TextSecondary), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Surface(
            color = bubbleColor,
            shape = if (isCustomer) {
                RoundedCornerShape(topStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
            } else {
                RoundedCornerShape(topStart = 12.dp, topEnd = 4.dp, bottomEnd = 12.dp, bottomStart = 12.dp)
            },
            modifier = Modifier.fillMaxWidth(0.78f),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(fontSize = 13.sp, color = textColor, lineHeight = 19.sp),
            )
        }
    }
}

/* ─────────────────────────────────────────────────────
 * 유틸
 * ───────────────────────────────────────────────────── */
private fun nextSpeed(current: Float): Float = when (current) { 1f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2f; 2f -> 0.75f; else -> 1f }
private fun formatSpeed(speed: Float): String = if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0').trimEnd('.')
private fun msToTime(ms: Long): String { val s = (ms / 1000L).coerceAtLeast(0L); return "%d:%02d".format(s / 60, s % 60) }

private fun formatCallDateTime(serverTime: String?): String {
    if (serverTime.isNullOrBlank()) return ""
    val date = parseDate(serverTime) ?: return ""
    return SimpleDateFormat("yyyy. MM. dd · HH:mm", Locale.KOREAN).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }.format(date)
}

private fun formatDuration(durationSec: Int?): String {
    val sec = durationSec ?: return "재생 길이 미상"
    return "${sec / 60}분 ${sec % 60}초"
}

private fun parseDate(s: String): Date? {
    val fmts = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    for (fmt in fmts) { try { return SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s) } catch (_: Exception) {} }
    return null
}

/* ─────────────────────────────────────────────────────
 * 색상
 * ───────────────────────────────────────────────────── */
private val HeaderBg = Color(0xFF3D4166)       // 연한 남색/그레이 계열
private val SectionDivider = Color(0xFFF2F2F7) // 섹션 구분 배경
private val BubbleCustomer = Color(0xFFF1F2F7) // 발신자: 연한 그레이
private val BubbleOwner = Color(0xFF3D4166)    // 수신자: 어두운 보라색