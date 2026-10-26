package com.folio.app.core.designsystem

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Light + dark preview pair used by every design-system component. */
@Preview(name = "Light", showBackground = true, backgroundColor = 0xFFFAF9F5)
@Preview(name = "Dark", showBackground = true, backgroundColor = 0xFF141413, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class FolioPreviews

@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    FolioTheme { Surface(content = content) }
}
