package com.callrecorder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.R
import com.callrecorder.app.ui.theme.AppColors

@Composable
fun FianoTopHeader(
    modifier: Modifier = Modifier,
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(AppColors.DeepBrown900)
            .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.call_icon_logo),
            contentDescription = "FIANO",
            modifier = Modifier.size(width = 77.142852.dp, height = 36.dp),
            contentScale = ContentScale.Fit,
        )
        FianoHeaderAlarmButton(
            onClick = onNotificationClick,
            hasNotification = hasNotification,
        )
    }
}

@Composable
fun FianoHeaderAlarmButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    Box(
        modifier
            .size(32.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                if (hasNotification) R.drawable.call_icon_alarm_on else R.drawable.call_icon_alarm
            ),
            contentDescription = "알림",
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun FianoListHeroHeader(
    title: String,
    searchText: TextFieldValue,
    onSearchTextChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "전화번호 또는 요약 검색",
    onNotificationClick: () -> Unit = {},
    hasNotification: Boolean = false,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppColors.DeepBrown900),
    ) {
        FianoTopHeader(
            onNotificationClick = onNotificationClick,
            hasNotification = hasNotification,
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                title,
                style = TextStyle(
                    fontSize = 18.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFFE8ECF2)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (searchText.text.isEmpty()) {
                            Text(
                                placeholder,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    lineHeight = 16.sp,
                                    color = AppColors.DeepBrown500,
                                ),
                            )
                        }
                        BasicTextField(
                            value = searchText,
                            onValueChange = onSearchTextChange,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                lineHeight = 16.sp,
                                color = AppColors.DeepBrown950,
                            ),
                            cursorBrush = SolidColor(AppColors.DeepBrown950),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.call_icon_search),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
