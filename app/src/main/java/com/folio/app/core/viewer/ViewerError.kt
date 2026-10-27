package com.folio.app.core.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.folio.app.core.designsystem.components.ErrorState
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle

/** The standard "can't show this" screen for any format (plan §2.3). */
@Composable
fun ViewerError(
    message: String = "This file looks damaged, so Folio can't show it.",
    modifier: Modifier = Modifier,
) {
    val actions = LocalViewerActions.current
    ErrorState(message, modifier) {
        PillButton("Open with another app", actions.openWithAnotherApp, style = PillStyle.Tonal)
        PillButton("File info", actions.showInfo, style = PillStyle.Outline)
    }
}
