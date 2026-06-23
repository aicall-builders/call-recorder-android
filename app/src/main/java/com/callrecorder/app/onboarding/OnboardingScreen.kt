package com.callrecorder.app.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
// 팔레트
// ─────────────────────────────────────────────────────────────
private val Bg          = Color(0xFFE5ECF6)
private val Primary     = Color(0xFF474B6B)
private val DarkNavy    = Color(0xFF343659)
private val TitleColor  = Color(0xFF1B1F2A)
private val DescColor   = Color(0xFF5A5F6C)
private val Device      = Color(0xFF5F6071)
private val Blue        = Color(0xFF2867E5)
private val AiBg        = Color(0xFFE8F2FF)
private val AiText      = Color(0xFF1C6BD4)
private val GrayText    = Color(0xFF99A1AF)
private val DotInactive = Color(0xFFFFFFFF)

private const val PAGE_COUNT = 5

private data class OnbPage(val pill: String, val title: String, val desc: String)

// 업종 선택은 별도 화면(BusinessTypeScreen)으로 분리 → 여기는 기능 소개 5장
private val pages = listOf(
    OnbPage("메인 화면", "통화 업무관리 프로세스",
        "통화를 분석하고 일정을 등록하고 고객을\n관리하는 자동 프로세스를 경험해보세요."),
    OnbPage("통화 관리 · 분석 완료", "통화 자동 분석",
        "메모나 기억할 필요 없어요.\n모든 통화를 AI가 수집하고 정리해요."),
    OnbPage("통화 상세 · AI 요약", "통화 핵심 요약카드",
        "액션·고객 성향 희망 조건까지,\n카드 한 장으로 요약해요."),
    OnbPage("일정관리 · 월 캘린더", "일정 자동 등록",
        "통화에서 잡힌 일정을 캘린더에\n자동 등록하고 알림까지 챙겨드려요."),
    OnbPage("고객 관리", "나만의 통화 DB",
        "통화자와의 히스토리를 맥락에 맞게\n자동으로 분석하고 정리해줘요."),
)

// ─────────────────────────────────────────────────────────────
// 메인 — 한 화면 안에서 step 만 바뀜 (스와이프 X, 버튼으로만)
// ─────────────────────────────────────────────────────────────
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    fun next() {
        if (step >= PAGE_COUNT - 1) onFinish() else step++
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {

            Crossfade(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onbStep",
            ) { s ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Pill(pages[s].pill)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        pages[s].title,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = TitleColor),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pages[s].desc,
                        style = TextStyle(fontSize = 16.sp, color = DescColor, lineHeight = 24.sp),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    when (s) {
                        0 -> HomeMockup()
                        1 -> CallListMockup()
                        2 -> SummaryMockup()
                        3 -> CalendarMockup()
                        4 -> CustomerMockup()
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Dots(current = step)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Primary)
                        .clickable { next() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (step == PAGE_COUNT - 1) "시작하기" else "다음",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .noRippleClickable { onFinish() },
                ) {
                    Text("건너뛰기", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Primary))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 공통 조각
// ─────────────────────────────────────────────────────────────
@Composable
private fun Pill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary))
    }
}

@Composable
private fun Dots(current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { i ->
            val active = i == current
            val w by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotW")
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = w, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) DarkNavy else DotInactive),
            )
        }
    }
}

@Composable
private fun DeviceFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Device),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("AI 통화 비서", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
                Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f)))
            }
            content()
        }
    }
}

private val ChipTextStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)

@Composable
private fun MiniChip(text: String, filled: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (filled) Modifier.background(DarkNavy)
                else Modifier.background(Color.White).border(0.8.dp, DarkNavy, RoundedCornerShape(999.dp))
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = ChipTextStyle.copy(color = if (filled) Color.White else DarkNavy))
    }
}

@Composable
private fun CallRow(name: String, sub: String, time: String, badge: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFEDEFF5)), contentAlignment = Alignment.Center) {
            Text(name.take(1), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5F5F5F)))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy))
                Spacer(Modifier.width(4.dp))
                Text(time, style = TextStyle(fontSize = 10.sp, color = Blue))
            }
            Text(sub, style = TextStyle(fontSize = 10.sp, color = Color(0xFF7A7F8C)))
        }
        if (badge != null) {
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(DarkNavy).padding(horizontal = 8.dp, vertical = 3.dp),
            ) { Text(badge, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White)) }
        }
    }
}

// ── 1. 홈 대시보드 ──
@Composable
private fun HomeMockup() {
    DeviceFrame {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Text("2026년 6월 9일 화요일", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White))
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("통화 분석 대기 24건", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavy))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("통화 자동 요약 OFF", false)
                MiniChip("중요 통화 필터링 ON", true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color.White)
                .padding(12.dp),
        ) {
            Text("주요 분석 통화", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy))
            Spacer(Modifier.height(4.dp))
            CallRow("김민준", "예약 변경 요청", "13:22")
            CallRow("이태양", "메뉴 문의", "13:20")
        }
    }
}

// ── 2. 통화 관리 ──
@Composable
private fun CallListMockup() {
    DeviceFrame {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(999.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("전화번호 또는 요약 검색", style = TextStyle(fontSize = 11.sp, color = GrayText))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color.White)
                .padding(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("전체 12", true)
                MiniChip("예약 6", false)
                MiniChip("문의 4", false)
            }
            Spacer(Modifier.height(8.dp))
            CallRow("김민준", "매물 방문 문의", "13:22", badge = "예약")
            Box(
                Modifier.fillMaxWidth().padding(start = 34.dp, end = 4.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(999.dp)).background(AiBg).padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text("✦ 18일 저녁 7시, 매물 방문을 캘린더에 등록했어요.",
                    style = TextStyle(fontSize = 10.sp, color = AiText))
            }
            CallRow("02-6959-1842", "매물 방문 문의", "13:22", badge = "예약")
        }
    }
}

// ── 3. AI 요약 카드 ──
@Composable
private fun SummaryMockup() {
    DeviceFrame {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Text("010-4762-0815", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White))
            Text("2026. 06. 04   17:07", style = TextStyle(fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f)))
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color.White)
                .padding(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("통화 분석", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy))
                Text("전문", style = TextStyle(fontSize = 12.sp, color = GrayText))
            }
            Spacer(Modifier.height(12.dp))
            Text("✦ AI 요약", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AiText))
            Spacer(Modifier.height(8.dp))
            SummaryLine("액션", "추가 매물 정보 요청")
            SummaryLine("희망 매물", "주변 추천 교통")
            SummaryLine("희망 동네", "강남")
            SummaryLine("방문 일정", "화요일 저녁 7시")
            SummaryLine("연락 방식", "통화 후 문자")
        }
    }
}

@Composable
private fun SummaryLine(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, modifier = Modifier.width(64.dp), style = TextStyle(fontSize = 11.sp, color = GrayText))
        Text(v, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = DarkNavy))
    }
}

// ── 4. 미니 캘린더 ──
@Composable
private fun CalendarMockup() {
    DeviceFrame {
        Column(
            Modifier.fillMaxWidth().padding(8.dp)
                .clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp),
        ) {
            Text("2026년 6월", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = TextStyle(fontSize = 10.sp, color = GrayText))
                }
            }
            Spacer(Modifier.height(4.dp))
            val padded = List(1) { 0 } + (1..30).toList()
            padded.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val d = week.getOrNull(i) ?: 0
                        val highlight = d == 9
                        Box(
                            modifier = Modifier.weight(1f).height(28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (d != 0) {
                                Box(
                                    modifier = Modifier.size(22.dp).clip(CircleShape)
                                        .then(if (highlight) Modifier.background(DarkNavy) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("$d", style = TextStyle(
                                        fontSize = 10.sp,
                                        color = if (highlight) Color.White else DarkNavy,
                                        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                                    ))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Blue))
                Spacer(Modifier.width(4.dp))
                Text("통화 자동 등록", style = TextStyle(fontSize = 9.sp, color = Color(0xFF7A7F8C)))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                Spacer(Modifier.width(4.dp))
                Text("수동 등록", style = TextStyle(fontSize = 9.sp, color = Color(0xFF7A7F8C)))
            }
        }
    }
}

// ── 5. 고객 DB ──
@Composable
private fun CustomerMockup() {
    DeviceFrame {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(999.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("고객 이름 또는 전화번호 검색", style = TextStyle(fontSize = 11.sp, color = GrayText))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color.White)
                .padding(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniChip("전체 24", true)
                MiniChip("VIP 4", false)
                MiniChip("신규 4", false)
            }
            Spacer(Modifier.height(8.dp))
            CustomerRow("김민준", "VIP", "예약 변경 요청 · 12회 통화")
            CustomerRow("박서연", "단골", "납품 일정 확인 · 8회 통화")
            CustomerRow("이태양", "신규", "메뉴 문의 · 1회 통화")
        }
    }
}

@Composable
private fun CustomerRow(name: String, tag: String, sub: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFEDEFF5)), contentAlignment = Alignment.Center) {
            Text(name.take(1), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5F5F5F)))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy))
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(AiBg).padding(horizontal = 5.dp, vertical = 1.dp)) {
                    Text(tag, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AiText))
                }
            }
            Text(sub, style = TextStyle(fontSize = 10.sp, color = Color(0xFF7A7F8C)))
        }
    }
}

@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}