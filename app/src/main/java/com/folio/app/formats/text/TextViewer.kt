package com.folio.app.formats.text

import android.content.Context
import android.print.PrintDocumentAdapter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folio.app.appContainer
import com.folio.app.core.data.Settings
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.colors
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState

/** Plain text, logs, code and data files (plan M7), read-only in Sora with syntax colours. */
object TextViewer : FormatViewer {
    const val EDIT_LIMIT_BYTES = 5L * 1024 * 1024
    const val PRETTY = "Pretty view"

    override val families = setOf(FileFamily.TEXT, FileFamily.CODE)
    override val supportsSearch = true
    override val supportsPaper = true

    override fun canEdit(file: OpenedFile) =
        file.family in setOf(FileFamily.TEXT, FileFamily.CODE, FileFamily.MARKDOWN, FileFamily.HTML) && file.sizeBytes in 0..EDIT_LIMIT_BYTES

    override fun toggles(file: OpenedFile) = if (Pretty.supports(file.extension, file.mime)) listOf(PRETTY) else emptyList()

    override fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter = TextPrintAdapter(context, file.uri, file.name)

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        TextContent(file, state, onTap, modifier)
    }
}

/** Shared by [TextViewer] and the Markdown viewer's Source mode. */
@Composable
internal fun TextContent(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var limit by rememberSaveable(file.uri) { mutableLongStateOf(TextLoader.WINDOW_BYTES) }
    val loaded by produceState<Result<LoadedText>?>(null, file.uri, limit) {
        value = runCatching { TextLoader.load(context, file.uri, limit) }
    }

    LaunchedEffect(loaded) {
        val text = loaded?.getOrNull() ?: return@LaunchedEffect
        state.editBlockedReason = when {
            file.sizeBytes > TextViewer.EDIT_LIMIT_BYTES || text.truncated -> "This file is too big to edit (over 5 MB)."
            text.doc?.editable == false -> "This file uses an unusual text encoding, so saving could change it."
            else -> null
        }
        state.infoExtras = mapOf("Lines" to "%,d".format(text.text.count { it == '\n' } + 1))
    }

    val result = loaded
    val s = settings
    when {
        result == null || s == null -> LoadingSkeleton(modifier)
        result.isFailure -> ViewerError("Folio couldn't read this file.", modifier)
        else -> {
            val text = result.getOrThrow()
            val pretty = state.toggles[TextViewer.PRETTY] == true
            val shown = remember(text, pretty) { if (pretty) Pretty.format(text.text, file.extension, file.mime) ?: text.text else text.text }
            Box(modifier) {
                SoraText(
                    text = shown,
                    scope = TextMate.scopeFor(file.extension.ifEmpty { if (file.family == FileFamily.MARKDOWN) "md" else "" }),
                    readOnly = true,
                    look = soraLook(state.paper, s),
                    modifier = Modifier.fillMaxSize(),
                    lineSeparator = text.doc?.lineSeparator ?: "\n",
                    search = state,
                    onTap = onTap,
                )
                if (text.truncated) {
                    TruncatedBanner(limit, Modifier.align(Alignment.BottomCenter)) { limit += TextLoader.WINDOW_BYTES }
                }
            }
        }
    }
}

@Composable
internal fun soraLook(paper: Paper, settings: Settings): SoraLook {
    val appDark = LocalFolioDark.current
    val dark = paper == Paper.Dark || (paper == Paper.Auto && appDark)
    return SoraLook(
        paper = paper.colors(appDark),
        darkPaper = dark,
        accent = MaterialTheme.colorScheme.tertiary,
        fontSp = settings.editorFontSp,
        wordWrap = settings.wordWrap,
        lineNumbers = settings.lineNumbers,
    )
}

@Composable
private fun TruncatedBanner(limit: Long, modifier: Modifier, onMore: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Text("Showing the first ${limit / (1024 * 1024)} MB", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onMore) { Text("Load more") }
        }
    }
}
