package com.callrecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.callrecorder.app.ui.theme.AppColors

enum class FianoPopupActionType { FILL, OUTLINE }

@Composable
fun FianoPopupActionButton(
    label: String,
    type: FianoPopupActionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFill = type == FianoPopupActionType.FILL
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(999.dp),
        color = if (isFill) AppColors.DeepBrown900 else androidx.compose.ui.graphics.Color.White,
        border = if (isFill) null else BorderStroke(1.dp, AppColors.DeepBrown950),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (isFill) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFill) androidx.compose.ui.graphics.Color.White else AppColors.DeepBrown900,
                ),
            )
        }
    }
}

@Composable
fun FianoConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "확인",
    dismissLabel: String = "취소",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(296.dp),
            color = androidx.compose.ui.graphics.Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 10.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 32.dp, end = 24.dp),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.DeepBrown950,
                    ),
                )
                Text(
                    message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, top = 16.dp, end = 32.dp),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.DeepBrown800,
                    ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FianoPopupActionButton(
                        label = dismissLabel,
                        type = FianoPopupActionType.OUTLINE,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    FianoPopupActionButton(
                        label = confirmLabel,
                        type = FianoPopupActionType.FILL,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
