package com.callrecorder.app.onboarding


import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.callrecorder.app.ui.theme.AppColors

// ─────────────────────────────────────────────────────────────
// 1) 영구 저장 플래그 (한 번 본 사용자는 다시 안 보이게)
// ─────────────────────────────────────────────────────────────
private const val TOUR_PREFS = "feature_tour_prefs"
private const val KEY_HOME_TOUR_DONE = "home_tour_done"

fun Context.shouldShowFeatureTour(): Boolean =
    !getSharedPreferences(TOUR_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_HOME_TOUR_DONE, false)

fun Context.markFeatureTourDone() {
    getSharedPreferences(TOUR_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_HOME_TOUR_DONE, true)
        .apply()
}

fun Context.resetFeatureTour() {
    getSharedPreferences(TOUR_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_HOME_TOUR_DONE)
        .apply()
}

// ─────────────────────────────────────────────────────────────
// 2) 투어 대상 키 (홈 화면 기준 - 필요하면 추가/수정)
// ─────────────────────────────────────────────────────────────
object TourKeys {
    const val UPLOAD = "upload"
    const val IMPORTANT_FILTER = "important_filter"
    const val RECENT_CALLS = "recent_calls"
    const val BOTTOM_NAV = "bottom_nav"
}

// ─────────────────────────────────────────────────────────────
// 3) 단계 정의
// ─────────────────────────────────────────────────────────────
data class TourStep(
    val targetKey: String,
    val title: String,
    val description: String,
)

val HomeTourSteps: List<TourStep> = listOf(
    TourStep(
        targetKey = TourKeys.UPLOAD,
        title = "통화 파일 올리기",
        description = "여기로 통화 녹음을 올리면 자동으로 텍스트 변환·요약·예약정보 추출까지 한 번에 끝나요.",
    ),
    TourStep(
        targetKey = TourKeys.IMPORTANT_FILTER,
        title = "중요 통화만 골라보기",
        description = "이 토글을 켜면 예약·문의처럼 중요한 통화만 추려서 보여줘요.",
    ),
    TourStep(
        targetKey = TourKeys.RECENT_CALLS,
        title = "분석된 통화 확인",
        description = "최근 분석된 통화가 여기 카드로 떠요. 탭하면 전체 내용·요약·메모를 볼 수 있어요.",
    ),
    TourStep(
        targetKey = TourKeys.BOTTOM_NAV,
        title = "메뉴 이동",
        description = "통화목록·캘린더·고객 관리는 아래 메뉴에서 바로 이동할 수 있어요.",
    ),
)

// ─────────────────────────────────────────────────────────────
// 4) 컨트롤러
// ─────────────────────────────────────────────────────────────
class FeatureTourController(val steps: List<TourStep>) {
    private val targetRects = mutableStateMapOf<String, Rect>()
    var currentIndex by mutableIntStateOf(-1)
        private set

    val isActive: Boolean get() = currentIndex in steps.indices
    val currentStep: TourStep? get() = steps.getOrNull(currentIndex)
    val currentRect: Rect? get() = currentStep?.let { targetRects[it.targetKey] }
    val isLast: Boolean get() = currentIndex == steps.lastIndex

    fun updateRect(key: String, rect: Rect) { targetRects[key] = rect }

    fun start() { if (steps.isNotEmpty()) currentIndex = 0 }

    fun next(onFinish: () -> Unit) {
        if (currentIndex < steps.lastIndex) currentIndex++
        else finish(onFinish)
    }

    fun finish(onFinish: () -> Unit) {
        currentIndex = -1
        onFinish()
    }
}

@Composable
fun rememberFeatureTourController(steps: List<TourStep>): FeatureTourController =
    remember { FeatureTourController(steps) }

// ─────────────────────────────────────────────────────────────
// 5) 대상 등록용 Modifier — 강조하고 싶은 컴포저블에 붙이면 됨
//    예) Modifier.tourTarget(controller, TourKeys.UPLOAD)
// ─────────────────────────────────────────────────────────────
fun Modifier.tourTarget(controller: FeatureTourController, key: String): Modifier =
    this.onGloballyPositioned { coords ->
        controller.updateRect(key, coords.boundsInRoot())
    }

// ─────────────────────────────────────────────────────────────
// 6) 오버레이 — 화면 최상단에 한 번만 깔면 됨
// ─────────────────────────────────────────────────────────────
@Composable
fun FeatureTourOverlay(
    controller: FeatureTourController,
    onFinish: () -> Unit,
) {
    if (!controller.isActive) return
    val step = controller.currentStep ?: return
    val rect = controller.currentRect

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(100f)
            // 스포트라이트 밖 영역을 탭하면 다음 단계로
            .pointerInput(controller.currentIndex) {
                detectTapGestures { controller.next(onFinish) }
            }
    ) {
        val density = LocalDensity.current
        val padPx = with(density) { 8.dp.toPx() }
        val cornerPx = with(density) { 14.dp.toPx() }
        val screenHpx = with(density) { maxHeight.toPx() }

        // 6-1) 어둡게 + 구멍
        Canvas(Modifier.fillMaxSize()) {
            val scrim = AppColors.FianoBlack950.copy(alpha = 0.74f)
            if (rect != null && rect.width > 0f && rect.height > 0f) {
                val spot = Rect(
                    left = rect.left - padPx,
                    top = rect.top - padPx,
                    right = rect.right + padPx,
                    bottom = rect.bottom + padPx,
                )
                val path = Path().apply {
                    addRect(Rect(Offset.Zero, size))
                    addRoundRect(RoundRect(spot, CornerRadius(cornerPx, cornerPx)))
                    fillType = PathFillType.EvenOdd
                }
                drawPath(path, scrim)
                drawRoundRect(
                    color = AppColors.Surface.copy(alpha = 0.95f),
                    topLeft = Offset(spot.left, spot.top),
                    size = Size(spot.width, spot.height),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = 2.dp.toPx()),
                )
            } else {
                drawRect(scrim)
            }
        }

        // 6-2) 말풍선 위치 — 빈 공간 더 넓은 쪽으로
        val placeBelow = rect == null ||
                (screenHpx - rect.bottom) >= rect.top
        val gap = 16.dp

        val cardAlign = when {
            rect == null -> Modifier.align(Alignment.Center)
            placeBelow -> Modifier
                .align(Alignment.TopCenter)
                .padding(top = with(density) { (rect.bottom + padPx).toDp() } + gap)
            else -> Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { (screenHpx - rect.top + padPx).toDp() } + gap)
        }

        Box(
            cardAlign
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            TourTooltipCard(
                step = step,
                index = controller.currentIndex,
                total = controller.steps.size,
                isLast = controller.isLast,
                onSkip = { controller.finish(onFinish) },
                onNext = { controller.next(onFinish) },
            )
        }
    }
}

@Composable
private fun TourTooltipCard(
    step: TourStep,
    index: Int,
    total: Int,
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Card(
        // 카드 탭이 뒤 오버레이로 새어나가지 않게 소비
        modifier = Modifier.pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Box(Modifier.padding(20.dp)) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = step.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = step.description,
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StepDots(index = index, total = total)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isLast) {
                            TextButton(onClick = onSkip) {
                                Text("건너뛰기", color = AppColors.FianoBlack400, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        TextButton(onClick = onNext) {
                            Text(
                                text = if (isLast) "시작하기" else "다음",
                                color = AppColors.Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepDots(index: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            val active = i == index
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .then(
                        Modifier.pointerInput(Unit) {} // no-op, 시각 표시만
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = if (active) AppColors.Accent else AppColors.FianoBlack200
                    )
                }
            }
        }
    }
}
