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
    const val REFRESH_RECORDINGS = "refresh_recordings"
    const val ANALYSIS_STATUS = "analysis_status"
    const val UPLOAD = "upload"
    const val AUTO_SETTINGS = "auto_settings"
    const val CALLS_NAV = "calls_nav"
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
        targetKey = TourKeys.AUTO_SETTINGS,
        title = "자동 분석 설정",
        description = "자동 요약과 자동 필터링을 켜거나 끄며 분석 방식을 조정할 수 있어요.",
    ),
    TourStep(
        targetKey = TourKeys.REFRESH_RECORDINGS,
        title = "녹음 파일 새로 감지",
        description = "새로 생긴 통화 녹음이 있다면 이 버튼으로 다시 감지할 수 있어요.",
    ),
    TourStep(
        targetKey = TourKeys.UPLOAD,
        title = "통화 파일 직접 올리기",
        description = "녹음 파일을 직접 선택해서 분석 대기 목록에 추가할 수 있어요.",
    ),
    TourStep(
        targetKey = TourKeys.ANALYSIS_STATUS,
        title = "분석 승인과 진행 상태",
        description = "분석 상태 버튼을 누르거나 카드를 내리면 분석 중인 파일 리스트를 확인할 수 있어요.",
    ),
    TourStep(
        targetKey = TourKeys.CALLS_NAV,
        title = "통화관리",
        description = "분석이 완료된 파일은 통화관리에서 확인할 수 있습니다.",
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
            // 바깥 영역 터치는 앱 화면으로 새어나가지 않게 소비만 한다.
            .pointerInput(controller.currentIndex) {
                detectTapGestures { }
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
                    color = AppColors.SignalRed500,
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AppColors.FianoBlack900,
                        fontWeight = FontWeight.Normal,
                    ),
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
