package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingApprovalScreen(
    onBack: () -> Unit,
    vm: PendingApprovalViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val allSelected = state.recordings.isNotEmpty() && selectedIds.size == state.recordings.size

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedIds.isEmpty()) "업로드 승인"
                        else "${selectedIds.size}건 선택됨",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
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

                    // 선택 건 액션 버튼
                    if (selectedIds.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 선택 거절
                            OutlinedButton(
                                onClick = {
                                    selectedIds.forEach { vm.rejectOne(it) }
                                    selectedIds = emptySet()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC44545)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC44545)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("거절 (${selectedIds.size})", fontSize = 13.sp)
                            }
                            // 선택 승인
                            Button(
                                onClick = {
                                    selectedIds.forEach { vm.approveOne(it) }
                                    selectedIds = emptySet()
                                    onBack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.BrandBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("승인 (${selectedIds.size})", fontSize = 13.sp)
                            }
                        }
                    } else {
                        // 전체 승인 버튼
                        Button(
                            onClick = { vm.approveAll(); onBack() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.BrandBlue),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("전체 승인 (${state.recordings.size}건)", fontSize = 13.sp)
                        }
                    }
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.BrandBlue)
                }
            } else if (state.recordings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.recordings, key = { it.id }) { rec ->
                        val isSelected = rec.id in selectedIds
                        ApprovalCard(
                            recording = rec,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedIds = if (isSelected) selectedIds - rec.id
                                else selectedIds + rec.id
                            },
                            onApprove = { vm.approveOne(rec.id) },
                            onReject = { vm.rejectOne(rec.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    recording: RecordingEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) AppColors.BrandBlue else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onToggleSelect),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AppColors.BrandBlue.copy(alpha = 0.06f) else AppColors.Surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 체크박스
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = AppColors.BrandBlue),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))

            // 정보
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

            // 개별 승인/거절 버튼
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onReject,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFFEE7E7), RoundedCornerShape(8.dp)),
                ) {
                    Icon(Icons.Filled.Close, "거절", tint = Color(0xFFC44545), modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onApprove,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE3F2FF), RoundedCornerShape(8.dp)),
                ) {
                    Icon(Icons.Filled.Check, "승인", tint = AppColors.BrandBlue, modifier = Modifier.size(18.dp))
                }
            }
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