package com.callrecorder.app.onboarding

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.UpdateDomainRequest
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// 도메인(업종) 선택값 — 로컬 저장 ("처음 한 번만" 스킵 판단용)
// ─────────────────────────────────────────────────────────────
private const val DOMAIN_PREFS = "domain_prefs"
private const val KEY_DOMAIN = "selected_domain"

fun Context.selectedDomain(): String? =
    getSharedPreferences(DOMAIN_PREFS, Context.MODE_PRIVATE).getString(KEY_DOMAIN, null)

fun Context.hasSelectedDomain(): Boolean = selectedDomain() != null

fun Context.setSelectedDomain(code: String) {
    getSharedPreferences(DOMAIN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_DOMAIN, code)
        .apply()
}

// ─────────────────────────────────────────────────────────────
// 업종 목록 — code 는 백엔드 VALID_DOMAINS 와 동일해야 함
//   {"real_estate","education","insurance","construction","retail"}
// ─────────────────────────────────────────────────────────────
private data class DomainOption(val code: String, val icon: String, val label: String)

private val domainOptions = listOf(
    DomainOption("real_estate",  "🏠", "부동산업"),
    DomainOption("education",    "✏️", "교육사업"),
    DomainOption("insurance",    "🛡️", "보험설계업"),
    DomainOption("construction", "🛠️", "시공업"),
    DomainOption("retail",       "🛒", "판매업"),
)

private val Bg         = Color(0xFFE5ECF6)
private val Primary    = Color(0xFF474B6B)
private val TitleColor = Color(0xFF1B1F2A)
private val DescColor  = Color(0xFF5A5F6C)
private val Unselected = Color(0xFF8C93A1)

// ─────────────────────────────────────────────────────────────
// 화면
// ─────────────────────────────────────────────────────────────
@Composable
fun BusinessTypeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(context.selectedDomain()) }
    var saving by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(32.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(999.dp)).background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("통화비서에게 알려주세요!", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "어떤 일을 하시나요?",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = TitleColor),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "업종에 맞춰 통화 키워드와 분석 기준을\n미리 맞춰드려요. 나중에 바꿀 수 있어요.",
                    style = TextStyle(fontSize = 16.sp, color = DescColor, lineHeight = 24.sp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                Column(
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    domainOptions.forEach { opt ->
                        val isSel = selected == opt.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .then(
                                    if (isSel) Modifier.background(Color.White).border(2.dp, Primary, RoundedCornerShape(15.dp))
                                    else Modifier.background(Color.White.copy(alpha = 0.5f))
                                )
                                .clickable(enabled = !saving) { selected = opt.code }
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(opt.icon, style = TextStyle(fontSize = 18.sp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                opt.label,
                                modifier = Modifier.weight(1f),
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) Primary else Unselected,
                                ),
                            )
                            if (isSel) Text("✓", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Primary))
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // 하단 "다음" — 선택해야 활성화
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val enabled = selected != null && !saving
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (enabled) Primary else Primary.copy(alpha = 0.35f))
                        .then(if (enabled) Modifier.clickable {
                            val code = selected ?: return@clickable
                            saving = true
                            // 로컬 즉시 저장 (UX: 다음부터 스킵 판단)
                            context.setSelectedDomain(code)
                            // 서버 저장 후 다음 화면으로 (실패해도 흐름은 진행)
                            scope.launch {
                                runCatching {
                                    CallRecorderApp.instance.container.api.updateDomain(
                                        UpdateDomainRequest(code)
                                    )
                                }.onFailure {
                                    Log.e("Domain", "서버 저장 실패: ${it.message}")
                                }
                                onDone()
                            }
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (saving) "저장 중…" else "다음",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    )
                }
            }
        }
    }
}