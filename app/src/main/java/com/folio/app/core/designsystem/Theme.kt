package com.folio.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { System, Light, Dark, Black }

/** True when the active Folio theme is dark or black. Viewers use it to resolve [Paper.Auto] and badge tints. */
val LocalFolioDark = staticCompositionLocalOf { false }

@Composable
fun FolioTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Black -> true
    }
    val colors = when {
        mode == ThemeMode.Black -> FolioBlackColors
        dark -> FolioDarkColors
        else -> FolioLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // The in-app theme can differ from the system one, so set system bar icon contrast explicitly.
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalFolioDark provides dark) {
        MaterialTheme(colorScheme = colors, typography = FolioTypography, shapes = FolioShapes, content = content)
    }
}
