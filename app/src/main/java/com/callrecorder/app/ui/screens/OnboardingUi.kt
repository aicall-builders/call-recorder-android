package com.callrecorder.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callrecorder.app.R
import com.callrecorder.app.ui.theme.AppColors

@Composable
fun FianoFolderIcon(
    modifier: Modifier = Modifier,
    dark: Boolean = true,
) {
    Image(
        painter = painterResource(id = R.drawable.icon_fiano_app),
        contentDescription = null,
        modifier = modifier.size(80.dp),
    )
}

@Composable
fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AppColors.DeepBrown900,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 16.sp),
        )
    }
}

@Composable
fun OnboardingOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = AppColors.DeepBrown50,
    contentColor: Color = AppColors.DeepBrown50,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp)),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = contentColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = contentColor, lineHeight = 16.sp),
        )
    }
}

@Composable
fun CheckBadge(
    modifier: Modifier = Modifier,
    sizeDp: Int = 54,
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(AppColors.KakaoYellow),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.icon_kakao_check),
            contentDescription = null,
            modifier = Modifier.size((sizeDp * 0.44f).dp),
        )
    }
}
