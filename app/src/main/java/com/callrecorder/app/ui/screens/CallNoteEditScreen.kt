package com.callrecorder.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.callrecorder.app.data.model.CallPhoto
import com.callrecorder.app.ui.theme.AppColors
import com.callrecorder.app.ui.theme.CallRecorderTheme
import com.callrecorder.app.util.PhotoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/* ── 색상: 앱 공통 톤 ── */
private val DarkNavy       = AppColors.DeepBrown900
private val LightBg        = AppColors.Background
private val WhiteCard      = Color(0xFFFFFFFF)
private val AccentBlue     = AppColors.DeepBrown900
private val OnDarkPrimary  = Color(0xFFFFFFFF)
private val OnLightPrimary = AppColors.DeepBrown900
private val OnLightSub     = AppColors.DeepBrown500
private val OnLightMuted   = AppColors.DeepBrown400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallNoteEditScreen(
    callId: String,
    callTitle: String = "통화 메모",
    onBack: () -> Unit,
    vm: CallNoteViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 진입 시 1회 로드
    LaunchedEffect(callId) { vm.load(callId) }

    // 메시지/에러 → 스낵바
    LaunchedEffect(state.message, state.error) {
        val msg = state.error ?: state.message
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.clearMessage()
        }
    }

    // 카메라 촬영용 임시 파일 URI 보관
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // 공통: Uri → 압축 바이트 → 업로드
    fun handlePickedUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                PhotoUtils.uriToCompressedBytes(context, uri)
            }
            if (bytes == null) {
                snackbar.showSnackbar("이미지를 불러오지 못했어요")
                return@launch
            }
            val fileName = "photo_${System.currentTimeMillis()}.jpg"
            vm.uploadPhoto(fileName, bytes)
        }
    }

    // 갤러리 런처
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> handlePickedUri(uri) }

    // 카메라 런처
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) handlePickedUri(cameraImageUri)
    }

    // 카메라 실행 함수 (임시 파일 + FileProvider URI 생성)
    fun launchCamera() {
        val file: File = PhotoUtils.createTempImageFile(context)
        val uri = FileProvider.getUriForFile(context, PhotoUtils.authority(context), file)
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    // 카메라 권한 런처
    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
        else scope.launch { snackbar.showSnackbar("카메라 권한이 필요해요") }
    }

    fun onCameraClick() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera()
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    CallNoteEditContent(
        state = state,
        snackbarHost = { SnackbarHost(snackbar) },
        onMemoChange = vm::onMemoChange,
        onCancel = onBack,
        onSaveMemo = vm::saveMemo,
        onCameraClick = { onCameraClick() },
        onGalleryClick = { galleryLauncher.launch("image/*") },
        onDeletePhoto = vm::deletePhoto,
    )
}

@Composable
private fun CallNoteEditContent(
    state: CallNoteUiState,
    snackbarHost: @Composable () -> Unit = {},
    onMemoChange: (String) -> Unit,
    onCancel: () -> Unit = {},
    onSaveMemo: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDeletePhoto: (String) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = snackbarHost,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = 620.dp),
            ) {
                if (state.loading) {
                    Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 520.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text("메모", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                                    Spacer(Modifier.height(10.dp))
                                    Surface(color = LightBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        BasicTextField(
                                            value = state.memo,
                                            onValueChange = onMemoChange,
                                            textStyle = TextStyle(fontSize = 14.sp, color = OnLightPrimary, lineHeight = 21.sp),
                                            cursorBrush = SolidColor(AccentBlue),
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(12.dp),
                                            decorationBox = { inner ->
                                                if (state.memo.isEmpty()) {
                                                    Text(
                                                        "이 통화에 대한 메모를 남겨보세요.\n예: 예약 변경, 특이사항, 후속 조치 등",
                                                        style = TextStyle(fontSize = 14.sp, color = OnLightMuted, lineHeight = 21.sp),
                                                    )
                                                }
                                                inner()
                                            },
                                        )
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                Column(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("사진 (${state.photos.size})", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                                        if (state.uploading) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentBlue)
                                                Spacer(Modifier.width(6.dp))
                                                Text("업로드 중...", style = TextStyle(fontSize = 12.sp, color = OnLightSub))
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        AddPhotoButton(
                                            icon = { Icon(Icons.Filled.CameraAlt, null, tint = AccentBlue, modifier = Modifier.size(18.dp)) },
                                            label = "카메라",
                                            modifier = Modifier.weight(1f),
                                            enabled = !state.uploading,
                                            onClick = onCameraClick,
                                        )
                                        AddPhotoButton(
                                            icon = { Icon(Icons.Filled.PhotoLibrary, null, tint = AccentBlue, modifier = Modifier.size(18.dp)) },
                                            label = "갤러리",
                                            modifier = Modifier.weight(1f),
                                            enabled = !state.uploading,
                                            onClick = onGalleryClick,
                                        )
                                    }

                                    if (state.photos.isNotEmpty()) {
                                        Spacer(Modifier.height(14.dp))
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(3),
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            userScrollEnabled = false,
                                        ) {
                                            items(state.photos, key = { it.id }) { photo ->
                                                Box(
                                                    Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(LightBg),
                                                ) {
                                                    AsyncImage(
                                                        model = photo.url,
                                                        contentDescription = "통화 사진",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                    Box(
                                                        Modifier.align(Alignment.TopEnd).padding(4.dp)
                                                            .size(22.dp).clip(RoundedCornerShape(11.dp))
                                                            .background(Color(0xCC000000)),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        IconButton(
                                                            onClick = { onDeletePhoto(photo.id) },
                                                            modifier = Modifier.size(22.dp),
                                                        ) {
                                                            Icon(Icons.Filled.Close, "삭제", tint = Color.White, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.height(12.dp))
                                        Text("아직 첨부된 사진이 없어요.", style = TextStyle(fontSize = 12.sp, color = OnLightMuted))
                                    }
                                }
                            }
                        }
                        BottomSheetActionBar(
                            cancelLabel = "취소",
                            saveLabel = "저장",
                            saving = state.saving,
                            onCancel = onCancel,
                            onSave = onSaveMemo,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomSheetActionBar(
    cancelLabel: String,
    saveLabel: String,
    saving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BottomSheetActionButton(
                label = cancelLabel,
                type = BottomSheetActionType.OUTLINE,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            BottomSheetActionButton(
                label = saveLabel,
                type = BottomSheetActionType.FILL,
                enabled = !saving,
                showProgress = saving,
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private enum class BottomSheetActionType { FILL, OUTLINE }

@Composable
private fun BottomSheetActionButton(
    label: String,
    type: BottomSheetActionType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showProgress: Boolean = false,
) {
    val isFill = type == BottomSheetActionType.FILL
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = if (isFill) AppColors.DeepBrown900 else Color.White,
        shape = RoundedCornerShape(999.dp),
        border = if (isFill) null else BorderStroke(1.dp, AppColors.DeepBrown900),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = if (isFill) FontWeight.Bold else FontWeight.Medium,
                    color = if (isFill) Color.White else AppColors.DeepBrown900,
                    lineHeight = 20.sp,
                ),
            )
        }
    }
}

@Composable
private fun AddPhotoButton(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.White,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, AppColors.DeepBrown950),
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AccentBlue, lineHeight = 18.sp))
        }
    }
}

@Preview(
    name = "Call Note Edit",
    showBackground = true,
    widthDp = 360,
    heightDp = 800,
)
@Composable
private fun CallNoteEditScreenPreview() {
    CallRecorderTheme {
        CallNoteEditContent(
            state = CallNoteUiState(
                memo = "방문 일정 전 확인 전화 필요. 1층과 4층 매물을 모두 보고 싶어 하며, 주차 가능 여부를 중요하게 확인함.",
                photos = listOf(
                    CallPhoto(id = "preview-1", url = ""),
                    CallPhoto(id = "preview-2", url = ""),
                    CallPhoto(id = "preview-3", url = ""),
                ),
            ),
            onMemoChange = {},
            onCancel = {},
            onSaveMemo = {},
            onCameraClick = {},
            onGalleryClick = {},
            onDeletePhoto = {},
        )
    }
}
