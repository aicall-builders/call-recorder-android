package com.callrecorder.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.CallCategory
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.onboarding.FeatureTourOverlay
import com.callrecorder.app.onboarding.HomeTourSteps
import com.callrecorder.app.onboarding.TourKeys
import com.callrecorder.app.onboarding.markFeatureTourDone
import com.callrecorder.app.onboarding.rememberFeatureTourController
import com.callrecorder.app.onboarding.shouldShowFeatureTour
import com.callrecorder.app.onboarding.tourTarget
import com.callrecorder.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private val NavBarBg      = Color(0xFF3D4D6B)
private val NavBarDivider = Color(0xFF4A5A78)
private val NavSelected   = Color(0xFF6FA8F0)
private val NavUnselected = Color(0xFFAAB6C8)

@Composable
fun MainScreen(
    onCallClick: (String) -> Unit,
    onLoggedOut: () -> Unit,
    onChangeStore: () -> Unit,
    homeVm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    var selected by remember { mutableStateOf(BottomTab.HOME) }
    var showApproval by remember { mutableStateOf(false) }
    var callDetailId by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = CallRecorderApp.instance.container
    val recordingDao = container.recordingDao

    // ── 기능 투어 컨트롤러 ──
    val tourController = rememberFeatureTourController(HomeTourSteps)

    // 첫 진입(홈) 때 한 번만 자동 시작
    LaunchedEffect(Unit) {
        if (context.shouldShowFeatureTour()) {
            delay(350)  // 타겟 위치 측정 시간 한 박자
            if (selected == BottomTab.HOME && !showApproval) {
                tourController.start()
            }
        }
    }

    val handleCallClick: (String) -> Unit = { callId ->
        selected = BottomTab.CALLS
        callDetailId = callId
    }

    // 파일 피커 런처 — Scaffold 밖 선언
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    var name: String? = null
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && idx >= 0) {
                            name = cursor.getString(idx)
                        }
                    }
                    val fileName = name ?: "upload_${UUID.randomUUID()}.m4a"

                    val destFile = File(context.filesDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val storeId = container.storeRepo.activeStoreId() ?: ""

                    recordingDao.insert(
                        RecordingEntity(
                            filePath = destFile.absolutePath,
                            fileName = fileName,
                            fileSize = destFile.length(),
                            durationSeconds = 0,
                            callStartedAtMillis = System.currentTimeMillis(),
                            counterpartNumber = null,
                            storeId = storeId,
                            status = "AWAITING_APPROVAL",
                            category = CallCategory.UNCLASSIFIED,
                        )
                    )
                    homeVm.refresh()
                    delay(300)
                    showApproval = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // 좌표 정합을 위해 Scaffold와 투어 오버레이를 같은 루트 Box로 묶는다
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = AppColors.Background,
            bottomBar = {
                BottomTabBar(
                    selected = selected,
                    onSelect = { tab ->
                        selected = tab
                        if (tab != BottomTab.CALLS) callDetailId = null
                    },
                    modifier = Modifier.tourTarget(tourController, TourKeys.BOTTOM_NAV),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (showApproval) {
                    PendingApprovalScreen(onBack = {
                        showApproval = false
                        homeVm.refresh()  // 추가
                    })
                } else {
                    when (selected) {
                        BottomTab.HOME -> HomeScreen(
                            vm = homeVm,
                            onCallClick = handleCallClick,
                            onSettings = { selected = BottomTab.SETTINGS },
                            onApprovalClick = { showApproval = true },
                            onUploadClick = { uploadLauncher.launch("audio/*") },
                            onSeeAllCalls = { selected = BottomTab.CALLS },
                            onSeeAllSchedules = { selected = BottomTab.CALENDAR },
                            tourController = tourController,
                        )
                        BottomTab.CALLS -> {
                            if (callDetailId != null) {
                                CallSummaryDetailScreen(
                                    callId = callDetailId!!,
                                    onBack = { callDetailId = null },
                                )
                            } else {
                                CallSummaryListScreen(
                                    onCallClick = { callId -> callDetailId = callId },
                                )
                            }
                        }
                        BottomTab.CUSTOMERS -> CustomerScreen()
                        BottomTab.CALENDAR -> InternalCalendarScreen()
                        BottomTab.SETTINGS -> SettingsScreen(
                            onBack = { selected = BottomTab.HOME },
                            onChangeStore = onChangeStore,
                            onLoggedOut = onLoggedOut,
                        )
                    }
                }
            }
        }

        // ── 기능 투어 오버레이 (최상단) ──
        FeatureTourOverlay(
            controller = tourController,
            onFinish = { context.markFeatureTourDone() },
        )
    }
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    CALLS("통화관리", Icons.Filled.Mic),
    CUSTOMERS("고객", Icons.Filled.People),
    CALENDAR("캘린더", Icons.Filled.CalendarMonth),
    SETTINGS("설정", Icons.Filled.Settings),
}

@Composable
private fun BottomTabBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        HorizontalDivider(color = NavBarDivider, thickness = 0.5.dp)
        NavigationBar(
            containerColor = NavBarBg,
            tonalElevation = 0.dp,
        ) {
            BottomTab.values().forEach { tab ->
                val isSelected = tab == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = {
                        Text(
                            tab.label,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NavSelected,
                        selectedTextColor = NavSelected,
                        unselectedIconColor = NavUnselected,
                        unselectedTextColor = NavUnselected,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}