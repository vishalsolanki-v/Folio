package com.folio.app.formats.text

import android.content.Context
import android.print.PrintDocumentAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.folio.app.core.designsystem.CodeTextStyle
import com.folio.app.core.designsystem.InterDisplay
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.PaperColors
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.colors
import com.folio.app.core.designsystem.components.FilterChipsRow
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

/** Markdown: rendered Preview or highlighted Source (plan M7). Searching switches to Source. */
object MarkdownViewer : FormatViewer {
    override val families = setOf(FileFamily.MARKDOWN)
    override val supportsSearch = true
    override val supportsPaper = true

    override fun canEdit(file: OpenedFile) = TextViewer.canEdit(file)

    override fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter = TextPrintAdapter(context, file.uri, file.name)

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        var source by rememberSaveable(file.uri) { mutableStateOf(false) }
        LaunchedEffect(state.query) { if (state.query.isNotEmpty()) source = true }
        Column(modifier) {
            FilterChipsRow(listOf("Preview", "Source"), if (source) 1 else 0, { source = it == 1 }, Modifier.padding(vertical = Spacing.sm))
            if (source) TextContent(file, state, onTap, Modifier.fillMaxSize())
            else MarkdownPreview(file, state, onTap, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MarkdownPreview(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val loaded by produceState<Result<LoadedText>?>(null, file.uri) {
        value = runCatching { TextLoader.load(context, file.uri, TextLoader.WINDOW_BYTES) }
    }
    LaunchedEffect(loaded) { loaded?.getOrNull()?.let { state.editBlockedReason = editBlockedReason(it) } }
    val paper = state.paper.colors(LocalFolioDark.current)
    val result = loaded
    when {
        result == null -> LoadingSkeleton(modifier)
        result.isFailure -> ViewerError("Folio couldn't read this file.", modifier)
        else -> Box(
            modifier.background(paper.background).pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
            contentAlignment = Alignment.TopCenter,
        ) {
            MarkdownBody(result.getOrThrow().text, paper)
        }
    }
}

/** Why a rendered preview's file can't be edited, or null. Shared by Markdown and HTML previews. */
internal fun editBlockedReason(text: LoadedText): String? = when {
    text.truncated -> "This file is too big to edit (over 5 MB)."
    text.doc?.editable == false -> "This file uses an unusual text encoding, so saving could change it."
    else -> null
}

/** Rendered Markdown on [paper], in Folio's type scale. Used by the viewer and the editor's Preview. */
@Composable
internal fun MarkdownBody(text: String, paper: PaperColors, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    val heading = { size: Int, line: Int -> type.titleLarge.copy(fontFamily = InterDisplay, fontWeight = FontWeight.SemiBold, fontSize = size.sp, lineHeight = line.sp) }
    Markdown(
        content = text,
        colors = markdownColor(text = paper.ink, codeBackground = paper.ink.copy(alpha = 0.06f), dividerColor = paper.ink.copy(alpha = 0.12f)),
        typography = markdownTypography(
            h1 = heading(28, 36), h2 = heading(24, 32), h3 = heading(20, 28),
            h4 = type.titleMedium, h5 = type.titleMedium, h6 = type.titleSmall,
            text = type.bodyLarge, paragraph = type.bodyLarge, ordered = type.bodyLarge, bullet = type.bodyLarge, list = type.bodyLarge,
            code = CodeTextStyle, quote = type.bodyLarge.copy(fontStyle = FontStyle.Italic),
        ),
        modifier = modifier
            .widthIn(max = Spacing.contentMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
            .navigationBarsPadding(),
    )
}
