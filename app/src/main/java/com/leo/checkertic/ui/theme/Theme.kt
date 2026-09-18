package com.leo.checkertic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = NavyContainer,
    onPrimaryContainer = SkyBlueText,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    tertiary = CompletionGreen,
    background = ObsidianRoot,
    onBackground = TextPrimaryDark,
    surface = DarkSurfaceCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = SubtleGrayLine,
    onSurfaceVariant = TextSecondaryDark,
    error = AccentRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightElectricBlue,
    onPrimary = Color.White,
    primaryContainer = LightNavyContainer,
    onPrimaryContainer = LightSkyBlueText,
    secondary = LightElectricBlue,
    onSecondary = Color.White,
    tertiary = CompletionGreen,
    background = LightRoot,
    onBackground = TextPrimaryLight,
    surface = LightSurfaceCard,
    onSurface = TextPrimaryLight,
    surfaceVariant = SubtleGrayLineLight,
    onSurfaceVariant = TextSecondaryLight,
    error = AccentRed
)

@Composable
fun CheckerTicTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}