package com.folio.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.folio.app.R

// Bundled variable fonts (SIL OFL, licenses in assets/licenses). Plan §3.2.

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int, opticalSize: Float) = Font(
    R.font.inter,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
)

@OptIn(ExperimentalTextApi::class)
private fun mono(weight: Int) = Font(
    R.font.jetbrains_mono,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter at text optical size, for body and UI. */
val InterText = FontFamily(inter(400, 14f), inter(500, 14f), inter(600, 14f))

/** Inter at display optical size: tighter, crisper headlines. */
val InterDisplay = FontFamily(inter(500, 32f), inter(600, 32f))

val JetBrainsMono = FontFamily(mono(400), mono(500))

// Text follows its own direction (first strong letter): Folio's UI is English, so in right-to-left locales
// "2 items" must not be reordered to "items 2".
val FolioTypography = Typography(
    headlineMedium = TextStyle(textDirection = TextDirection.Content, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(textDirection = TextDirection.Content, fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(textDirection = TextDirection.Content, fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.15).sp),
    titleSmall = TextStyle(textDirection = TextDirection.Content, fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(textDirection = TextDirection.Content, fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(textDirection = TextDirection.Content, fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(textDirection = TextDirection.Content, fontFamily = InterText, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(textDirection = TextDirection.Content, fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(textDirection = TextDirection.Content, fontFamily = InterText, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(textDirection = TextDirection.Content, fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp, fontFeatureSettings = "tnum"),
)

/** Code and numbers in viewers/editors; size is overridden by the editor font-size setting. */
val CodeTextStyle = TextStyle(textDirection = TextDirection.Content, fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
