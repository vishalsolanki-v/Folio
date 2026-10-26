package com.folio.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Warm neutral palette (docs/plan.md §3.1). Every M3 role is set so no default purple can leak in.

val FolioLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F1F1E),
    onPrimary = Color(0xFFFAF9F5),
    primaryContainer = Color(0xFFEAE7DF),
    onPrimaryContainer = Color(0xFF191918),
    inversePrimary = Color(0xFFECEAE4),
    secondary = Color(0xFF63625E),
    onSecondary = Color(0xFFFAF9F5),
    secondaryContainer = Color(0xFFEAE7DF),
    onSecondaryContainer = Color(0xFF191918),
    tertiary = Color(0xFF2E5AAC),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE4F5),
    onTertiaryContainer = Color(0xFF14284D),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF191918),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF191918),
    surfaceVariant = Color(0xFFEAE7DF),
    onSurfaceVariant = Color(0xFF63625E),
    surfaceTint = Color(0xFFFAF9F5),
    inverseSurface = Color(0xFF262624),
    inverseOnSurface = Color(0xFFECEAE4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFB9B6AD),
    outlineVariant = Color(0xFFE2DFD7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF9F5),
    surfaceDim = Color(0xFFE6E3DB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F1EC),
    surfaceContainer = Color(0xFFEEECE6),
    surfaceContainerHigh = Color(0xFFEAE7DF),
    surfaceContainerHighest = Color(0xFFE4E1D9),
)

val FolioDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFECEAE4),
    onPrimary = Color(0xFF141413),
    primaryContainer = Color(0xFF262624),
    onPrimaryContainer = Color(0xFFECEAE4),
    inversePrimary = Color(0xFF1F1F1E),
    secondary = Color(0xFF9E9C96),
    onSecondary = Color(0xFF141413),
    secondaryContainer = Color(0xFF262624),
    onSecondaryContainer = Color(0xFFECEAE4),
    tertiary = Color(0xFF8AB0F5),
    onTertiary = Color(0xFF0E1F3D),
    tertiaryContainer = Color(0xFF1F3561),
    onTertiaryContainer = Color(0xFFDCE4F5),
    background = Color(0xFF141413),
    onBackground = Color(0xFFECEAE4),
    surface = Color(0xFF141413),
    onSurface = Color(0xFFECEAE4),
    surfaceVariant = Color(0xFF262624),
    onSurfaceVariant = Color(0xFF9E9C96),
    surfaceTint = Color(0xFF141413),
    inverseSurface = Color(0xFFECEAE4),
    inverseOnSurface = Color(0xFF191918),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF5C5B57),
    outlineVariant = Color(0xFF2E2E2B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2B2B29),
    surfaceDim = Color(0xFF141413),
    surfaceContainerLowest = Color(0xFF0F0F0E),
    surfaceContainerLow = Color(0xFF1C1C1A),
    surfaceContainer = Color(0xFF21211F),
    surfaceContainerHigh = Color(0xFF262624),
    surfaceContainerHighest = Color(0xFF2E2E2B),
)

val FolioBlackColors: ColorScheme = FolioDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceTint = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF1C1C1A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0E0E0D),
    surfaceContainer = Color(0xFF141413),
    surfaceContainerHigh = Color(0xFF1C1C1A),
    surfaceContainerHighest = Color(0xFF232321),
    outlineVariant = Color(0xFF232321),
)

/** Document "paper" for viewers that support it (plan §3.1). Auto resolves from the app theme. */
enum class Paper { Auto, Light, Sepia, Dark }

data class PaperColors(val background: Color, val ink: Color)

fun Paper.colors(appIsDark: Boolean): PaperColors = when (this) {
    Paper.Light -> PaperColors(Color(0xFFFFFFFF), Color(0xFF191918))
    Paper.Sepia -> PaperColors(Color(0xFFF4ECD8), Color(0xFF3B2F1E))
    Paper.Dark -> PaperColors(Color(0xFF1A1A19), Color(0xFFE6E3DC))
    Paper.Auto -> (if (appIsDark) Paper.Dark else Paper.Light).colors(appIsDark)
}

/** Extension badge tints (plan §3.1). Badge background is the tint at 14% alpha; text is the tint. */
enum class BadgeTint(private val base: Color) {
    Pdf(Color(0xFFC0504D)),
    Word(Color(0xFF3B6EA5)),
    Sheet(Color(0xFF3D8B5F)),
    Slides(Color(0xFFC2793A)),
    Text(Color(0xFF6B6A65)),
    Code(Color(0xFF7E57C2)),
    Image(Color(0xFF2F8F9D)),
    Media(Color(0xFFB0476E)),
    Archive(Color(0xFFB87D2D)),
    Unknown(Color(0xFF6B6A65));

    fun color(dark: Boolean): Color = if (dark) lerp(base, Color.White, 0.25f) else base
}
