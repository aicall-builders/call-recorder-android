package com.callrecorder.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.callrecorder.app.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlin.math.roundToInt

@Composable
fun SwipeRevealDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    revealWidth: Dp = 50.dp,
    deleteOnReveal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { revealWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(1f) }
    val containerHeight = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(0f) }
    var measuredHeightPx by remember { mutableFloatStateOf(0f) }

    fun settle(open: Boolean) {
        scope.launch {
            if (open && deleteOnReveal) {
                val exitTarget = if (widthPx > 0f) -widthPx else -revealPx * 4f
                joinAll(
                    launch {
                        offset.animateTo(
                            targetValue = exitTarget,
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                        )
                    },
                    launch {
                        contentAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        )
                    },
                )
                if (measuredHeightPx > 0f) {
                    containerHeight.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    )
                }
                onDelete()
                offset.snapTo(0f)
                contentAlpha.snapTo(1f)
                if (measuredHeightPx > 0f) {
                    containerHeight.snapTo(measuredHeightPx)
                }
            } else {
                offset.animateTo(
                    targetValue = if (open) -revealPx else 0f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .then(
                if (deleteOnReveal && measuredHeightPx > 0f) {
                    Modifier.height(with(density) { containerHeight.value.toDp() })
                } else {
                    Modifier
                },
            )
            .onSizeChanged {
                if (deleteOnReveal && measuredHeightPx == 0f && it.height > 0) {
                    measuredHeightPx = it.height.toFloat()
                    scope.launch { containerHeight.snapTo(measuredHeightPx) }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(if (deleteOnReveal) Color.Transparent else Color.White)
                .clickable { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "삭제",
                tint = AppColors.SignalRed600,
                modifier = Modifier.size(22.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { widthPx = it.width.toFloat() }
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .graphicsLayer { alpha = contentAlpha.value }
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            scope.launch { offset.stop() }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offset.snapTo((offset.value + dragAmount).coerceIn(-revealPx, 0f))
                            }
                        },
                        onDragEnd = {
                            settle(offset.value <= -revealPx / if (deleteOnReveal) 4f else 2f)
                        },
                        onDragCancel = {
                            settle(offset.value <= -revealPx / if (deleteOnReveal) 4f else 2f)
                        },
                    )
                },
        ) {
            content()
        }
    }
}
