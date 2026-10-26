package com.folio.app.core.files

import com.folio.app.core.model.FormatInfo
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * Decides a file's real MIME type (plan §4.4). Order: magic bytes (incl. looking inside ZIP containers)
 * > extension > intent MIME > text heuristic > application/octet-stream. Pure JVM code, unit-tested.
 */
object MimeSniffer {
    const val HEAD_SIZE = 4096
    private const val OCTET = "application/octet-stream"
    private const val ZIP_MAX_ENTRIES = 200
    private const val ZIP_MAX_BYTES = 2L * 1024 * 1024

    /**
     * @param head the first [HEAD_SIZE] bytes (or fewer for small files)
     * @param name display name, used for its extension
     * @param intentMime MIME the sending app claimed, if any
     * @param openStream opens the file from the start; used only to peek inside ZIP containers
     */
    fun detect(head: ByteArray, name: String, intentMime: String?, openStream: () -> InputStream?): String {
        val extension = extensionOf(name)
        magic(head, extension)?.let { sniffed ->
            if (sniffed != ZIP) return sniffed
            return zipContainer(openStream) ?: zipBasedExtension(extension) ?: ZIP
        }
        FormatInfo.forExtension(extension)?.let { return it.mime }
        intentMime?.lowercase()?.substringBefore(';')?.trim()
            ?.takeIf { it.isNotEmpty() && it !in FormatInfo.GENERIC_MIMES && FormatInfo.forMime(it) != null }
            ?.let { return it }
        return if (looksLikeText(head)) "text/plain" else OCTET
    }

    fun extensionOf(name: String): String =
        name.substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) } ?: ""

    private const val ZIP = "application/zip"
    private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"

    private fun magic(h: ByteArray, extension: String): String? {
        fun at(offset: Int, vararg bytes: Int) = h.size >= offset + bytes.size && bytes.indices.all { h[offset + it] == bytes[it].toByte() }
        fun at(offset: Int, text: String) = at(offset, *text.map { it.code }.toIntArray())
        val textExtension = FormatInfo.isTextExtension(extension)

        return when {
            at(0, "%PDF-") -> "application/pdf"
            at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, "GIF87a") || at(0, "GIF89a") -> "image/gif"
            at(0, "RIFF") && at(8, "WEBP") -> "image/webp"
            at(0, "RIFF") && at(8, "WAVE") -> "audio/wav"
            at(4, "ftyp") -> ftypBrand(String(h, 8, minOf(4, h.size - 8), Charsets.ISO_8859_1))
            at(0, "ID3") -> "audio/mpeg"
            at(0, "fLaC") -> "audio/flac"
            at(0, "OggS") -> "audio/ogg"
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> if (String(h, 0, minOf(64, h.size), Charsets.ISO_8859_1).contains("webm")) "video/webm" else "video/x-matroska"
            at(0, "Rar!") && at(4, 0x1A, 0x07) -> "application/vnd.rar"
            at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> "application/x-7z-compressed"
            at(0, 0x1F, 0x8B) -> "application/gzip"
            at(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) ->
                FormatInfo.forExtension(extension)?.mime?.takeIf { extension in setOf("doc", "xls", "ppt", "msg") } ?: "application/x-ole-storage"
            at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) -> ZIP
            // Weak signatures: plausible as the start of ordinary text, so only trusted for non-text extensions.
            !textExtension && at(0, "BM") && validDibHeader(h) -> "image/bmp"
            !textExtension && h.size >= 2 && h[0] == 0xFF.toByte() && (h[1].toInt() and 0xF6) == 0xF2 -> "audio/mpeg"
            else -> null
        }
    }

    private fun validDibHeader(h: ByteArray): Boolean {
        if (h.size < 18) return false
        val size = (h[14].toInt() and 0xFF) or ((h[15].toInt() and 0xFF) shl 8) or ((h[16].toInt() and 0xFF) shl 16) or ((h[17].toInt() and 0xFF) shl 24)
        return size in setOf(12, 40, 52, 56, 108, 124)
    }

    private fun ftypBrand(brand: String): String = when (brand.trimEnd()) {
        "heic", "heix", "hevc", "heim", "heis", "mif1", "msf1" -> "image/heic"
        "avif", "avis" -> "image/avif"
        "M4A", "M4B" -> "audio/mp4"
        "qt" -> "video/quicktime"
        "3gp4", "3gp5", "3gp6", "3gg6", "3gs7" -> "video/3gpp"
        else -> "video/mp4"
    }

    /** Peeks at ZIP entry names to tell Office/EPUB/APK/ODF apart from a plain ZIP. Null if unreadable. */
    private fun zipContainer(openStream: () -> InputStream?): String? = try {
        openStream()?.let { raw ->
            ZipInputStream(LimitedStream(raw, ZIP_MAX_BYTES)).use { zip ->
                val names = HashSet<String>()
                var mimetype: String? = null
                var count = 0
                while (count < ZIP_MAX_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    count++
                    names += entry.name
                    if (entry.name == "mimetype") mimetype = ByteArray(100).let { buf -> String(buf, 0, maxOf(0, zip.read(buf)), Charsets.US_ASCII).trim() }
                    classify(names, mimetype)?.let { return@use it }
                }
                if (count == 0) null else ZIP
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null // malformed entry names
    }

    private fun classify(names: Set<String>, mimetype: String?): String? = when {
        "word/document.xml" in names -> DOCX
        "xl/workbook.xml" in names -> XLSX
        "ppt/presentation.xml" in names -> PPTX
        mimetype == "application/epub+zip" || "META-INF/container.xml" in names -> "application/epub+zip"
        mimetype?.startsWith("application/vnd.oasis.opendocument.") == true -> mimetype
        "AndroidManifest.xml" in names && "classes.dex" in names -> "application/vnd.android.package-archive"
        else -> null
    }

    private fun zipBasedExtension(extension: String): String? =
        extension.takeIf { it in setOf("docx", "xlsx", "pptx", "epub", "apk", "odt", "ods", "odp") }?.let { FormatInfo.forExtension(it)?.mime }

    /** No NUL bytes (unless a UTF-16 BOM says otherwise) and almost all of it decodes as UTF-8 text. */
    private fun looksLikeText(h: ByteArray): Boolean {
        if (h.isEmpty()) return false
        if (h.size >= 2 && ((h[0] == 0xFF.toByte() && h[1] == 0xFE.toByte()) || (h[0] == 0xFE.toByte() && h[1] == 0xFF.toByte()))) return true
        if (h.any { it == 0.toByte() }) return false
        // The head may cut a multi-byte character in half; drop up to 3 trailing bytes before strict decoding.
        val text = (0..3).firstNotNullOfOrNull { cut ->
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(h, 0, h.size - cut)).toString()
            } catch (_: CharacterCodingException) {
                null
            }
        } ?: return false
        val printable = text.count { it == '\n' || it == '\r' || it == '\t' || !it.isISOControl() }
        return printable >= text.length * 0.95
    }

    /** Stops reading after [limit] bytes so a huge or hostile ZIP can't make the sniffer read forever. */
    private class LimitedStream(input: InputStream, private var remaining: Long) : FilterInputStream(input) {
        override fun read(): Int = if (remaining <= 0) -1 else super.read().also { if (it >= 0) remaining-- }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            return super.read(b, off, minOf(len.toLong(), remaining).toInt()).also { if (it > 0) remaining -= it }
        }
    }
}
