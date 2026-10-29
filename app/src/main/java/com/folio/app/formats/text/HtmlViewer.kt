package com.folio.app.formats.text

import android.content.Context
import android.print.PrintDocumentAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.colors
import com.folio.app.core.designsystem.components.FilterChipsRow
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.HtmlTemplates
import com.folio.app.core.viewer.SafeWebView
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import com.folio.app.core.viewer.WebViewPrintAdapter

/**
 * Web pages (plan M10): rendered in the SafeWebView (no scripts, no network) or as highlighted Source; editable like text.
 * Lives with the text formats because Source mode and editing share their loader and viewer.
 */
object HtmlViewer : FormatViewer {
    override val families = setOf(FileFamily.HTML)
    override val supportsSearch = true
    const val FIT = "Fit to screen"

    // Light/Auto = the page as written; Sepia/Dark tint it for reading.
    override val supportsPaper = true

    override fun toggles(file: OpenedFile) = listOf(FIT)

    override fun canEdit(file: OpenedFile) = TextViewer.canEdit(file)

    override fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter =
        WebViewPrintAdapter(context, file.name) {
            HtmlTemplates.page(TextLoader.load(context, file.uri, TextLoader.WINDOW_BYTES).text, Paper.Light, fitToScreen = true)
        }

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        var source by rememberSaveable(file.uri) { mutableStateOf(false) }
        Column(modifier) {
            FilterChipsRow(listOf("Preview", "Source"), if (source) 1 else 0, { source = it == 1 }, Modifier.padding(vertical = Spacing.sm))
            if (source) TextContent(file, state, onTap, Modifier.fillMaxSize())
            else HtmlPreview(file, state, onTap, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HtmlPreview(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val loaded by produceState<Result<LoadedText>?>(null, file.uri) {
        value = runCatching { TextLoader.load(context, file.uri, TextLoader.WINDOW_BYTES) }
    }
    LaunchedEffect(loaded) { loaded?.getOrNull()?.let { state.editBlockedReason = editBlockedReason(it) } }
    // On unless turned off: pages without a mobile layout tag would otherwise show zoomed out with tiny text.
    LaunchedEffect(Unit) { if (HtmlViewer.FIT !in state.toggles) state.toggles[HtmlViewer.FIT] = true }
    val fit = state.toggles[HtmlViewer.FIT] != false
    val paper = state.paper
    val backdrop = if (paper == Paper.Dark) Color(0xFF1A1A1A) else Color.White
    val result = loaded
    when {
        result == null -> LoadingSkeleton(modifier)
        result.isFailure -> ViewerError("Folio couldn't read this file.", modifier)
        else -> {
            val html = remember(result, paper, fit) { HtmlTemplates.page(result.getOrThrow().text, paper, fit) }
            SafeWebView(html, backdrop, state, onTap, modifier.background(backdrop).navigationBarsPadding())
        }
    }
}
