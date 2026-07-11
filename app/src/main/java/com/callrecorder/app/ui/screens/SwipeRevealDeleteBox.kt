package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.callrecorder.app.ui.theme.AppColors
import kotlin.math.roundToInt

@Composable
fun SwipeRevealDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    revealWidth: Dp = 50.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { revealWidth.toPx() }
    var offsetPx by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
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
                .offset { IntOffset(offsetPx.roundToInt(), 0) }
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetPx = (offsetPx + dragAmount).coerceIn(-revealPx, 0f)
                        },
                        onDragEnd = {
                            offsetPx = if (offsetPx <= -revealPx / 2f) -revealPx else 0f
                        },
                        onDragCancel = {
                            offsetPx = if (offsetPx <= -revealPx / 2f) -revealPx else 0f
                        },
                    )
                },
        ) {
            content()
        }
    }
}
