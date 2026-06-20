package com.example.player.ui.theme

import androidx.compose.ui.graphics.Color

data class ThemeColorOption(
    val index: Int,
    val primary: Color,
    val primaryVariant: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val darkPrimary: Color,
    val darkPrimaryContainer: Color,
    val label: String
)

val THEME_COLOR_OPTIONS = listOf(

    ThemeColorOption(
        index = 0,
        primary = Color(0xFF1DB954),
        primaryVariant = Color(0xFF1ED760),
        primaryDark = Color(0xFF158A3E),
        primaryLight = Color(0xFFE8F5E9),
        darkPrimary = Color(0xFF81C784),
        darkPrimaryContainer = Color(0xFF005319),
        label = "绿色"
    ),

    ThemeColorOption(
        index = 1,
        primary = Color(0xFFE53935),
        primaryVariant = Color(0xFFEF5350),
        primaryDark = Color(0xFFB71C1C),
        primaryLight = Color(0xFFFFEBEE),
        darkPrimary = Color(0xFFEF9A9A),
        darkPrimaryContainer = Color(0xFFB71C1C),
        label = "红色"
    ),

    ThemeColorOption(
        index = 2,
        primary = Color(0xFF1E88E5),
        primaryVariant = Color(0xFF42A5F5),
        primaryDark = Color(0xFF1565C0),
        primaryLight = Color(0xFFE3F2FD),
        darkPrimary = Color(0xFF90CAF9),
        darkPrimaryContainer = Color(0xFF1565C0),
        label = "蓝色"
    ),

    ThemeColorOption(
        index = 3,
        primary = Color(0xFFFF9800),
        primaryVariant = Color(0xFFFFB74D),
        primaryDark = Color(0xFFE65100),
        primaryLight = Color(0xFFFFF3E0),
        darkPrimary = Color(0xFFFFB74D),
        darkPrimaryContainer = Color(0xFFE65100),
        label = "橙色"
    ),

    ThemeColorOption(
        index = 4,
        primary = Color(0xFF8E24AA),
        primaryVariant = Color(0xFFAB47BC),
        primaryDark = Color(0xFF6A1B9A),
        primaryLight = Color(0xFFF3E5F5),
        darkPrimary = Color(0xFFCE93D8),
        darkPrimaryContainer = Color(0xFF6A1B9A),
        label = "紫色"
    ),

    ThemeColorOption(
        index = 5,
        primary = Color(0xFF00ACC1),
        primaryVariant = Color(0xFF26C6DA),
        primaryDark = Color(0xFF00838F),
        primaryLight = Color(0xFFE0F7FA),
        darkPrimary = Color(0xFF80DEEA),
        darkPrimaryContainer = Color(0xFF00838F),
        label = "青色"
    ),

    ThemeColorOption(
        index = 6,
        primary = Color(0xFFE91E63),
        primaryVariant = Color(0xFFF06292),
        primaryDark = Color(0xFFAD1457),
        primaryLight = Color(0xFFFCE4EC),
        darkPrimary = Color(0xFFF48FB1),
        darkPrimaryContainer = Color(0xFFAD1457),
        label = "粉色"
    ),
)

fun getThemeColorOption(index: Int): ThemeColorOption =
    THEME_COLOR_OPTIONS.getOrElse(index) { THEME_COLOR_OPTIONS[2] }

val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFDFDFD)
val LightSurfaceVariant = Color(0xFFF0EDE8)
val LightSurfaceElevated = Color(0xFFFFFFFF)

val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF5A5A5A)
val TextTertiary = Color(0xFF8A8A8A)

val AccentGreen = Color(0xFF1DB954)
val AccentGreenVariant = Color(0xFF1ED760)
val AccentGreenDark = Color(0xFF158A3E)
val AccentGreenLight = Color(0xFFE8F5E9)

val AccentBlue = Color(0xFF1565C0)
val AccentBlueVariant = Color(0xFF42A5F5)

val AccentOrange = Color(0xFFFF8A50)
val AccentPurple = Color(0xFF7C4DFF)

val BottomBarBackground = Color(0xFFFFFFFF)
val BottomBarInactive = Color(0xFF9E9E9E)

val DividerColor = Color(0xFFE0E0E0)
val CardBackground = Color(0xFFFFFFFF)
val CardStrokeColor = Color(0xFFF0F0F0)

val ErrorRed = Color(0xFFCF6679)
val WarningYellow = Color(0xFFF57C00)

val ScrimColor = Color(0x80000000)

val GradientStart = Color(0xFFF5F5F5)
val GradientMid = Color(0xFFE8F5E9)
val GradientEnd = Color(0xFFFFF8E1)

val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2C2C2C)

val DarkTextPrimary = Color(0xFFE8E8E8)
val DarkTextSecondary = Color(0xFFB0B0B0)
val DarkTextTertiary = Color(0xFF8A8A8A)

val DarkCardBackground = Color(0xFF2C2C2C)
val DarkDividerColor = Color(0xFF49454F)
val DarkBottomBarBackground = Color(0xFF1E1E1E)

val DarkGradientStart = Color(0xFF1A1A2E)
val DarkGradientMid = Color(0xFF16213E)
val DarkGradientEnd = Color(0xFF0F3460)
