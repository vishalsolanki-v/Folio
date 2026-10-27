package com.folio.app.formats.docx

import android.content.Context
import android.net.Uri
import android.print.PrintDocumentAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.colors
import com.folio.app.core.designsystem.components.FolioBottomSheet
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.HtmlTemplates
import com.folio.app.core.viewer.SafeWebView
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import com.folio.app.core.viewer.WebViewPrintAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zwobble.mammoth.DocumentConverter

/** Word documents (plan M10): mammoth turns DOCX into clean HTML (images inline), shown in the SafeWebView. */
object DocxViewer : FormatViewer {
    override val families = setOf(FileFamily.WORD)
    override val supportsSearch = true
    override val supportsPaper = true

    override fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter =
        WebViewPrintAdapter(context, file.name) { HtmlTemplates.wrap(toHtml(context, file.uri), Paper.Light.colors(false)) }

    private suspend fun toHtml(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        // mammoth already maps Heading 1-6; Word's Title/Subtitle styles would otherwise become plain paragraphs.
        val converter = DocumentConverter().addStyleMap("p[style-name='Title'] => h1.title:fresh\np[style-name='Subtitle'] => h2:fresh")
        context.contentResolver.openInputStream(uri)!!.use { converter.convertToHtml(it).value }
    }

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val body by produceState<Result<String>?>(null, file.uri) {
            value = try {
                Result.success(toHtml(context, file.uri))
            } catch (_: OutOfMemoryError) {
                Result.failure(IllegalStateException("This file is too large to preview on this phone."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val paper = state.paper.colors(LocalFolioDark.current)
        val result = body
        when {
            result == null -> LoadingSkeleton(modifier)
            result.isFailure -> ViewerError(
                result.exceptionOrNull()?.message?.takeIf { it.startsWith("This file is too large") }
                    ?: "This file looks damaged, so Folio can't show it.",
                modifier,
            )
            else -> Column(modifier.background(paper.background)) {
                SimplifiedLayoutNote()
                val html = remember(result, paper) { HtmlTemplates.wrap(result.getOrThrow(), paper) }
                SafeWebView(html, paper.background, state, onTap, Modifier.fillMaxSize().navigationBarsPadding())
            }
        }
    }
}

/** A quiet note that Word layout is simplified, with a one-tap explanation (plan M10). */
@Composable
private fun SimplifiedLayoutNote() {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = { open = true }, modifier = Modifier.padding(end = Spacing.sm)) {
            Text("Simplified layout", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs).size(16.dp),
            )
        }
    }
    if (open) {
        FolioBottomSheet(onDismiss = { open = false }, title = "Simplified layout") {
            Text(
                "Folio shows Word files in a clean reading layout. Some formatting may look different. " +
                    "To see the exact layout, use Open with another app.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
        }
    }
}
