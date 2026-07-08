package com.callrecorder.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FIANO 디자인 시스템에서 추출한 색상 토큰.
 * Material3 컬러스킴에 의존하지 않고 Composable에서 직접 참조한다.
 */
object AppColors {
    // ===== Primitive: Fiano Black =====
    val FianoBlack950 = Color(0xFF07090C)
    val FianoBlack900 = Color(0xFF101418)
    val FianoBlack800 = Color(0xFF2A2F36)
    val FianoBlack700 = Color(0xFF454C55)
    val FianoBlack600 = Color(0xFF6B7078)
    val FianoBlack500 = Color(0xFF8A9098)
    val FianoBlack400 = Color(0xFFA8AFB6)
    val FianoBlack300 = Color(0xFFC7CDD3)
    val FianoBlack200 = Color(0xFFE2E6EA)
    val FianoBlack100 = Color(0xFFF4F6F8)
    val FianoBlack50 = Color(0xFFFAFBFC)

    // 기존 화면 호환 alias. primitive/deep-brown 토큰은 primitive/fiano-black 토큰으로 대체됨.
    val DeepBrown950 = FianoBlack950
    val DeepBrown900 = FianoBlack900
    val DeepBrown800 = FianoBlack800
    val DeepBrown700 = FianoBlack700
    val DeepBrown600 = FianoBlack600
    val DeepBrown500 = FianoBlack500
    val DeepBrown400 = FianoBlack400
    val DeepBrown300 = FianoBlack300
    val DeepBrown200 = FianoBlack200
    val DeepBrown100 = FianoBlack100
    val DeepBrown50 = FianoBlack50

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
