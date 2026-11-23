package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { System, Light, Dark }

private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalSizes = staticCompositionLocalOf { Sizes() }
private val LocalMotion = staticCompositionLocalOf { Motion() }
private val LocalTeenaColors = staticCompositionLocalOf<TeenaColors> { error("TeenaTheme missing") }

object TeenaTheme {
    val colors: TeenaColors @Composable @ReadOnlyComposable get() = LocalTeenaColors.current
    val spacing: Spacing @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val sizes: Sizes @Composable @ReadOnlyComposable get() = LocalSizes.current
    val motion: Motion @Composable @ReadOnlyComposable get() = LocalMotion.current
}

@Composable
fun TeenaTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = if (dark) darkScheme(Brand.accentDark) else lightScheme(Brand.accentLight)
    val extras = remember(scheme, dark) { buildExtras(scheme, dark) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalTeenaColors provides extras,
        LocalSpacing provides Spacing(),
        LocalSizes provides Sizes(),
        LocalMotion provides Motion()
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TeenaTypography,
            shapes = TeenaShapes,
            content = content
        )
    }
}
