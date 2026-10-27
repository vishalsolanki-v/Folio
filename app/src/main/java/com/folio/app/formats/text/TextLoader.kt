package com.folio.app.formats.text

import android.content.Context
import android.net.Uri
import com.folio.app.core.files.TextCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Text for display. [doc] is null when only part of the file was read (it can't be saved back then). */
class LoadedText(val text: String, val doc: TextCodec.TextDoc?, val truncated: Boolean)

object TextLoader {
    const val WINDOW_BYTES = 5L * 1024 * 1024

    /** Reads at most [limitBytes]; a longer file is cut at the last line break inside the window (plan §4.5). */
    suspend fun load(context: Context, uri: Uri, limitBytes: Long): LoadedText = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (total <= limitBytes) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), limitBytes + 1 - total).toInt())
                if (n < 0) break
                out.write(buffer, 0, n)
                total += n
            }
            out.toByteArray()
        } ?: throw FileNotFoundException(uri.toString())

        if (bytes.size <= limitBytes) {
            val doc = TextCodec.decode(bytes)
            LoadedText(doc.text, doc, truncated = false)
        } else {
            // Cut after the last '\n' so a multi-byte character is never split (UTF-16 LE keeps its 0x00 high byte).
            val window = bytes.copyOf(limitBytes.toInt())
            var cut = window.indexOfLast { it == '\n'.code.toByte() } + 1
            if (cut > 0 && window.size > 1 && window[0] == 0xFF.toByte() && window[1] == 0xFE.toByte()) cut++
            val part = TextCodec.decode(if (cut in 1..window.size) window.copyOf(cut) else window)
            LoadedText(part.text, null, truncated = true)
        }
    }
}
