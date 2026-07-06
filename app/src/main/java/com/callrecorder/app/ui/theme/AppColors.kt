package com.callrecorder.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 0705 FIANO 디자인 시스템에서 추출한 색상 토큰.
 * Material3 컬러스킴에 의존하지 않고 Composable에서 직접 참조한다.
 */
object AppColors {
    // ===== Primitive: Deep Brown =====
    val DeepBrown950 = Color(0xFF342D2D)
    val DeepBrown900 = Color(0xFF413838)
    val DeepBrown800 = Color(0xFF4F4545)
    val DeepBrown700 = Color(0xFF5F5555)
    val DeepBrown600 = Color(0xFF746A6A)
    val DeepBrown500 = Color(0xFF8E8585)
    val DeepBrown400 = Color(0xFFA59C9C)
    val DeepBrown300 = Color(0xFFC3BBBB)
    val DeepBrown200 = Color(0xFFDAD4D4)
    val DeepBrown100 = Color(0xFFE9E4E4)
    val DeepBrown50 = Color(0xFFF6F3F3)

    // ===== Primitive: Signal Red =====
    val SignalRed900 = Color(0xFF7A0C00)
    val SignalRed800 = Color(0xFF991000)
    val SignalRed700 = Color(0xFFC61500)
    val SignalRed600 = Color(0xFFF11800)
    val SignalRed500 = Color(0xFFFF3A22)
    val SignalRed300 = Color(0xFFFF8F80)
    val SignalRed100 = Color(0xFFFFE1DC)
    val SignalRed50 = Color(0xFFFFF3F1)

    // ===== 배경 =====
    val Background = DeepBrown50
    val Surface = Color(0xFFFFFFFF)           // 카드/표면

    // ===== 텍스트 =====
    val TextPrimary = DeepBrown900
    val TextSecondary = DeepBrown500
    val TextOnPrimary = Color(0xFFFFFFFF)     // 파란 버튼 위 흰 글씨

    // ===== 브랜드 =====
    val Brand = DeepBrown900
    val BrandDark = DeepBrown950
    val BrandSoft = DeepBrown100
    val Accent = SignalRed500

    // 기존 화면 호환 alias. 새 화면에서는 Brand/BrandDark/BrandSoft/Accent를 우선 사용.
    val BrandBlue = Brand
    val BrandBlueDark = BrandDark
    val BrandBlueSoft = BrandSoft

    // ===== 카카오 =====
    val KakaoYellow = Color(0xFFFEE500)
    val KakaoBlack = Color(0xFF191919)

    // ===== 보조 =====
    val Divider = DeepBrown100
    val IconBoxBg = Color(0xFFFFFFFF)         // 로고 박스 배경
    val IconBoxShadow = Color(0x14000000)     // 로고 박스 그림자
}
