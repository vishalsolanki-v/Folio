package com.folio.app.core.files

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Lossless text decoding/encoding for the viewer and editor (plan §4.5).
 * The text keeps its original line separators (the editor preserves them per line), so decode→encode
 * without edits gives back the exact original bytes. [TextDoc.lineSeparator] is only the default for new lines.
 */
object TextCodec {
    enum class Bom(val bytes: ByteArray, val charset: Charset) {
        None(ByteArray(0), Charsets.UTF_8),
        Utf8(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), Charsets.UTF_8),
        Utf16Le(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), Charsets.UTF_16LE),
        Utf16Be(byteArrayOf(0xFE.toByte(), 0xFF.toByte()), Charsets.UTF_16BE),
    }

    class TextDoc(
        val text: String,
        val charset: Charset,
        val bom: Bom,
        val lineSeparator: String,
        /** False when the bytes aren't valid in any encoding we can write back exactly (shown read-only). */
        val editable: Boolean,
    )

    private val windows1252: Charset = Charset.forName("windows-1252")

    fun decode(bytes: ByteArray): TextDoc {
        val bom = Bom.entries.drop(1).firstOrNull { b -> bytes.size >= b.bytes.size && b.bytes.indices.all { bytes[it] == b.bytes[it] } } ?: Bom.None
        val body = ByteBuffer.wrap(bytes, bom.bytes.size, bytes.size - bom.bytes.size)
        val strict = try {
            bom.charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(body).toString()
        } catch (_: CharacterCodingException) {
            null
        }
        if (strict != null) return TextDoc(strict, bom.charset, bom, dominantSeparator(strict), editable = true)

        // Not valid UTF-8/16: show it as windows-1252 (covers Latin-1) but never write it back.
        val lenient = String(bytes, windows1252)
        return TextDoc(lenient, windows1252, Bom.None, dominantSeparator(lenient), editable = false)
    }

    fun encode(doc: TextDoc, text: String): ByteArray {
        check(doc.editable) { "This text can't be saved without changing its encoding" }
        return doc.bom.bytes + text.toByteArray(doc.charset)
    }

    private fun dominantSeparator(text: String): String {
        var crlf = 0
        var lf = 0
        var cr = 0
        var i = 0
        while (i < text.length) {
            when (text[i]) {
                '\r' -> if (i + 1 < text.length && text[i + 1] == '\n') { crlf++; i++ } else cr++
                '\n' -> lf++
            }
            i++
        }
        return when {
            crlf >= lf && crlf >= cr && crlf > 0 -> "\r\n"
            cr > lf -> "\r"
            else -> "\n"
        }
    }
}
