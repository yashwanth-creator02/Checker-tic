package com.leo.checkertic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

private fun materialSchemeFor(c: AppColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.accent,
        onPrimary = Color.White,
        primaryContainer = c.accentContainer,
        onPrimaryContainer = c.onAccentContainer,
        secondary = c.accent,
        onSecondary = Color.White,
        tertiary = c.success,
        background = c.root,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceRaised,
        onSurfaceVariant = c.textSecondary,
        outline = c.hairline,
        scrim = c.scrim,
        error = c.danger
    )
} else {
    lightColorScheme(
        primary = c.accent,
        onPrimary = Color.White,
        primaryContainer = c.accentContainer,
        onPrimaryContainer = c.onAccentContainer,
        secondary = c.accent,
        onSecondary = Color.White,
        tertiary = c.success,
        background = c.root,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceRaised,
        onSurfaceVariant = c.textSecondary,
        outline = c.hairline,
        scrim = c.scrim,
        error = c.danger
    )
}

/**
 * The app theme.
 *
 * Provides both the Material 3 scheme (so stock M3 components look right) and
 * the richer [AppTheme] token set, which app code should prefer. The Material
 * scheme is derived from the tokens and never the other way round, so there is
 * exactly one place to change a colour.
 */
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

    val colors = appColorsFor(isDark)
    val scheme = remember(isDark) { materialSchemeFor(colors) }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppSpacing provides AppSpacing(),
        LocalAppRadius provides AppRadius(),
        LocalAppSizes provides AppSizes(),
        LocalAppMotion provides AppMotion()
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content
        )
    }
}
