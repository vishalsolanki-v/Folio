package com.folio.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing scale and layout constants (plan §3.3). */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val screen = 20.dp // horizontal screen padding on phones
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    val contentMaxWidth = 760.dp // centered column on tablets / foldables
    val rowHeight = 64.dp
    val touchTarget = 48.dp
    val hairline = 1.dp
}

val BadgeShape = RoundedCornerShape(6.dp)
val CardShape = RoundedCornerShape(12.dp)
val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val PillShape = RoundedCornerShape(percent = 50)

val FolioShapes = Shapes(
    extraSmall = BadgeShape,
    small = RoundedCornerShape(8.dp),
    medium = CardShape,
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
