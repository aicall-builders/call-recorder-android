package com.callrecorder.app.onboarding

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.R
import com.callrecorder.app.CallRecorderApp
import com.callrecorder.app.data.model.UpdateDomainRequest
import com.callrecorder.app.ui.screens.OnboardingPrimaryButton
import com.callrecorder.app.ui.theme.AppColors
import kotlinx.coroutines.launch

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

private data class DomainOption(val code: String, val iconRes: Int, val label: String)

private val domainOptions = listOf(
    DomainOption("real_estate", R.drawable.icon_biz_real_estate, "부동산중개업"),
    DomainOption("education", R.drawable.icon_biz_education, "교육사업"),
    DomainOption("insurance", R.drawable.icon_biz_insurance, "보험설계업"),
    DomainOption("construction", R.drawable.icon_biz_construction, "시공업"),
    DomainOption("retail", R.drawable.icon_biz_retail, "판매업"),
)

@Composable
fun BusinessTypeScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(context.selectedDomain() ?: "real_estate") }
    var saving by remember { mutableStateOf(false) }

    fun saveAndContinue() {
        if (saving) return
        saving = true
        val code = selected
        context.setSelectedDomain(code)
        scope.launch {
            runCatching {
                CallRecorderApp.instance.container.api.updateDomain(UpdateDomainRequest(code))
            }.onFailure {
                Log.e("Domain", "서버 저장 실패: ${it.message}")
            }
            onDone()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DeepBrown100),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 20.dp),
            ) {
                IconButton(
                    onClick = { },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icon_onboarding_back),
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.icon_timeline_marker),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "FIANO에게 알려주세요!",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.SignalRed500, lineHeight = 16.sp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "어떤 일을 하시나요?",
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B1F2A), lineHeight = 32.sp),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "업종에 맞춰 통화 키워드와 분석 기준을\n미리 맞춰드려요. 나중에 바꿀 수 있어요.",
                    style = TextStyle(fontSize = 18.sp, color = Color(0xFF5A5F6C), lineHeight = 24.sp, letterSpacing = (-0.5).sp),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(27.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    domainOptions.forEach { option ->
                        BusinessOptionRow(
                            option = option,
                            selected = selected == option.code,
                            enabled = !saving,
                            onClick = { selected = option.code },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingPrimaryButton(
                    text = if (saving) "저장 중..." else "다음",
                    onClick = ::saveAndContinue,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = AppColors.DeepBrown900,
                )

                Text(
                    text = "건너뛰기",
                    modifier = Modifier
                        .clickable(enabled = !saving, onClick = onDone)
                        .padding(16.dp),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF474B6B), lineHeight = 16.sp),
                )
            }
        }
    }
}

@Composable
private fun BusinessOptionRow(
    option: DomainOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) AppColors.DeepBrown50 else Color.White)
            .then(
                if (selected) Modifier.border(2.dp, AppColors.DeepBrown900, RoundedCornerShape(15.dp))
                else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = option.iconRes),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }

        Text(
            text = option.label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) AppColors.DeepBrown900 else AppColors.DeepBrown500,
                lineHeight = 16.sp,
            ),
        )

        Image(
            painter = painterResource(
                id = if (selected) R.drawable.icon_biz_check_selected else R.drawable.icon_biz_check_unselected,
            ),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    }
}
