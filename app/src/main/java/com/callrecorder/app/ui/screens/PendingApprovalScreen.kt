package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PendingApprovalScreen(
    refreshKey: Int = 0,
    onBack: () -> Unit,
    vm: PendingApprovalViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val allSelected = state.recordings.isNotEmpty() && selectedIds.size == state.recordings.size
    val actionCount = if (selectedIds.isNotEmpty()) selectedIds.size else state.recordings.size

    LaunchedEffect(refreshKey) {
        selectedIds = emptySet()
        vm.load()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .background(AppColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (selectedIds.isEmpty()) "업로드 승인"
                    else "${selectedIds.size}건 선택됨",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary),
                )
                Text(
                    "닫기",
                    modifier = Modifier.clickable { onBack() },
                    style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary),
                )
            }

            if (state.recordings.isNotEmpty()) {
                // 전체 선택 + 액션 버튼 행
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 전체 선택 체크박스
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            selectedIds = if (allSelected) emptySet()
                            else state.recordings.map { it.id }.toSet()
                        }
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) state.recordings.map { it.id }.toSet()
                                else emptySet()
                            },
                            colors = CheckboxDefaults.colors(checkedColor = AppColors.BrandBlue),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "전체 선택",
                            style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary),
                        )
                    }

                    if (selectedIds.isNotEmpty()) {
                        Text(
                            "${selectedIds.size}건 선택됨",
                            style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary),
                        )
                    }
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.BrandBlue)
                }
            } else if (state.recordings.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", style = TextStyle(fontSize = 48.sp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "대기 중인 통화가 없어요",
                            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.recordings, key = { it.id }) { rec ->
                        val isSelected = rec.id in selectedIds
                        ApprovalCard(
                            recording = rec,
                            isSelected = isSelected,
                            isDuplicate = rec.id in state.duplicateIds,
                            onToggleSelect = {
                                selectedIds = if (isSelected) selectedIds - rec.id
                                else selectedIds + rec.id
                            },
                        )
                    }
                }
            }
        }

        if (state.recordings.isNotEmpty()) {
            ApprovalActionBar(
                deleteLabel = if (selectedIds.isNotEmpty()) "삭제 ${actionCount}건" else "삭제",
                approveLabel = if (selectedIds.isNotEmpty()) "승인 ${actionCount}건" else "승인",
                onDelete = {
                    if (selectedIds.isNotEmpty()) {
                        selectedIds.forEach { vm.rejectOne(it) }
                        selectedIds = emptySet()
                    }
                },
                onApprove = {
                    if (selectedIds.isNotEmpty()) {
                        selectedIds.forEach { vm.approveOne(it) }
                    } else {
                        vm.approveAll()
                    }
                    selectedIds = emptySet()
                    onBack()
                },
            )
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ApprovalActionBar(
    deleteLabel: String,
    approveLabel: String,
    onDelete: () -> Unit,
    onApprove: () -> Unit,
) {
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ApprovalActionButton(
                label = deleteLabel,
                fill = false,
                enabled = deleteLabel != "삭제",
                onClick = onDelete,
                modifier = Modifier.weight(1f),
            )
            ApprovalActionButton(
                label = approveLabel,
                fill = true,
                onClick = onApprove,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ApprovalActionButton(
    label: String,
    fill: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val strokeColor = if (enabled) AppColors.DeepBrown900 else AppColors.DeepBrown200
    val labelColor = when {
        fill -> Color.White
        enabled -> AppColors.DeepBrown900
        else -> AppColors.DeepBrown300
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (fill) AppColors.DeepBrown900 else Color.White,
        shape = RoundedCornerShape(999.dp),
        border = if (fill) null else BorderStroke(1.dp, strokeColor),
        modifier = modifier.height(48.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = TextStyle(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (fill) FontWeight.Bold else FontWeight.Medium,
                    color = labelColor,
                ),
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    recording: RecordingEntity,
    isSelected: Boolean,
    isDuplicate: Boolean,
    onToggleSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelect() },
            colors = CheckboxDefaults.colors(checkedColor = AppColors.BrandBlue),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recording.counterpartNumber ?: "발신번호 없음",
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = formatMillis(recording.callStartedAtMillis),
                style = TextStyle(fontSize = 12.sp, color = AppColors.TextSecondary),
            )
            Text(
                text = formatDuration(recording.durationSeconds),
                style = TextStyle(fontSize = 12.sp, color = AppColors.TextSecondary),
            )
        }
        if (isDuplicate) {
            Text(
                "중복파일",
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.SignalRed700,
                ),
            )
        }
    }
}

private fun formatMillis(millis: Long): String {
    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.KOREAN)
    return sdf.format(Date(millis))
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}분 ${s}초" else "${s}초"
}
