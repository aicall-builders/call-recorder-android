package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callrecorder.app.data.local.CallCategory
import com.callrecorder.app.data.local.RecordingEntity
import com.callrecorder.app.data.local.RecordingStatus
import com.callrecorder.app.data.model.Call
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    onCallClick: (String) -> Unit,
    onSettings: () -> Unit,
    vm: CallsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val selectedCategory by vm.selectedCategory.collectAsState()
    val categorized by vm.categorizedRecordings.collectAsState()
    val unclassifiedCount by vm.unclassifiedCount.collectAsState()
    val businessCount by vm.businessCount.collectAsState()
    val personalCount by vm.personalCount.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val filteredCalls by vm.filteredCalls.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("통화 기록", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, "새로고침") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            CallSearchBar(query = searchQuery, onChange = vm::setSearch)

            if (searchQuery.isNotBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SectionHeader("검색 결과", "${filteredCalls.size}건") }
                    if (filteredCalls.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("검색 결과가 없어요", color = Color(0xFF8A8B94))
                            }
                        }
                    } else {
                        items(filteredCalls, key = { it.id }) {
                            CallRow(it, onClick = { onCallClick(it.id) })
                        }
                    }
                }
            } else {

                // ===== 카테고리 탭 =====
                CategoryTabs(
                    selected = selectedCategory,
                    unclassifiedCount = unclassifiedCount,
                    businessCount = businessCount,
                    personalCount = personalCount,
                    onSelect = vm::selectCategory,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // ===== 카테고리별 로컬 녹음 =====
                    if (categorized.isEmpty()) {
                        item { CategoryEmptyState(selectedCategory) }
                    } else {
                        items(categorized, key = { "cat-${it.id}" }) { rec ->
                            ClassifiableRow(
                                rec = rec,
                                onClassify = { cat -> vm.classifyAs(rec.id, cat) },
                            )
                        }
                    }

                    // ===== 서버 완료 통화 (BUSINESS 탭일 때만) =====
                    if (selectedCategory == CallCategory.BUSINESS) {
                        item { Spacer(Modifier.height(8.dp)) }
                        item {
                            SectionHeader("최근 분석 완료", if (state.calls.isEmpty()) "" else "${state.calls.size}건")
                        }
                        if (state.loading && state.calls.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else {
                            items(state.calls, key = { it.id }) {
                                CallRow(it, onClick = { onCallClick(it.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallSearchBar(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("번호 · 요약 · 분류 검색", fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "검색") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "지우기")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun CategoryTabs(
    selected: String,
    unclassifiedCount: Int,
    businessCount: Int,
    personalCount: Int,
    onSelect: (String) -> Unit,
) {
    val tabs = listOf(
        Triple(CallCategory.UNCLASSIFIED, "미분류", unclassifiedCount),
        Triple(CallCategory.BUSINESS, "업무", businessCount),
        Triple(CallCategory.PERSONAL, "개인", personalCount),
    )
    val selectedIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    TabRow(selectedTabIndex = selectedIndex) {
        tabs.forEach { (cat, label, count) ->
            Tab(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label)
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    "$count",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryEmptyState(category: String) {
    val (emoji, msg) = when (category) {
        CallCategory.UNCLASSIFIED -> "✅" to "분류할 통화가 없어요"
        CallCategory.BUSINESS -> "💼" to "업무 통화가 없어요\n미분류에서 업무로 분류해주세요"
        CallCategory.PERSONAL -> "👤" to "개인 통화가 없어요"
        else -> "📞" to "통화가 없어요"
    }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 마스킹 정책:
 * - UNCLASSIFIED: 발신자명/파일명을 *** 처리 (개인정보 보호)
 * - BUSINESS: 원본 표시 (업무 통화로 분류됨)
 * - PERSONAL: 발신자명 마스킹 (개인정보 노출 차단)
 */
private fun displayName(rec: RecordingEntity): String {
    return when (rec.category) {
        CallCategory.BUSINESS -> rec.counterpartNumber ?: rec.fileName
        CallCategory.PERSONAL, CallCategory.UNCLASSIFIED -> {
            // 시간 정보만 살리고 발신자명은 가림
            val datePart = Regex("""_(\d{6,})_(\d{6})""").find(rec.fileName)?.value ?: ""
            "통화 녹음 ***$datePart"
        }
        else -> "통화 녹음 ***"
    }
}

@Composable
private fun ClassifiableRow(
    rec: RecordingEntity,
    onClassify: (String) -> Unit,
) {
    val (statusLabel, statusColor) = when (rec.status) {
        RecordingStatus.PENDING -> "대기 중" to Color(0xFF8C8C8C)
        RecordingStatus.UPLOADING -> "업로드 중…" to MaterialTheme.colorScheme.primary
        RecordingStatus.UPLOADED -> "업로드 완료" to Color(0xFF4A8A3A)
        RecordingStatus.PROCESSING -> "분석 중…" to MaterialTheme.colorScheme.primary
        RecordingStatus.FAILED -> "실패 - 재시도 예정" to MaterialTheme.colorScheme.error
        RecordingStatus.DONE -> "완료" to Color(0xFF4A8A3A)
        else -> rec.status to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (rec.status == RecordingStatus.UPLOADING || rec.status == RecordingStatus.PROCESSING) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp), strokeWidth = 2.dp, color = statusColor,
                        )
                    } else Text("📞")
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName(rec),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(statusLabel, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                }
                Text(
                    "${rec.durationSeconds / 60}분 ${rec.durationSeconds % 60}초",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ===== 분류 버튼 =====
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (rec.category) {
                    CallCategory.UNCLASSIFIED -> {
                        ClassifyButton(
                            label = "💼 업무",
                            isPrimary = true,
                            onClick = { onClassify(CallCategory.BUSINESS) },
                            modifier = Modifier.weight(1f),
                        )
                        ClassifyButton(
                            label = "👤 개인",
                            isPrimary = false,
                            onClick = { onClassify(CallCategory.PERSONAL) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CallCategory.BUSINESS -> {
                        ClassifyButton(
                            label = "👤 개인으로 변경",
                            isPrimary = false,
                            onClick = { onClassify(CallCategory.PERSONAL) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    CallCategory.PERSONAL -> {
                        ClassifyButton(
                            label = "💼 업무로 변경",
                            isPrimary = false,
                            onClick = { onClassify(CallCategory.BUSINESS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassifyButton(
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isPrimary) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label, fontSize = 13.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CallRow(call: Call, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Text("📞") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    call.callerNumber ?: "알 수 없음",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatTime(call.createdAt ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(call.status)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(call.duration ?: 0) / 60}분",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "DONE" -> "요약 완료" to Color(0xFF4A8A3A)
        "PROCESSING" -> "분석 중" to Color(0xFFE08A1A)
        "FAILED" -> "실패" to Color(0xFFC44545)
        else -> status to Color(0xFF8C8C8C)
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 11.sp,
        )
    }
}

private fun formatTime(iso: String?): String = if (iso == null) "" else try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val d = parser.parse(iso.substringBefore('Z').substringBefore('.'))
    SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREAN).apply {
        timeZone = TimeZone.getDefault()
    }.format(d ?: Date())
} catch (e: Exception) { iso }