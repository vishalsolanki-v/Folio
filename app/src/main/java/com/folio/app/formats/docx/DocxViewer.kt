package com.folio.app.formats.docx

import android.content.Context
import android.net.Uri
import android.print.PrintDocumentAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.colors
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.HtmlTemplates
import com.folio.app.core.viewer.SafeWebView
import com.folio.app.core.viewer.SimplifiedNote
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
                SimplifiedNote(
                    "Simplified layout",
                    "Folio shows Word files in a clean reading layout. Some formatting may look different. " +
                        "To see the exact layout, use Open with another app.",
                )
                val html = remember(result, paper) { HtmlTemplates.wrap(result.getOrThrow(), paper) }
                SafeWebView(html, paper.background, state, onTap, Modifier.fillMaxSize().navigationBarsPadding())
            }
        }
    }
}
