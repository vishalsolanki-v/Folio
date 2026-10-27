package com.folio.app.formats.unknown

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.folio.app.core.designsystem.CardShape
import com.folio.app.core.designsystem.CodeTextStyle
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.ExtBadge
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle
import com.folio.app.core.files.MimeSniffer
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.FormatInfo
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.LocalViewerActions
import com.folio.app.core.viewer.ViewerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fallback for anything without a viewer (plan M6): what it is, how big, a raw peek, and a way out. */
object UnknownViewer : FormatViewer {
    override val families = setOf(FileFamily.UNKNOWN)
    override val supportsSearch = false
    override val supportsPaper = false

    private const val PEEK_BYTES = 512

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val actions = LocalViewerActions.current
        val format = FormatInfo.describe(file.mime, file.extension)
        val head by produceState<ByteArray?>(null, file.uri) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        val buffer = ByteArray(MimeSniffer.HEAD_SIZE)
                        var filled = 0
                        while (filled < buffer.size) {
                            val n = input.read(buffer, filled, buffer.size - filled)
                            if (n < 0) break
                            filled += n
                        }
                        buffer.copyOf(filled)
                    }
                }.getOrNull() ?: ByteArray(0)
            }
        }
        var showBytes by remember { mutableStateOf(false) }

        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Spacing.contentMaxWidth).verticalScroll(rememberScrollState()).padding(Spacing.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.xxxl))
                ExtBadge(format.badge, format.tint, format.friendlyName, Modifier.scale(1.6f))
                Spacer(Modifier.height(Spacing.xl))
                Text(format.friendlyName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.xs))
                val size = if (file.sizeBytes >= 0) Formatter.formatShortFileSize(context, file.sizeBytes) else "Unknown size"
                Text(size, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    "Folio can't show this kind of file yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Spacer(Modifier.height(Spacing.xl))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                    PillButton("Open with another app", actions.openWithAnotherApp)
                    val openAsText = actions.openAsText
                    if (openAsText != null && head?.let(MimeSniffer::looksLikeText) == true) {
                        PillButton("Open as text", openAsText, style = PillStyle.Tonal)
                    }
                }
                val bytes = head
                if (bytes != null && bytes.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.lg))
                    TextButton(onClick = { showBytes = !showBytes }) { Text(if (showBytes) "Hide raw bytes" else "Show raw bytes") }
                    if (showBytes) HexPeek(bytes.copyOf(minOf(bytes.size, PEEK_BYTES)))
                }
            }
        }
    }
}

/** "00000000  25 50 44 46 ...  %PDF-..." rows, 16 bytes each. */
@Composable
private fun HexPeek(bytes: ByteArray) {
    val text = remember(bytes) { hexDump(bytes) }
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = CodeTextStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(Spacing.md),
        )
    }
}

internal fun hexDump(bytes: ByteArray): String = bytes.toList().chunked(16).mapIndexed { row, chunk ->
    val hex = chunk.joinToString(" ") { "%02x".format(it) }.padEnd(16 * 3 - 1)
    val ascii = chunk.joinToString("") { b -> (b.toInt() and 0xFF).let { if (it in 32..126) it.toChar().toString() else "." } }
    "%08x  %s  %s".format(row * 16, hex, ascii)
}.joinToString("\n")
