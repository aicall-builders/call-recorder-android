package com.callrecorder.app.ui.screens

import android.app.NotificationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.local.CallCategory
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val NavBarBg = AppColors.DeepBrown900

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
    var noteEditCallId by remember { mutableStateOf<String?>(null) }
    var noteEditTitle by remember { mutableStateOf("통화 메모") }
    var approvalRefreshKey by remember { mutableStateOf(0) }
    var showExternalCalendarSheet by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var openCallsOnPendingTab by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = CallRecorderApp.instance.container
    val recordingDao = container.recordingDao
    val homeState by homeVm.state.collectAsState()
    var hasSystemNotification by remember { mutableStateOf(context.hasActiveFianoSummaryNotification()) }
    val hasDataNotification = remember(homeState.recentCalls, homeState.schedules) {
        val today = mainTodayDate()
        homeState.recentCalls.any {
            it.status.equals("summarized", true) || !it.summary.isNullOrBlank()
        } || homeState.schedules.any {
            it.reminderEnabled && it.startAt?.startsWith(today) == true
        }
    }
    val hasNotification = hasDataNotification || hasSystemNotification

    LaunchedEffect(showNotifications, homeState.recentCalls, homeState.schedules) {
        hasSystemNotification = context.hasActiveFianoSummaryNotification()
    }

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
        noteEditCallId = null
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

                    val localFileName = "${System.currentTimeMillis()}_${fileName}"
                    val destFile = File(context.filesDir, localFileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val storeId = container.storeRepo.activeStoreId() ?: ""
                    val durationSeconds = readAudioDurationSeconds(destFile.absolutePath)

                    val insertedId = recordingDao.insert(
                        RecordingEntity(
                            filePath = destFile.absolutePath,
                            fileName = fileName,
                            fileSize = destFile.length(),
                            durationSeconds = durationSeconds,
                            callStartedAtMillis = System.currentTimeMillis(),
                            counterpartNumber = null,
                            storeId = storeId,
                            status = RecordingStatus.AWAITING_APPROVAL,
                            category = CallCategory.UNCLASSIFIED,
                        )
                    )
                    if (insertedId == -1L) {
                        recordingDao.findByPath(destFile.absolutePath)?.let { existing ->
                            recordingDao.updateStatus(existing.id, RecordingStatus.AWAITING_APPROVAL)
                        }
                    }
                    homeVm.refresh()
                    approvalRefreshKey += 1
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
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                if (showNotifications) {
                    NotificationScreen(
                        onBack = { showNotifications = false },
                        onCallClick = { callId ->
                            showNotifications = false
                            noteEditCallId = null
                            selected = BottomTab.CALLS
                            callDetailId = callId
                        },
                        vm = homeVm,
                    )
                } else if (noteEditCallId != null) {
                    CallNoteEditScreen(
                        callId = noteEditCallId!!,
                        callTitle = noteEditTitle,
                        onBack = { noteEditCallId = null },
                    )
                } else {
                    when (selected) {
                        BottomTab.HOME -> HomeScreen(
                            vm = homeVm,
                            onCallClick = handleCallClick,
                            onSettings = { selected = BottomTab.SETTINGS },
                            onApprovalClick = {
                                approvalRefreshKey += 1
                                showApproval = true
                            },
                            onUploadClick = { uploadLauncher.launch("audio/*") },
                            onUploadingClick = {
                                callDetailId = null
                                openCallsOnPendingTab = true
                                selected = BottomTab.CALLS
                            },
                            onSeeAllCalls = {
                                openCallsOnPendingTab = false
                                selected = BottomTab.CALLS
                            },
                            onSeeAllSchedules = { selected = BottomTab.CALENDAR },
                            onSeeAllCustomers = { selected = BottomTab.CUSTOMERS },
                            onNotificationClick = { showNotifications = true },
                            hasNotification = hasNotification,
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
                                    onNotificationClick = { showNotifications = true },
                                    hasNotification = hasNotification,
                                    startOnPendingTab = openCallsOnPendingTab,
                                    vm = homeVm,
                                )
                            }
                        }
                        BottomTab.CUSTOMERS -> CustomerScreen(
                            onCallDetailClick = handleCallClick,
                            onNotificationClick = { showNotifications = true },
                            hasNotification = hasNotification,
                            onCustomerPinnedChanged = { homeVm.refresh(silent = true) },
                        )
                        BottomTab.CALENDAR -> InternalCalendarScreen(
                            onCallDetailClick = handleCallClick,
                            onMemoImageClick = { callId, title ->
                                noteEditCallId = callId
                                noteEditTitle = title.ifBlank { "통화 메모" }
                            },
                            onNotificationClick = { showNotifications = true },
                            hasNotification = hasNotification,
                            onScheduleChanged = { homeVm.refresh(silent = true) },
                        )
                        BottomTab.SETTINGS -> SettingsScreen(
                            onBack = { selected = BottomTab.HOME },
                            onChangeStore = onChangeStore,
                            onLoggedOut = onLoggedOut,
                            onExternalCalendarClick = { showExternalCalendarSheet = true },
                            onNotificationClick = { showNotifications = true },
                            hasNotification = hasNotification,
                        )
                    }
                }
            }
        }

        if (!showNotifications) {
            BottomTabBar(
                selected = selected,
                onSelect = { tab ->
                    noteEditCallId = null
                    if (tab == BottomTab.CALLS) openCallsOnPendingTab = false
                    selected = tab
                    if (tab != BottomTab.CALLS) callDetailId = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .tourTarget(tourController, TourKeys.BOTTOM_NAV),
            )
        }

        if (showApproval) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable {
                        showApproval = false
                        homeVm.refresh()
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Bottom)
                        .clickable { },
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = AppColors.Surface,
                ) {
                    PendingApprovalScreen(
                        refreshKey = approvalRefreshKey,
                        onBack = {
                            showApproval = false
                            homeVm.refresh()
                        },
                    )
                }
            }
        }

        if (showExternalCalendarSheet) {
            ExternalCalendarBottomSheetOverlay(
                onDismiss = { showExternalCalendarSheet = false },
            )
        }

        // ── 기능 투어 오버레이 (최상단) ──
        if (!showApproval && !showExternalCalendarSheet && !showNotifications) {
            FeatureTourOverlay(
                controller = tourController,
                onFinish = { context.markFeatureTourDone() },
            )
        }
    }
}

private fun readAudioDurationSeconds(path: String): Int {
    return runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val millis = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            (millis / 1000L).toInt()
        } finally {
            retriever.release()
        }
    }.getOrDefault(0)
}

private fun mainTodayDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

enum class BottomTab(val label: String, val pathData: String) {
    HOME("홈", IconPaths.HOME),
    CALLS("통화관리", IconPaths.CALL),
    CUSTOMERS("고객관리", IconPaths.PERSON),
    CALENDAR("일정관리", IconPaths.CALENDAR),
    SETTINGS("설정", IconPaths.SETTING),
}

/** 시안 SVG(viewBox 0 0 20 20, stroke 1.667)의 path 데이터 */
private object IconPaths {
    const val HOME =
        "M12.5 17.5V10.8333C12.5 10.6123 12.4122 10.4004 12.2559 10.2441C12.0996 10.0878 11.8877 10 11.6667 10H8.33333C8.11232 10 7.90036 10.0878 7.74408 10.2441C7.5878 10.4004 7.5 10.6123 7.5 10.8333V17.5 " +
                "M2.5 8.33335C2.49994 8.0909 2.55278 7.85137 2.65482 7.63144C2.75687 7.41152 2.90566 7.21651 3.09083 7.06001L8.92417 2.06085C9.22499 1.8066 9.60613 1.66711 10 1.66711C10.3939 1.66711 10.775 1.8066 11.0758 2.06085L16.9092 7.06001C17.0943 7.21651 17.2431 7.41152 17.3452 7.63144C17.4472 7.85137 17.5001 8.0909 17.5 8.33335V15.8333C17.5 16.2754 17.3244 16.6993 17.0118 17.0119C16.6993 17.3244 16.2754 17.5 15.8333 17.5H4.16667C3.72464 17.5 3.30072 17.3244 2.98816 17.0119C2.67559 16.6993 2.5 16.2754 2.5 15.8333V8.33335Z"
    const val CALL =
        "M12.4812 4.12753C13.3184 4.29088 14.0879 4.70036 14.6911 5.30355C15.2943 5.90674 15.7037 6.67621 15.8671 7.51346 " +
                "M12.4812 0.69873C14.2207 0.891974 15.8427 1.67094 17.0811 2.90774C18.3195 4.14453 19.1005 5.76564 19.2959 7.50489 " +
                "M18.4387 14.3453V16.9169C18.4397 17.1557 18.3908 17.392 18.2951 17.6107C18.1995 17.8294 18.0592 18.0258 17.8833 18.1872C17.7074 18.3486 17.4997 18.4715 17.2736 18.5479C17.0474 18.6244 16.8078 18.6528 16.57 18.6313C13.9323 18.3447 11.3985 17.4434 9.17238 15.9997C7.10124 14.6836 5.34527 12.9277 4.02918 10.8565C2.5805 8.62028 1.67895 6.07422 1.39758 3.42462C1.37616 3.18758 1.40433 2.94867 1.4803 2.72311C1.55627 2.49755 1.67838 2.29028 1.83884 2.1145C1.9993 1.93871 2.19461 1.79827 2.41233 1.7021C2.63004 1.60593 2.8654 1.55615 3.10341 1.55593H5.67501C6.09101 1.55184 6.49431 1.69915 6.80973 1.97041C7.12516 2.24168 7.33118 2.61838 7.3894 3.03031C7.49794 3.85328 7.69924 4.66133 7.98944 5.43904C8.10477 5.74585 8.12973 6.0793 8.06137 6.39986C7.993 6.72042 7.83417 7.01467 7.6037 7.24773L6.51506 8.33637C7.73533 10.4824 9.51222 12.2593 11.6583 13.4796L12.7469 12.3909C12.98 12.1605 13.2742 12.0016 13.5948 11.9333C13.9153 11.8649 14.2488 11.8899 14.5556 12.0052C15.3333 12.2954 16.1413 12.4967 16.9643 12.6052C17.3807 12.664 17.761 12.8737 18.0328 13.1945C18.3047 13.5154 18.4491 13.9249 18.4387 14.3453Z"
    const val PERSON =
        "M13.9492 18.5777V15.0777C13.9492 14.282 13.6331 13.519 13.0705 12.9564C12.5079 12.3937 11.7449 12.0777 10.9492 12.0777H4.94922C4.15357 12.0777 3.39051 12.3937 2.8279 12.9564C2.26529 13.519 1.94922 14.282 1.94922 15.0777V18.5777 " +
                "M18.4492 18.5777V15.0777C18.4487 14.413 18.2275 13.7673 17.8202 13.2419C17.413 12.7166 16.8428 12.3413 16.1992 12.1752 " +
                "M13.1992 3.17508C13.8445 3.3403 14.4165 3.71561 14.8249 4.24182C15.2334 4.76803 15.4551 5.41523 15.4551 6.08136C15.4551 6.74749 15.2334 7.39468 14.8249 7.92089C14.4165 8.4471 13.8445 8.82241 13.1992 8.98763 " +
                "M10.9492 6.07761C10.9492 7.73446 9.60607 9.07764 7.94922 9.07764C6.29236 9.07764 4.94922 7.73446 4.94922 6.07761C4.94922 4.42075 6.29236 3.07758 7.94922 3.07758C9.60607 3.07758 10.9492 4.42075 10.9492 6.07761Z"
    const val CALENDAR =
        "M6.66797 1.66669V5.00002 " +
                "M13.332 1.66669V5.00002 " +
                "M15.8333 3.33331H4.16667C3.24619 3.33331 2.5 4.07951 2.5 4.99998V16.6666C2.5 17.5871 3.24619 18.3333 4.16667 18.3333H15.8333C16.7538 18.3333 17.5 17.5871 17.5 16.6666V4.99998C17.5 4.07951 16.7538 3.33331 15.8333 3.33331Z " +
                "M2.5 8.33331H17.5"
    const val SETTING =
        "M10.1824 1.6665H9.81569C9.37366 1.6665 8.94974 1.8421 8.63718 2.15466C8.32462 2.46722 8.14902 2.89114 8.14902 3.33317V3.48317C8.14872 3.77544 8.07157 4.0625 7.92531 4.31553C7.77904 4.56857 7.56881 4.7787 7.31569 4.92484L6.95736 5.13317C6.70399 5.27945 6.41659 5.35646 6.12402 5.35646C5.83146 5.35646 5.54406 5.27945 5.29069 5.13317L5.16569 5.0665C4.78325 4.84589 4.32889 4.78604 3.90236 4.90009C3.47583 5.01415 3.11198 5.29278 2.89069 5.67484L2.70736 5.9915C2.48674 6.37395 2.42689 6.82831 2.54095 7.25484C2.655 7.68137 2.93364 8.04521 3.31569 8.2665L3.44069 8.34984C3.69259 8.49526 3.90204 8.70408 4.04823 8.95553C4.19443 9.20698 4.27227 9.49231 4.27403 9.78317V10.2082C4.27519 10.5019 4.19873 10.7906 4.05239 11.0453C3.90606 11.2999 3.69503 11.5113 3.44069 11.6582L3.31569 11.7332C2.93364 11.9545 2.655 12.3183 2.54095 12.7448C2.42689 13.1714 2.48674 13.6257 2.70736 14.0082L2.89069 14.3248C3.11198 14.7069 3.47583 14.9855 3.90236 15.0996C4.32889 15.2136 4.78325 15.1538 5.16569 14.9332L5.29069 14.8665C5.54406 14.7202 5.83146 14.6432 6.12402 14.6432C6.41659 14.6432 6.70399 14.7202 6.95736 14.8665L7.31569 15.0748C7.56881 15.221 7.77904 15.4311 7.92531 15.6841C8.07157 15.9372 8.14872 16.2242 8.14902 16.5165V16.6665C8.14902 17.1085 8.32462 17.5325 8.63718 17.845C8.94974 18.1576 9.37366 18.3332 9.81569 18.3332H10.1824C10.6244 18.3332 11.0483 18.1576 11.3609 17.845C11.6734 17.5325 11.849 17.1085 11.849 16.6665V16.5165C11.8493 16.2242 11.9265 15.9372 12.0727 15.6841C12.219 15.4311 12.4292 15.221 12.6824 15.0748L13.0407 14.8665C13.2941 14.7202 13.5815 14.6432 13.874 14.6432C14.1666 14.6432 14.454 14.7202 14.7074 14.8665L14.8324 14.9332C15.2148 15.1538 15.6692 15.2136 16.0957 15.0996C16.5222 14.9855 16.8861 14.7069 17.1074 14.3248L17.2907 13.9998C17.5113 13.6174 17.5712 13.163 17.4571 12.7365C17.343 12.31 17.0644 11.9461 16.6824 11.7248L16.5574 11.6582C16.303 11.5113 16.092 11.2999 15.9457 11.0453C15.7993 10.7906 15.7229 10.5019 15.724 10.2082V9.7915C15.7229 9.49782 15.7993 9.20904 15.9457 8.95441C16.092 8.69978 16.303 8.48834 16.5574 8.3415L16.6824 8.2665C17.0644 8.04521 17.343 7.68137 17.4571 7.25484C17.5712 6.82831 17.5113 6.37395 17.2907 5.9915L17.1074 5.67484C16.8861 5.29278 16.5222 5.01415 16.0957 4.90009C15.6692 4.78604 15.2148 4.84589 14.8324 5.0665L14.7074 5.13317C14.454 5.27945 14.1666 5.35646 13.874 5.35646C13.5815 5.35646 13.2941 5.27945 13.0407 5.13317L12.6824 4.92484C12.4292 4.7787 12.219 4.56857 12.0727 4.31553C11.9265 4.0625 11.8493 3.77544 11.849 3.48317V3.33317C11.849 2.89114 11.6734 2.46722 11.3609 2.15466C11.0483 1.8421 10.6244 1.6665 10.1824 1.6665Z " +
                "M10 12.5C11.3807 12.5 12.5 11.3807 12.5 10C12.5 8.61929 11.3807 7.5 10 7.5C8.61929 7.5 7.5 8.61929 7.5 10C7.5 11.3807 8.61929 12.5 10 12.5Z"
}

@Composable
private fun BottomTabBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Figma navigation: bg deep-brown-900, selected notch 72x32, icon stroke deep-brown-950.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(NavBarBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTab.values().forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (!isSelected) onSelect(tab)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(bottom = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            NotchShape(modifier = Modifier.matchParentSize())
                            StrokeIcon(pathData = tab.pathData, color = AppColors.DeepBrown950, modifier = Modifier.size(20.dp))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                tab.label,
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Color.White, lineHeight = 16.sp),
                            )
                        }
                    }
                } else {
                    StrokeIcon(pathData = tab.pathData, color = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Rectangle_4.svg (72×32) 흰 곡선 — 윗변 꽉 차고 아래로 좁아지는 형태 */
@Composable
private fun NotchShape(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sx = size.width / 72f
        val sy = size.height / 32f
        // path: M0 0 H72 L64.5094 21.3066 C... 22.585 32 ... 7.4906 21.3066 L0 0 Z
        val p = Path().apply {
            moveTo(0f, 0f)
            lineTo(72f * sx, 0f)
            lineTo(64.5094f * sx, 21.3066f * sy)
            cubicTo(62.2571f * sx, 27.7132f * sy, 56.206f * sx, 32f * sy, 49.415f * sx, 32f * sy)
            lineTo(22.585f * sx, 32f * sy)
            cubicTo(15.794f * sx, 32f * sy, 9.74291f * sx, 27.7132f * sy, 7.49061f * sx, 21.3066f * sy)
            lineTo(0f, 0f)
            close()
        }
        drawPath(path = p, color = Color.White)
    }
}

/** stroke 기반 SVG 아이콘을 그대로 렌더 (viewBox 20×20, stroke 1.667) */
@Composable
private fun StrokeIcon(pathData: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 20f
        val parsed = PathParser().parsePathString(pathData).toPath()
        scale(s, s, pivot = Offset.Zero) {
            drawPath(
                path = parsed,
                color = color,
                style = Stroke(width = 1.667f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

private fun android.content.Context.hasActiveFianoSummaryNotification(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
    return runCatching {
        val manager = getSystemService(NotificationManager::class.java)
        manager.activeNotifications.any { notification ->
            notification.notification.channelId == CallRecorderApp.CHANNEL_SUMMARY
        }
    }.getOrDefault(false)
}
