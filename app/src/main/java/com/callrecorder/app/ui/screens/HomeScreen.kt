package com.callrecorder.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.BuildConfig
import com.callrecorder.app.R
import com.callrecorder.app.data.model.Call
import com.callrecorder.app.data.model.CalendarEvent
import com.callrecorder.app.data.model.CallStatus
import com.callrecorder.app.data.model.CustomerListItem
import com.callrecorder.app.data.model.extractedInfoOrNull
import com.callrecorder.app.data.model.internalKeywordsMap
import com.callrecorder.app.onboarding.FeatureTourController
import com.callrecorder.app.onboarding.TourKeys
import com.callrecorder.app.onboarding.tourTarget
import com.callrecorder.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private object HomeFigmaTokens {
    // Figma variables used by node 6:24667 / Recent calls section.
    val BaseWhite = AppColors.Surface                         // primitive/base-white
    val FianoBlack900 = AppColors.FianoBlack900               // primitive/fiano-black-900
    val FianoBlack700 = AppColors.FianoBlack700               // primitive/fiano-black-700
    val FianoBlack500 = AppColors.FianoBlack500               // primitive/fiano-black-500
    val FianoBlack50 = AppColors.FianoBlack50                 // primitive/fiano-black-50
    val SignalRed600 = AppColors.SignalRed600                 // primitive/signal-red-600
    val SignalRed500 = AppColors.SignalRed500                 // primitive/signal-red-500
    val SignalRed300 = AppColors.SignalRed300                 // primitive/signal-red-300
    val Space0 = 0.dp                                         // primitive/space-0
    val Space2 = 2.dp                                         // primitive/space-2
    val Space4 = 4.dp                                         // primitive/space-4
    val Space8 = 8.dp                                         // primitive/space-8
    val Space16 = 16.dp                                       // primitive/space-16
    val Space24 = 24.dp                                       // primitive/space-24
    val BottomSpacing = 64.dp                                 // semantic/bottom-spacing
    val Radius24 = 24.dp                                      // primitive/radius-24
    val ShadowRadius = 16.dp                                  // effect/shadow/bottom-nav radius
    val ShadowColor = AppColors.FianoBlack900.copy(alpha = 0.10f) // effect color #1014181A
    val ManagedCustomerListHeight = 112.dp                    // Managed clients section list frame (single-row)
    val ManagedSectionVisibleHeight = 176.dp                  // Height visible above the bottom navigation.
    val ManagedCollapsedVisibleHeight = 64.dp
    val ManagedBottomSpacing = 16.dp
    val ScheduleCollapsedVisibleHeight = 72.dp
}

/* 색상 (FIANO 디자인 시스템 변수 매핑) */
private val ScreenBg    = HomeFigmaTokens.FianoBlack900
private val ContentBg   = HomeFigmaTokens.BaseWhite
private val Navy        = HomeFigmaTokens.FianoBlack900
private val OnDark      = HomeFigmaTokens.BaseWhite
private val OnDarkSub   = HomeFigmaTokens.BaseWhite
private val TimeBlue    = HomeFigmaTokens.FianoBlack700
private val SchedTimeHi = HomeFigmaTokens.SignalRed600
private val SchedTimeSm = HomeFigmaTokens.SignalRed300
private val SchedMeta   = HomeFigmaTokens.FianoBlack700
private val Connector   = HomeFigmaTokens.SignalRed300
private val AvatarBg    = HomeFigmaTokens.FianoBlack50
private val AvatarText  = HomeFigmaTokens.FianoBlack700
private val AccentBlue  = HomeFigmaTokens.SignalRed500
private val UploadBlue  = HomeFigmaTokens.FianoBlack900
private val UploadBlueBg = AppColors.FianoBlack100
private val HomeBottomNavClearance = 96.dp
private val HomePendingActionHeight = 56.dp
private val HomePendingActionFadeHeight = 28.dp
private val HomePendingListTopFadeHeight = HomeFigmaTokens.Space24
private val HomeCardHeroOverlap = 9.dp
private val HomeCollapsedSheetDrop = 24.dp
private const val HomeManagedCardMotionStop = 0.92f
private const val HomeSheetAnimationDurationMillis = 520

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
    val clamped = fraction.coerceIn(0f, 1f)
    return (start.value + (stop.value - start.value) * clamped).dp
}

private fun smoothStep(fraction: Float): Float {
    val clamped = fraction.coerceIn(0f, 1f)
    return clamped * clamped * clamped * (clamped * (clamped * 6f - 15f) + 10f)
}

private val HomeUploadMetaLabelStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Medium,
    color = AppColors.FianoBlack500,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HomeUploadMetaValueStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 16.sp,
    color = Color.White,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCallClick: (String) -> Unit,
    onSettings: () -> Unit,
    onApprovalClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onSeeAllCalls: () -> Unit = {},
    onSeeAllSchedules: () -> Unit = {},
    onScheduleClick: (String) -> Unit = {},
    onSeeAllCustomers: () -> Unit = {},
    onUploadingClick: () -> Unit = {},
    onRefreshRecordings: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
    tourController: FeatureTourController,
    vm: HomeViewModel = viewModel(),
    approvalVm: PendingApprovalViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val approvalState by approvalVm.state.collectAsState()
    var dismissedSampleUploadIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val approvalUploads = remember(approvalState.recordings) {
        approvalState.recordings.map { recording ->
            UploadItem(
                id = recording.id,
                name = recording.fileName,
                phase = "승인 대기",
                status = recording.status,
                fileSize = recording.fileSize,
                durationSeconds = recording.durationSeconds,
                createdAtMillis = recording.callStartedAtMillis,
                isManualUpload = recording.counterpartNumber.isNullOrBlank() && recording.category == "UNCLASSIFIED",
            )
        }
    }
    val rawDisplayedUploads = remember(state.activeUploads, approvalUploads) {
        val combined = approvalUploads + state.activeUploads
        if (combined.isEmpty() && shouldShowEmulatorPendingSamples()) {
            samplePendingUploads()
        } else {
            combined
        }
    }
    val displayedUploads = remember(rawDisplayedUploads, dismissedSampleUploadIds) {
        rawDisplayedUploads.filterNot { it.id in dismissedSampleUploadIds }
    }
    val displayedPinnedCustomers = remember(state.pinnedCustomers) {
        if (shouldShowEmulatorPendingSamples() && state.pinnedCustomers.size < 12) {
            (state.pinnedCustomers + samplePinnedCustomers())
                .distinctBy { it.phone }
                .take(20)
        } else {
            state.pinnedCustomers.take(20)
        }
    }

    LaunchedEffect(Unit) {
        vm.syncSettingsFromPrefs()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        FianoTopHeader(
            onNotificationClick = onNotificationClick,
            hasNotification = hasNotification,
        )

        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val density = LocalDensity.current
            var heroHeight by remember { mutableStateOf(0.dp) }
            var sheetExpanded by remember { mutableStateOf(true) }
            var sheetInteractionStarted by remember { mutableStateOf(false) }
            val collapsedSheetHeight =
                HomeFigmaTokens.ScheduleCollapsedVisibleHeight +
                        HomeFigmaTokens.ManagedCollapsedVisibleHeight +
                        HomeBottomNavClearance
            val heroMeasured = heroHeight > 0.dp
            val expandedSheetHeight = if (heroMeasured) {
                (maxHeight - heroHeight + HomeCardHeroOverlap).coerceAtLeast(collapsedSheetHeight)
            } else {
                collapsedSheetHeight
            }
            val targetCollapsedSheetDrop = if (sheetExpanded) HomeFigmaTokens.Space0 else HomeCollapsedSheetDrop
            val collapsedSheetDrop by animateDpAsState(
                targetValue = targetCollapsedSheetDrop,
                animationSpec = if (sheetInteractionStarted) {
                    tween(durationMillis = HomeSheetAnimationDurationMillis, easing = FastOutSlowInEasing)
                } else {
                    snap()
                },
                label = "home-collapsed-sheet-drop",
            )
            val collapsedListBoundary = (collapsedSheetHeight - collapsedSheetDrop).coerceAtLeast(HomeFigmaTokens.Space0)
            val targetSheetHeight = if (sheetExpanded) expandedSheetHeight else collapsedSheetHeight
            val sheetHeight by animateDpAsState(
                targetValue = targetSheetHeight,
                animationSpec = if (sheetInteractionStarted) {
                    tween(durationMillis = HomeSheetAnimationDurationMillis, easing = FastOutSlowInEasing)
                } else {
                    snap()
                },
                label = "home-bottom-card-height",
            )

            Column(Modifier.fillMaxSize()) {
                Hero(
                    pendingCount = state.pendingApprovalCount,
                    autoSummaryOn = state.autoSummaryEnabled,
                    importantFilterOn = state.importantFilterEnabled,
                    uploadingCount = state.uploadingCount,
                    autoDetectedCount = displayedUploads.count { it.status.equals("PENDING", ignoreCase = true) },
                    autoAnalyzingCount = displayedUploads.count {
                        it.status.equals("UPLOADING", ignoreCase = true) ||
                                it.status.equals("UPLOADED", ignoreCase = true) ||
                                it.status.equals("PROCESSING", ignoreCase = true)
                    },
                    onAutoSummaryChange = { vm.setAutoSummary(it) },
                    onImportantFilterChange = { vm.setImportantFilter(it) },
                    onApprovalClick = {
                        sheetInteractionStarted = true
                        sheetExpanded = false
                        onApprovalClick()
                    },
                    onUploadClick = onUploadClick,
                    onRefresh = onRefreshRecordings,
                    onUploadingClick = onUploadingClick,
                    tourController = tourController,
                    modifier = Modifier.onGloballyPositioned {
                        heroHeight = with(density) { it.size.height.toDp() }
                    },
                )

                if (heroMeasured) {
                    HomePendingUploadList(
                        uploads = displayedUploads,
                        topPadding = HomePendingListTopFadeHeight + 8.dp,
                        bottomPadding = collapsedListBoundary + HomePendingActionHeight + 16.dp,
                        onDelete = { id ->
                            displayedUploads.firstOrNull { it.id == id }?.let { upload ->
                                when {
                                    id <= 0L -> dismissedSampleUploadIds = dismissedSampleUploadIds + id
                                    upload.status == "AWAITING_APPROVAL" -> approvalVm.rejectOne(id)
                                    else -> vm.deleteUpload(id)
                                }
                            }
                        },
                        onRetry = { if (it > 0L) vm.retryUpload(it) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            if (heroMeasured && displayedUploads.isNotEmpty()) {
                HomePendingListActions(
                    onDeleteAll = {
                        if (displayedUploads.any { it.id > 0L }) {
                            approvalVm.rejectAll()
                            vm.deleteAllUploads()
                        }
                        val sampleIds = displayedUploads.filter { it.id <= 0L }.map { it.id }
                        if (sampleIds.isNotEmpty()) {
                            dismissedSampleUploadIds = dismissedSampleUploadIds + sampleIds
                        }
                    },
                    onApproveAll = {
                        if (displayedUploads.any { it.id > 0L }) {
                            vm.approveAll()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = collapsedListBoundary),
                )
            }

            if (heroMeasured) {
                HomeBottomCardSheet(
                    expanded = sheetExpanded,
                    height = sheetHeight,
                    collapsedHeight = collapsedSheetHeight,
                    expandedHeight = expandedSheetHeight,
                    schedules = state.schedules,
                    pinnedCustomers = displayedPinnedCustomers,
                    onExpandedChange = {
                        sheetInteractionStarted = true
                        sheetExpanded = it
                    },
                    onSeeAllSchedules = onSeeAllSchedules,
                    onScheduleClick = onScheduleClick,
                    onSeeAllCustomers = onSeeAllCustomers,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = collapsedSheetDrop),
                )
            }
        }
    }
}

@Composable
private fun HomePendingUploadList(
    uploads: List<UploadItem>,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onDelete: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uploads.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = topPadding)
                .padding(bottom = bottomPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                "분석 대기 중인 통화가 없어요",
                modifier = Modifier.padding(top = 24.dp),
                style = TextStyle(fontSize = 13.sp, color = AppColors.FianoBlack400),
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = topPadding, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            items(uploads, key = { it.id }) { upload ->
                SwipeRevealDeleteBox(
                    onDelete = { onDelete(upload.id) },
                    modifier = Modifier.fillMaxWidth(),
                    deleteOnReveal = true,
                ) {
                    HomePendingUploadRow(upload = upload, onRetry = { onRetry(upload.id) })
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(HomePendingListTopFadeHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ScreenBg, Color.Transparent),
                    ),
                ),
        )
    }
}

@Composable
private fun HomePendingListActions(
    onDeleteAll: () -> Unit,
    onApproveAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HomePendingActionHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomePendingActionFadeHeight)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, ScreenBg),
                    ),
                )
                .align(Alignment.TopCenter)
                .offset(y = -HomePendingActionFadeHeight),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HomePendingActionHeight)
                .background(ScreenBg)
                .padding(horizontal = 48.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "-리스트삭제",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onDeleteAll() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
            Text(
                "+리스트승인",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { onApproveAll() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
    }
}

@Composable
private fun HomePendingUploadRow(upload: UploadItem, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScreenBg)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(
                id = homeUploadIconRes(upload),
            ),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    upload.name,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                if (upload.canRetry) {
                    HomeRetryChip(onClick = onRetry)
                } else {
                    HomeStatusChip(upload.phase)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                HomeUploadMeta("생성", homeUploadDateLabel(upload.createdAtMillis))
                HomeUploadMeta("음성", homeDuration(upload.durationSeconds))
                HomeUploadMeta("파일", homeFileSize(upload.fileSize))
            }
        }
    }
}

@Composable
private fun HomeStatusChip(label: String) {
    val isApprovalWaiting = label == "승인 대기"
    val color = when {
        isApprovalWaiting -> AppColors.FianoBlack900
        label == "실패" -> AppColors.SignalRed500
        else -> AppColors.FianoBlack50
    }
    Surface(
        color = if (isApprovalWaiting) Color.White else Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = if (isApprovalWaiting) null else androidx.compose.foundation.BorderStroke(1.dp, color),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, color = color),
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeRetryChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.SignalRed500),
    ) {
        Text(
            "재시도",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.SignalRed500),
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeUploadMeta(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            label,
            style = HomeUploadMetaLabelStyle,
            maxLines = 1,
        )
        Text(
            value,
            style = HomeUploadMetaValueStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeBottomCardSheet(
    expanded: Boolean,
    height: androidx.compose.ui.unit.Dp,
    collapsedHeight: Dp,
    expandedHeight: Dp,
    schedules: List<CalendarEvent>,
    pinnedCustomers: List<CustomerListItem>,
    onExpandedChange: (Boolean) -> Unit,
    onSeeAllSchedules: () -> Unit,
    onScheduleClick: (String) -> Unit,
    onSeeAllCustomers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragTotal by remember { mutableStateOf(0f) }
    val sheetRange = (expandedHeight - collapsedHeight).value.takeIf { it > 0f } ?: 1f
    val sheetProgress = ((height - collapsedHeight).value / sheetRange).coerceIn(0f, 1f)
    val sectionProgress = smoothStep(sheetProgress)
    val managedMotionProgress = smoothStep(sheetProgress / HomeManagedCardMotionStop)
    val collapsedManagedCardHeight =
        HomeFigmaTokens.ManagedCollapsedVisibleHeight + HomeBottomNavClearance
    val expandedManagedCardHeight =
        HomeFigmaTokens.ManagedSectionVisibleHeight + HomeBottomNavClearance
    val managedCardHeight = lerpDp(
        start = collapsedManagedCardHeight,
        stop = expandedManagedCardHeight,
        fraction = managedMotionProgress,
    )
    val secondCardTop =
        (height - managedCardHeight).coerceAtLeast(HomeFigmaTokens.ScheduleCollapsedVisibleHeight)
    val scheduleCardHeight = secondCardTop
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(expanded) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                    },
                    onDragEnd = {
                        if (abs(dragTotal) > HomeFigmaTokens.Space24.toPx()) {
                            onExpandedChange(dragTotal < 0f)
                        }
                        dragTotal = 0f
                    },
                    onDragCancel = { dragTotal = 0f },
                )
            }
            .clip(RoundedCornerShape(topStart = HomeFigmaTokens.Radius24, topEnd = HomeFigmaTokens.Radius24)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ContentBg),
        )
        HomeSheetSection(
            title = "다가오는 일정",
            expanded = expanded,
            expandedProgress = sectionProgress,
            onSeeAll = onSeeAllSchedules,
            background = ContentBg,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(scheduleCardHeight),
        ) {
            if (schedules.isEmpty()) {
                EmptyBox("예정된 일정이 없어요")
            } else {
                Column(Modifier.padding(start = HomeFigmaTokens.Space8, end = HomeFigmaTokens.Space8, top = HomeFigmaTokens.Space8)) {
                    schedules.forEachIndexed { idx, ev ->
                        ScheduleTimelineItem(
                            event = ev,
                            isFirst = idx == 0,
                            isLast = idx == schedules.lastIndex,
                            onClick = { scheduleDateValue(ev)?.let(onScheduleClick) },
                        )
                    }
                }
            }
        }

        HomeBottomNavShadow(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (secondCardTop - HomeFigmaTokens.ShadowRadius).coerceAtLeast(HomeFigmaTokens.Space0)),
        )

        HomeSheetSection(
            title = "주요 관리 고객",
            expanded = expanded,
            expandedProgress = sectionProgress,
            onSeeAll = onSeeAllCustomers,
            background = ContentBg,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = secondCardTop)
                .fillMaxWidth()
                .height(managedCardHeight),
            topCornerRadius = HomeFigmaTokens.Radius24,
            bottomPadding = lerpDp(
                start = HomeBottomNavClearance,
                stop = HomeFigmaTokens.ManagedBottomSpacing + HomeBottomNavClearance,
                fraction = sectionProgress,
            ),
            contentScrollable = false,
        ) {
            if (pinnedCustomers.isEmpty()) {
                EmptyBox("관리 중인 고객이 없어요")
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HomeFigmaTokens.ManagedCustomerListHeight)
                        .padding(start = HomeFigmaTokens.Space8, end = HomeFigmaTokens.Space8),
                    horizontalArrangement = Arrangement.spacedBy(HomeFigmaTokens.Space16),
                ) {
                    items(pinnedCustomers.take(20), key = { it.phone }) { customer ->
                        PinnedCustomerGridItem(
                            customer = customer,
                            modifier = Modifier.width(88.dp),
                            onClick = onSeeAllCustomers,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBottomNavShadow(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeFigmaTokens.ShadowRadius + HomeFigmaTokens.Radius24)
            .clip(RoundedCornerShape(topStart = HomeFigmaTokens.Radius24, topEnd = HomeFigmaTokens.Radius24))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        HomeFigmaTokens.ShadowColor,
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
private fun HomeSheetSection(
    title: String,
    expanded: Boolean,
    expandedProgress: Float,
    onSeeAll: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    topCornerRadius: Dp = HomeFigmaTokens.Radius24,
    bottomPadding: Dp = HomeFigmaTokens.Space0,
    contentScrollable: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius)
    val progress = expandedProgress.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .padding(
                start = HomeFigmaTokens.Space16,
                end = HomeFigmaTokens.Space16,
                top = HomeFigmaTokens.Space8,
                bottom = bottomPadding,
            ),
    ) {
        SectionHeader(
            title = title,
            onSeeAll = onSeeAll,
            expanded = expanded,
            expandedProgress = progress,
        )
        if (expanded || progress > 0.01f) {
            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { alpha = progress }
            if (contentScrollable) {
                Column(
                    modifier = contentModifier.verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            } else {
                Column(modifier = contentModifier) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun Hero(
    pendingCount: Int,
    autoSummaryOn: Boolean,
    importantFilterOn: Boolean,
    uploadingCount: Int,
    autoDetectedCount: Int,
    autoAnalyzingCount: Int,
    onAutoSummaryChange: (Boolean) -> Unit,
    onImportantFilterChange: (Boolean) -> Unit,
    onApprovalClick: () -> Unit,
    onUploadClick: () -> Unit,
    onRefresh: () -> Unit,
    onUploadingClick: () -> Unit,
    tourController: FeatureTourController,
    modifier: Modifier = Modifier,
) {
    val today = remember { todayFullDateLabel() }
    val autoStatusMessages = remember(autoSummaryOn, autoDetectedCount, autoAnalyzingCount) {
        if (!autoSummaryOn) {
            emptyList()
        } else {
            buildList {
                if (autoDetectedCount > 0) add("분석 감지 ${autoDetectedCount}건")
                if (autoAnalyzingCount > 0) add("자동 분석중 ${autoAnalyzingCount}건")
            }.ifEmpty { listOf("자동 분석 준비됨") }
        }
    }
    var autoStatusIndex by remember { mutableStateOf(0) }
    LaunchedEffect(autoStatusMessages) {
        autoStatusIndex = 0
        while (autoStatusMessages.size > 1) {
            delay(2_000L)
            autoStatusIndex = (autoStatusIndex + 1) % autoStatusMessages.size
        }
    }
    val primaryStatusText = if (autoSummaryOn) {
        autoStatusMessages.getOrElse(autoStatusIndex) { "자동 분석 준비됨" }
    } else {
        "분석 승인 요청 ${pendingCount}건"
    }
    val refreshTransition = rememberInfiniteTransition(label = "auto-analysis-refresh")
    val refreshRotation by refreshTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "auto-analysis-refresh-rotation",
    )
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)) {
            Text(today, style = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = OnDark))
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onRefresh,
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(64.dp)
                        .tourTarget(tourController, TourKeys.REFRESH_RECORDINGS),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.home_icon_refresh),
                            contentDescription = "녹음 파일 새로 감지",
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    rotationZ = if (autoSummaryOn) refreshRotation else 0f
                                },
                        )
                    }
                }
                Surface(
                    onClick = onApprovalClick,
                    color = Color.White,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .tourTarget(tourController, TourKeys.ANALYSIS_STATUS),
                ) {
                    Row(
                        Modifier.padding(start = 24.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.home_icon_auto_status),
                            contentDescription = if (autoSummaryOn) "자동 분석 상태" else "분석 승인 요청",
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            primaryStatusText,
                            style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Navy),
                            maxLines = 1,
                        )
                    }
                }
                Surface(
                    onClick = onUploadClick,
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp).tourTarget(tourController, TourKeys.UPLOAD),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.home_icon_add_calendar),
                            contentDescription = "파일 업로드",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            if (uploadingCount > 0) {
                Spacer(Modifier.height(10.dp))
                Surface(color = UploadBlueBg, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = UploadBlue)
                        Spacer(Modifier.width(10.dp))
                        Text("녹음 ${uploadingCount}건 업로드·분석 중",
                            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = UploadBlue),
                            modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .tourTarget(tourController, TourKeys.AUTO_SETTINGS),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ToggleButton(autoSummaryOn, "통화 자동 요약",
                    { onAutoSummaryChange(!autoSummaryOn) },
                    Modifier.weight(1f))
                ToggleButton(importantFilterOn, "통화 자동 필터링",
                    { onImportantFilterChange(!importantFilterOn) },
                    Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToggleButton(on: Boolean, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { onToggle() }
            .padding(horizontal = 0.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = if (on) R.drawable.home_icon_action_filter_on else R.drawable.home_icon_action_summary_off),
            contentDescription = if (on) "켜짐" else "꺼짐",
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$label ${if (on) "ON" else "OFF"}",
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = OnDark),
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onSeeAll: () -> Unit,
    emphasis: Boolean = false,
    expanded: Boolean = true,
    expandedProgress: Float = if (expanded) 1f else 0f,
) {
    val progress = expandedProgress.coerceIn(0f, 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = HomeFigmaTokens.Space8,
                end = HomeFigmaTokens.Space8,
                top = HomeFigmaTokens.Space4,
                bottom = lerpDp(HomeFigmaTokens.Space8, HomeFigmaTokens.Space16, progress),
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.home_icon_section_open),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = 180f * (1f - progress) },
            )
            Spacer(Modifier.width(HomeFigmaTokens.Space8))
            Text(title, style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = if (emphasis) FontWeight.ExtraBold else FontWeight.Bold, color = Navy))
        }
        Surface(onClick = onSeeAll, color = Color.Transparent) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("전체보기", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy))
                Image(
                    painter = painterResource(id = R.drawable.icon_go),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallRow(call: Call, onClick: () -> Unit) {
    val name = customerName(call)
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.home_icon_call),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy), maxLines = 1)
                // 이름이 진짜 이름일 때만 번호 한 줄 더
                if (hasRealName(call)) {
                    phoneOf(call)?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = TextStyle(fontSize = 11.sp, color = SchedMeta), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(callSubtitle(call), style = TextStyle(fontSize = 13.sp, color = Navy), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(callTimeLabel(call.createdAt), style = TextStyle(fontSize = 14.sp, color = TimeBlue))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerCard(call: Call, modifier: Modifier, onClick: () -> Unit) {
    val name = customerName(call)
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(name, 34.dp)
            Column {
                Text(name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Navy), maxLines = 1)
                if (hasRealName(call)) {
                    phoneOf(call)?.let {
                        Text(it, style = TextStyle(fontSize = 10.sp, color = SchedMeta), maxLines = 1)
                    }
                }
                Text(callSubtitle(call), style = TextStyle(fontSize = 11.sp, color = Navy), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val displayName = name.ifBlank { "발신번호 없음" }
    val isPhone = displayName.firstOrNull()?.isDigit() == true || displayName == "발신번호 없음"
    Box(Modifier.size(size).clip(CircleShape).background(AvatarBg), contentAlignment = Alignment.Center) {
        if (isPhone) {
            Icon(Icons.Filled.Phone, null, tint = AvatarText, modifier = Modifier.size(size * 0.45f))
        } else {
            Text(
                displayName.first().toString(),
                style = TextStyle(
                    fontSize = (size.value * 0.4f).sp,
                    fontWeight = FontWeight.Bold,
                    color = AvatarText,
                ),
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinnedCustomerCard(customer: CustomerListItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val name = customer.name?.takeIf { it.isNotBlank() } ?: customer.phone
    Surface(onClick = onClick, color = Color.White, shape = RoundedCornerShape(8.dp), modifier = modifier.height(112.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(AvatarBg), contentAlignment = Alignment.Center) {
                Text(name.take(1), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AvatarText))
            }
            Text(name, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy), maxLines = 1)
            Text(customer.phone, style = TextStyle(fontSize = 10.sp, color = SchedMeta), maxLines = 1)
            Text("★ 주요관리", style = TextStyle(fontSize = 10.sp, color = AccentBlue), maxLines = 1)
        }
    }
}

@Composable
private fun PinnedCustomerGridItem(
    customer: CustomerListItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val name = customer.name?.takeIf { it.isNotBlank() } ?: customer.phone
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AppColors.FianoBlack50),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1),
                style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Navy),
            )
        }
        Text(
            name,
            style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Navy),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            customer.latestSummary?.takeIf { it.isNotBlank() } ?: "납품 일정 확인",
            style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, color = Navy),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}


@Composable
private fun ScheduleTimelineItem(
    event: CalendarEvent,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit = {},
) {
    val itemHeight = if (isFirst) 86.dp else 66.dp
    val timeFontSize = if (isFirst) 14.sp else 12.sp
    val titleFontSize = if (isFirst) 14.sp else 12.sp
    val detailFontSize = if (isFirst) 12.sp else 10.sp
    Row(
        Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
    ) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isFirst) {
                Image(
                    painter = painterResource(id = R.drawable.call_icon_timeline_marker),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .border(1.dp, Connector, CircleShape)
                            .clip(CircleShape),
                    )
                }
            }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(Connector))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(
                    start = 0.dp,
                    top = if (isFirst) 2.dp else 0.dp,
                    bottom = if (isFirst) 24.dp else 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                listOfNotNull(scheduleDateLabel(event), event.time.takeIf { it.isNotBlank() }).joinToString("  "),
                style = TextStyle(
                    fontSize = timeFontSize,
                    lineHeight = timeFontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isFirst) SchedTimeHi else SchedTimeSm,
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    event.title,
                    style = TextStyle(
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold,
                        color = Navy,
                        lineHeight = if (isFirst) 18.sp else 16.sp,
                    ),
                )
                Text(
                    event.description.takeIf { it.isNotBlank() } ?: "통화 자동 등록",
                    style = TextStyle(
                        fontSize = detailFontSize,
                        lineHeight = if (isFirst) 16.sp else 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = SchedMeta,
                    ),
                )
            }
        }
    }
}

private fun scheduleDateLabel(event: CalendarEvent): String? {
    val datePart = scheduleDateValue(event) ?: return null
    val parts = datePart.split("-")
    if (parts.size < 3) return datePart.takeIf { it.isNotBlank() }
    val month = parts[1].toIntOrNull() ?: return datePart
    val day = parts[2].toIntOrNull() ?: return datePart
    return "${parts[0]}년 ${month}월 ${day}일"
}

private fun scheduleDateValue(event: CalendarEvent): String? {
    val raw = event.startAt?.takeIf { it.isNotBlank() } ?: return null
    return raw.substringBefore("T").substringBefore(" ").takeIf { it.isNotBlank() }
}

private fun homeUploadDateLabel(millis: Long): String {
    if (millis <= 0L) return "-"
    return SimpleDateFormat("yyyyMMdd", Locale.KOREAN).format(Date(millis))
}

private fun homeUploadIconRes(upload: UploadItem): Int {
    if (upload.isAnalyzingStatus()) {
        return if (upload.isManualUpload) R.drawable.icon_stt else R.drawable.icon_autocall
    }

    val source = "${upload.name} ${upload.status} ${upload.phase}".lowercase(Locale.KOREAN)
    return when {
        source.contains("수신") ||
                source.contains("incoming") ||
                source.contains("inbound") ||
                source.contains("received") -> R.drawable.icon_reception_white
        source.contains("발신") ||
                source.contains("outgoing") ||
                source.contains("outbound") ||
                source.contains("sent") -> R.drawable.icon_outgoing_white
        source.contains("녹음") ||
                source.contains("record") ||
                source.contains("upload") ||
                upload.isManualUpload -> R.drawable.icon_call_up_white
        else -> R.drawable.icon_call_up_white
    }
}

private fun UploadItem.isAnalyzingStatus(): Boolean {
    val normalizedStatus = status.lowercase(Locale.US)
    val normalizedPhase = phase.lowercase(Locale.KOREAN)
    return normalizedStatus in setOf("uploading", "uploaded", "processing") ||
            normalizedPhase.contains("분석")
}

private fun homeDuration(seconds: Int): String {
    if (seconds <= 0) return "-"
    val minutes = seconds / 60
    val sec = seconds % 60
    return "%02d:%02d".format(minutes, sec)
}

private fun homeFileSize(bytes: Long): String {
    if (bytes <= 0L) return "-"
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
        String.format(Locale.KOREAN, "%.1fKB", kb)
    } else {
        String.format(Locale.KOREAN, "%.1fMB", kb / 1024.0)
    }
}

private fun shouldShowEmulatorPendingSamples(): Boolean {
    if (!BuildConfig.DEBUG) return false
    val fingerprint = Build.FINGERPRINT.lowercase(Locale.US)
    val model = Build.MODEL.lowercase(Locale.US)
    val product = Build.PRODUCT.lowercase(Locale.US)
    val brand = Build.BRAND.lowercase(Locale.US)
    val device = Build.DEVICE.lowercase(Locale.US)
    return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            product.contains("sdk") ||
            brand == "google" && device.contains("emu")
}

private fun samplePendingUploads(): List<UploadItem> {
    val now = System.currentTimeMillis()
    return List(10) { index ->
        val isAwaitingApproval = index < 5
        val isManualUpload = !isAwaitingApproval && index < 8
        val phase = if (isAwaitingApproval) "승인 대기" else "서버 분석 중"
        UploadItem(
            id = -(index + 1L),
            name = when {
                isAwaitingApproval -> "승인대기_통화녹음_박서윤_070${index + 1}_09${(index + 10).toString().padStart(2, '0')}.m4a"
                isManualUpload -> "분석중_수동업로드_상담녹음_070${index + 1}_10${(index + 10).toString().padStart(2, '0')}.m4a"
                else -> "분석중_자동감지_김민준_070${index + 1}_10${(index + 10).toString().padStart(2, '0')}.m4a"
            },
            phase = phase,
            status = if (isAwaitingApproval) "AWAITING_APPROVAL" else "PROCESSING",
            errorMessage = null,
            canRetry = false,
            fileSize = 1_900_000L + index * 180_000L,
            durationSeconds = 96 + index * 13,
            createdAtMillis = now - index * 3_600_000L,
            isManualUpload = isManualUpload,
        )
    }
}

private fun samplePinnedCustomers(): List<CustomerListItem> {
    val names = listOf(
        "박서연" to "납품 일정 확인",
        "김민준" to "재방문 일정 조율",
        "이태양" to "견적서 발송 필요",
        "최하은" to "예약 변경 문의",
        "정도윤" to "계약 조건 확인",
        "한지우" to "추가 상담 요청",
        "오서준" to "방문 일정 확정",
        "윤서아" to "서류 준비 안내",
        "장민서" to "매물 확인 예정",
        "임지호" to "보증금 조건 논의",
        "송하린" to "주차 가능 여부",
        "강도현" to "입주일 재확인",
        "문채원" to "미팅 시간 조정",
        "조유찬" to "계약서 초안 검토",
        "서아린" to "상담 후속 연락",
        "배준서" to "견적 비교 요청",
        "신예린" to "방문 전 안내",
        "홍시우" to "잔금 일정 확인",
    )
    return names.mapIndexed { index, item ->
        CustomerListItem(
            phone = "010-90${index.toString().padStart(2, '0')}-${(1200 + index).toString().padStart(4, '0')}",
            name = item.first,
            callCount = 20 - index,
            latestSummary = item.second,
            isPinned = true,
        )
    }
}

@Composable
private fun EmptyBox(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(fontSize = 13.sp, color = SchedMeta))
    }
}

/* 데이터 헬퍼 */
private fun customerName(call: Call): String =
    call.extractedInfoOrNull()?.customerName?.takeIf { it.isNotBlank() }
        ?: call.callerName?.takeIf { it.isNotBlank() }
        ?: call.callerNumber?.takeIf { it.isNotBlank() }
        ?: "발신번호 없음"

/** 표시 이름이 전화번호가 아니라 진짜 이름인지 */
private fun hasRealName(call: Call): Boolean {
    val n = customerName(call)
    return n != call.callerNumber && n != "발신번호 없음"
}

private fun phoneOf(call: Call): String? = call.callerNumber?.takeIf { it.isNotBlank() }

private fun callSubtitle(call: Call): String {
    call.summary?.takeIf { it.isNotBlank() }?.let { return it }
    val info = call.extractedInfoOrNull()
    val brief = info?.let { i -> listOfNotNull(i.time?.takeIf { it.isNotBlank() }, i.partySize?.let { "${it}명" }).joinToString(" · ") }.orEmpty()
    if (brief.isNotBlank()) return brief
    val kw = call.internalKeywordsMap().entries.take(2).joinToString(" · ") { it.value }
    if (kw.isNotBlank()) return kw
    return when {
        call.status.equals(CallStatus.PROCESSING, true) -> "분석 중..."
        call.status.equals(CallStatus.UPLOADED, true) -> "서버 분석 대기"
        else -> "-"
    }
}

private val Weekdays = arrayOf("일", "월", "화", "수", "목", "금", "토")
private fun todayFullDateLabel(): String {
    val cal = java.util.Calendar.getInstance()
    val dow = Weekdays[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    return SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN).format(Date()) + " ${dow}요일"
}

private fun callTimeLabel(serverTime: String?): String {
    if (serverTime.isNullOrBlank()) return ""
    val date = parseServerTime(serverTime) ?: return ""
    val diffMs = System.currentTimeMillis() - date.time
    return if (diffMs < 86_400_000L) SimpleDateFormat("HH:mm", Locale.KOREAN).format(date)
    else SimpleDateFormat("M/d", Locale.KOREAN).format(date)
}

private fun parseServerTime(s: String): Date? {
    for (fmt in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")) {
        try { return SimpleDateFormat(fmt, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s) ?: continue } catch (_: Exception) {}
    }
    return null
}

/* 업로드 시트 (기존 유지) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadSheet(items: List<com.callrecorder.app.ui.screens.UploadItem>, onDismiss: () -> Unit, onDelete: (Long) -> Unit, onDeleteAll: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("업로드 진행 중", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1C23)))
                    Spacer(Modifier.height(4.dp))
                    Text("${items.size}건 처리 중", style = TextStyle(fontSize = 13.sp, color = Color(0xFF8A8B94)))
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onDeleteAll) {
                        Text("모두 삭제", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEF4444)))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (items.isEmpty()) {
                Text("진행 중인 업로드가 없습니다.", style = TextStyle(fontSize = 14.sp, color = Color(0xFF8A8B94)))
            } else {
                items.forEach { u -> UploadSheetItem(u, onDelete); Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun UploadSheetItem(u: com.callrecorder.app.ui.screens.UploadItem, onDelete: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (u.phase == "업로드중") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = UploadBlue)
        else Icon(Icons.Filled.UploadFile, null, tint = Color(0xFFB0B5C0), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(u.name, style = TextStyle(fontSize = 14.sp, color = Color(0xFF1B1C23)), modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(u.phase, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (u.phase != "대기중") UploadBlue else Color(0xFF8A8B94)))
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { onDelete(u.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, "삭제", tint = Color(0xFFC44545), modifier = Modifier.size(18.dp))
        }
    }
}
