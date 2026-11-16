package com.hdlee73.dailyhabit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 시안 A '클리어': iOS 설정 앱처럼 연회색 바탕 + 흰 카드(묶음 목록) + 파란 포인트
val AccentBlue = Color(0xFF2F6FEB)
val SuccessGreen = Color(0xFF30B566)

private val Light = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3ECFD),
    onPrimaryContainer = Color(0xFF0B2A66),
    secondary = Color(0xFF4A5568),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAF0),
    onSecondaryContainer = Color(0xFF1C2333),
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF4E6),
    onTertiaryContainer = Color(0xFF0D3B20),
    background = Color(0xFFF2F3F7),
    onBackground = Color(0xFF14161C),
    surface = Color(0xFFF2F3F7),
    onSurface = Color(0xFF14161C),
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF7A808C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFEDEEF3),
    surfaceContainerHigh = Color(0xFFE8EAF0),
    surfaceContainerHighest = Color(0xFFDFE2E9),
    outline = Color(0xFFB4B9C4),
    outlineVariant = Color(0xFFE4E6EC),
    error = Color(0xFFE5484D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5B8EF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C3566),
    onPrimaryContainer = Color(0xFFDCE7FD),
    secondary = Color(0xFFB8BFCC),
    onSecondary = Color(0xFF1C2333),
    secondaryContainer = Color(0xFF2A2D35),
    onSecondaryContainer = Color(0xFFE8EAF0),
    tertiary = Color(0xFF3DCB76),
    onTertiary = Color(0xFF062012),
    tertiaryContainer = Color(0xFF16402A),
    onTertiaryContainer = Color(0xFFDDF4E6),
    background = Color(0xFF0F1013),
    onBackground = Color(0xFFF1F2F5),
    surface = Color(0xFF0F1013),
    onSurface = Color(0xFFF1F2F5),
    surfaceVariant = Color(0xFF2A2D35),
    onSurfaceVariant = Color(0xFF959BA7),
    surfaceContainerLowest = Color(0xFF1C1D22),
    surfaceContainerLow = Color(0xFF17181C),
    surfaceContainer = Color(0xFF23252B),
    surfaceContainerHigh = Color(0xFF2A2D35),
    surfaceContainerHighest = Color(0xFF33363F),
    outline = Color(0xFF5A5F6B),
    outlineVariant = Color(0xFF2A2D35),
    error = Color(0xFFFF6B6F),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

/** 복음·기도문용 본문 서체 (시안 A: 고딕, 넉넉한 줄 간격) */
val ScriptureStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 30.sp)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DailyHabitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** 루틴 색상 */
val RoutinePalette = listOf(
    Color(0xFF5B5FC7), // 남색
    Color(0xFFE07A3F), // 주황
    Color(0xFF2E9E7A), // 초록
    Color(0xFFD9547A), // 장미
    Color(0xFF3A8FD1), // 하늘
    Color(0xFF9B59B6), // 보라
    Color(0xFFC9A227), // 금색
    Color(0xFF6D7A88), // 회청
)

fun routineColor(index: Int): Color = RoutinePalette[index.mod(RoutinePalette.size)]

/** 전례색 */
fun liturgicalColor(day: String): Color = when {
    day.startsWith("(백)") -> Color(0xFFD4AF37)
    day.startsWith("(홍)") -> Color(0xFFC62828)
    day.startsWith("(녹)") -> Color(0xFF2E7D32)
    day.startsWith("(자)") -> Color(0xFF6A1B9A)
    day.startsWith("(장미)") -> Color(0xFFE48FB1)
    day.startsWith("(흑)") -> Color(0xFF37474F)
    else -> Color(0xFFD4AF37)
}
