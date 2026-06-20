package com.example.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun buildLightColorScheme(themeColor: ThemeColorOption) = lightColorScheme(
    primary = themeColor.primary,
    onPrimary = Color.White,
    primaryContainer = themeColor.primaryLight,
    onPrimaryContainer = Color(0xFF002104),
    secondary = Color(0xFF4F6354),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D5),
    onSecondaryContainer = Color(0xFF0D1F13),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBFE9F6),
    onTertiaryContainer = Color(0xFF001F27),
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    outlineVariant = Color(0xFFF0F0F0),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private fun buildDarkColorScheme(themeColor: ThemeColorOption) = darkColorScheme(
    primary = themeColor.darkPrimary,
    onPrimary = Color(0xFF00390E),
    primaryContainer = themeColor.darkPrimaryContainer,
    onPrimaryContainer = Color(0xFF9CE49E),
    secondary = Color(0xFFB6CCB9),
    onSecondary = Color(0xFF213528),
    secondaryContainer = Color(0xFF374B3D),
    onSecondaryContainer = Color(0xFFD1E8D5),
    tertiary = Color(0xFFA2CDDB),
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF1F4D57),
    onTertiaryContainer = Color(0xFFBFE9F6),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkDividerColor,
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun MusicPlayerTheme(
    forceDarkMode: Boolean?,
    themeColorIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = forceDarkMode ?: systemDark
    val themeColor = getThemeColorOption(themeColorIndex)

    MaterialTheme(
        colorScheme = if (useDarkTheme) buildDarkColorScheme(themeColor) else buildLightColorScheme(themeColor),
        typography = Typography,
        content = content
    )
}
