package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.AppThemeMode

private val LightColors = lightColorScheme(
    primary = MjCrimsonLight,
    onPrimary = MjOnCrimsonLight,
    primaryContainer = MjCrimsonContainerLight,
    onPrimaryContainer = MjOnCrimsonContainerLight,
    secondary = MjSlateLight,
    onSecondary = MjOnSlateLight,
    secondaryContainer = MjSlateContainerLight,
    onSecondaryContainer = MjOnSlateContainerLight,
    tertiary = MjAmberLight,
    onTertiary = MjOnAmberLight,
    tertiaryContainer = MjAmberContainerLight,
    onTertiaryContainer = MjOnAmberContainerLight,
    background = MjBackgroundLight,
    onBackground = MjOnBackgroundLight,
    surface = MjSurfaceLight,
    onSurface = MjOnSurfaceLight,
    surfaceVariant = MjSurfaceVariantLight,
    onSurfaceVariant = MjOnSurfaceVariantLight,
    outline = MjOutlineLight,
    outlineVariant = MjOutlineVariantLight
)

private val DarkColors = darkColorScheme(
    primary = MjCrimsonDark,
    onPrimary = MjOnCrimsonDark,
    primaryContainer = MjCrimsonContainerDark,
    onPrimaryContainer = MjOnCrimsonContainerDark,
    secondary = MjSlateDark,
    onSecondary = MjOnSlateDark,
    secondaryContainer = MjSlateContainerDark,
    onSecondaryContainer = MjOnSlateContainerDark,
    tertiary = MjAmberDark,
    onTertiary = MjOnAmberDark,
    tertiaryContainer = MjAmberContainerDark,
    onTertiaryContainer = MjOnAmberContainerDark,
    background = MjBackgroundDark,
    onBackground = MjOnBackgroundDark,
    surface = MjSurfaceDark,
    onSurface = MjOnSurfaceDark,
    surfaceVariant = MjSurfaceVariantDark,
    onSurfaceVariant = MjOnSurfaceVariantDark,
    outline = MjOutlineDark,
    outlineVariant = MjOutlineVariantDark
)

private val AmoledColors = DarkColors.copy(
    background = MjAmoledBackground,
    surface = MjAmoledSurface,
    surfaceVariant = MjAmoledSurfaceVariant
)

@Composable
fun MjPdfTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (themeMode == AppThemeMode.AMOLED) {
                base.copy(
                    background = MjAmoledBackground,
                    surface = MjAmoledSurface,
                    surfaceVariant = MjAmoledSurfaceVariant
                )
            } else {
                base
            }
        }
        themeMode == AppThemeMode.AMOLED -> AmoledColors
        useDark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MjPdfTypography,
        content = content
    )
}
