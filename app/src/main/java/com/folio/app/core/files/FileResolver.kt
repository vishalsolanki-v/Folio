package com.folio.app.core.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.folio.app.core.model.FormatInfo
import com.folio.app.core.model.OpenedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/** Turns a URI from any source into an [OpenedFile] (plan §4.3). Reads only the first 4 KB (plus ZIP entry names). */
class FileResolver(private val context: Context) {
    suspend fun resolve(uri: Uri, intentMime: String? = null, referrerHost: String? = null): Result<OpenedFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val (queriedName, size) = nameAndSize(uri)
                val name = queriedName ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "file"
                val head = resolver.openInputStream(uri)?.use(::readHead) ?: throw FileNotFoundException(uri.toString())
                val claimed = intentMime ?: runCatching { resolver.getType(uri) }.getOrNull()
                val mime = MimeSniffer.detect(head, name, claimed) { resolver.openInputStream(uri) }
                val extension = MimeSniffer.extensionOf(name)
                OpenedFile(
                    uri = uri,
                    name = name,
                    extension = extension,
                    mime = mime,
                    family = FormatInfo.describe(mime, extension).family,
                    sizeBytes = size,
                    writable = UriAccess.canWrite(context, uri),
                    sourceApp = SourceApps.label(referrerHost),
                )
            }
        }

    private fun nameAndSize(uri: Uri): Pair<String?, Long> {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(uri.path ?: return null to -1L)
            return file.name to (if (file.exists()) file.length() else -1L)
        }
        // Some providers don't support query; the name then falls back to the URI's last segment.
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString)
                val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong) ?: -1L
                name to size
            }
        }.getOrNull() ?: (null to -1L)
    }

    private fun readHead(input: InputStream): ByteArray {
        val buffer = ByteArray(MimeSniffer.HEAD_SIZE)
        var filled = 0
        while (filled < buffer.size) {
            val n = input.read(buffer, filled, buffer.size - filled)
            if (n < 0) break
            filled += n
        }
        return buffer.copyOf(filled)
    }
}
