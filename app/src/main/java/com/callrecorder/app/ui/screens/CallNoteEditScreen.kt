package com.callrecorder.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.callrecorder.app.util.PhotoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/* ── 색상: 앱 공통 톤 ── */
private val DarkNavy       = Color(0xFF3D4D6B)
private val LightBg        = Color(0xFFF0F2F5)
private val WhiteCard      = Color(0xFFFFFFFF)
private val AccentBlue     = Color(0xFF3B7DD8)
private val OnDarkPrimary  = Color(0xFFFFFFFF)
private val OnDarkSub      = Color(0xFFC5D0E0)
private val OnLightPrimary = Color(0xFF1F2A3D)
private val OnLightSub     = Color(0xFF6B7889)
private val OnLightMuted   = Color(0xFF9AA5B5)

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

    Scaffold(
        containerColor = LightBg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().background(LightBg)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            // ═══ 다크 헤더 ═══
            Surface(color = DarkNavy, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = OnDarkPrimary)
                    }
                    Text(callTitle, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = OnDarkPrimary))
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    // ═══ 메모 카드 ═══
                    Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("메모", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnLightPrimary))
                            Spacer(Modifier.height(10.dp))
                            Surface(color = LightBg, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                BasicTextField(
                                    value = state.memo,
                                    onValueChange = vm::onMemoChange,
                                    textStyle = TextStyle(fontSize = 14.sp, color = OnLightPrimary, lineHeight = 21.sp),
                                    cursorBrush = SolidColor(AccentBlue),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp).padding(12.dp),
                                    decorationBox = { inner ->
                                        if (state.memo.isEmpty()) {
                                            Text("이 통화에 대한 메모를 남겨보세요.\n예: 예약 변경, 특이사항, 후속 조치 등",
                                                style = TextStyle(fontSize = 14.sp, color = OnLightMuted, lineHeight = 21.sp))
                                        }
                                        inner()
                                    },
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { vm.saveMemo() },
                                enabled = !state.saving,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.saving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text("메모 저장", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ═══ 사진 카드 ═══
                    Surface(color = WhiteCard, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
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

                            // 추가 버튼 2개
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AddPhotoButton(
                                    icon = { Icon(Icons.Filled.CameraAlt, null, tint = AccentBlue, modifier = Modifier.size(20.dp)) },
                                    label = "카메라",
                                    modifier = Modifier.weight(1f),
                                    enabled = !state.uploading,
                                    onClick = { onCameraClick() },
                                )
                                AddPhotoButton(
                                    icon = { Icon(Icons.Filled.PhotoLibrary, null, tint = AccentBlue, modifier = Modifier.size(20.dp)) },
                                    label = "갤러리",
                                    modifier = Modifier.weight(1f),
                                    enabled = !state.uploading,
                                    onClick = { galleryLauncher.launch("image/*") },
                                )
                            }

                            // 사진 그리드
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
                                            // 삭제 버튼
                                            Box(
                                                Modifier.align(Alignment.TopEnd).padding(4.dp)
                                                    .size(22.dp).clip(RoundedCornerShape(11.dp))
                                                    .background(Color(0xCC000000)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                IconButton(
                                                    onClick = { vm.deletePhoto(photo.id) },
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

                    Spacer(Modifier.height(20.dp))
                }
            }
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
        color = Color(0xFFEFF4FC),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue))
        }
    }
}