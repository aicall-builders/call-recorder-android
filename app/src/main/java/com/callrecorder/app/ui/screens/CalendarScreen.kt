package com.callrecorder.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.ui.theme.AppColors

@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    // 리다이렉트 URI (웹앱 OAuth 콜백)
    val redirectBase = "https://dsoh4vn0si08a.cloudfront.net/oauth"

    val providers = listOf(
        Triple("google", "Google 캘린더", Color(0xFF4285F4)),
        Triple("kakao",  "카카오 캘린더",  Color(0xFFFFE000)),
        Triple("naver",  "네이버 캘린더",  Color(0xFF03C75A)),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp),
    ) {
        Text(
            "캘린더 연동",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "예약 일정을 자동으로 캘린더에 등록하세요.",
            style = TextStyle(fontSize = 13.sp, color = AppColors.TextSecondary),
        )
        Spacer(Modifier.height(20.dp))

        if (state.loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.BrandBlue)
            }
        } else {
            val connectedProviders = state.connections.map { it.provider }.toSet()

            providers.forEach { (provider, label, color) ->
                val isConnected = provider in connectedProviders
                ProviderCard(
                    label = label,
                    color = color,
                    isConnected = isConnected,
                    onConnect = {
                        vm.getOAuthUrl(provider, "$redirectBase/$provider", java.util.UUID.randomUUID().toString()) { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    onDisconnect = { vm.disconnect(provider) }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "오류: ${state.error}",
                    style = TextStyle(fontSize = 12.sp, color = Color.Red),
                )
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { vm.loadConnections() }) {
                Text("새로고침", color = AppColors.BrandBlue)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    label: String,
    color: Color,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary),
                )
                Text(
                    text = if (isConnected) "연결됨" else "연결 안 됨",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = if (isConnected) Color(0xFF059669) else AppColors.TextSecondary,
                    ),
                )
            }
            if (isConnected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                ) {
                    Text("해제", fontSize = 12.sp)
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.BrandBlue),
                ) {
                    Text("연결", fontSize = 12.sp)
                }
            }
        }
    }
}
