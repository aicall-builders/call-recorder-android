package com.callrecorder.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.R
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CallStatus
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onCallClick: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.refresh(silent = true)
    }

    val completedCalls = state.recentCalls
        .filter { it.status.equals(CallStatus.SUMMARIZED, true) || !it.summary.isNullOrBlank() }
        .take(10)
    val todaySchedules = state.schedules
        .filter { it.reminderEnabled && it.startAt?.startsWith(todayDate()) == true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DeepBrown900),
    ) {
        NotificationHeader(onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.White),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                NotificationSectionTitle("분석 완료 알림")
                Spacer(Modifier.height(8.dp))
            }
            if (completedCalls.isEmpty()) {
                item { EmptyNotificationBox("완료된 분석 알림이 없어요") }
            } else {
                items(completedCalls, key = { "done-${it.id}" }) { call ->
                    AnalysisDoneNotification(call = call, onClick = { onCallClick(call.id) })
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                NotificationSectionTitle("오늘 일정 알림")
                Spacer(Modifier.height(8.dp))
            }
            if (todaySchedules.isEmpty()) {
                item { EmptyNotificationBox("오늘 알림 설정된 일정이 없어요") }
            } else {
                items(todaySchedules, key = { "schedule-${it.id}" }) { event ->
                    ScheduleNotification(event = event)
                }
            }
        }
    }
}

@Composable
private fun NotificationHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 20.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Text(
            "알림",
            style = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = Color.White),
        )
    }
}

@Composable
private fun NotificationSectionTitle(title: String) {
    Text(
        title,
        style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.DeepBrown950),
    )
}

@Composable
private fun AnalysisDoneNotification(call: Call, onClick: () -> Unit) {
    NotificationCard(onClick = onClick) {
        Image(
            painter = painterResource(R.drawable.icon_call_up),
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "통화 분석이 완료됐어요",
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.DeepBrown950),
            )
            Text(
                call.summary?.takeIf { it.isNotBlank() } ?: callDisplayName(call),
                maxLines = 2,
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = AppColors.DeepBrown700),
            )
        }
    }
}

@Composable
private fun ScheduleNotification(event: CalendarEvent) {
    NotificationCard {
        Image(
            painter = painterResource(R.drawable.home_icon_add_calendar),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                event.title,
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.DeepBrown950),
            )
            Text(
                "${event.time} · ${event.description.ifBlank { "오늘 예정된 일정입니다." }}",
                maxLines = 2,
                style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = AppColors.DeepBrown700),
            )
        }
    }
}

@Composable
private fun NotificationCard(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        color = AppColors.DeepBrown50,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun EmptyNotificationBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.DeepBrown50),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = AppColors.DeepBrown500))
    }
}

private fun callDisplayName(call: Call): String =
    call.callerName?.takeIf { it.isNotBlank() }
        ?: call.callerNumber?.takeIf { it.isNotBlank() }
        ?: "통화 분석"

private fun todayDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
