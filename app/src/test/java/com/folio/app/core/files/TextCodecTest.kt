package com.folio.app.core.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCodecTest {
    private fun roundTrip(bytes: ByteArray) {
        val doc = TextCodec.decode(bytes)
        assertTrue("should be editable", doc.editable)
        assertArrayEquals(bytes, TextCodec.encode(doc, doc.text))
    }

    @Test fun utf8Lf() = roundTrip("Hello\nwörld\n".toByteArray())
    @Test fun utf8NoTrailingNewline() = roundTrip("no newline at end".toByteArray())
    @Test fun utf8Crlf() = roundTrip("a\r\nb\r\n".toByteArray())
    @Test fun mixedLineEndings() = roundTrip("a\r\nb\nc\rd".toByteArray())
    @Test fun utf8Bom() = roundTrip(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "bom text\n".toByteArray())
    @Test fun utf16LeBomCrlf() = roundTrip(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "héllo\r\nworld\r\n".toByteArray(Charsets.UTF_16LE))
    @Test fun utf16BeBom() = roundTrip(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "big endian".toByteArray(Charsets.UTF_16BE))
    @Test fun emoji() = roundTrip("emoji 🎉 and CJK 文字\n".toByteArray())
    @Test fun empty() = roundTrip(ByteArray(0))

    @Test fun bomIsNotPartOfText() {
        val doc = TextCodec.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "x".toByteArray())
        assertEquals("x", doc.text)
        assertEquals(TextCodec.Bom.Utf8, doc.bom)
    }

    @Test fun detectsDominantLineSeparator() {
        assertEquals("\r\n", TextCodec.decode("a\r\nb\r\nc\n".toByteArray()).lineSeparator)
        assertEquals("\n", TextCodec.decode("a\nb".toByteArray()).lineSeparator)
        assertEquals("\r", TextCodec.decode("a\rb\rc".toByteArray()).lineSeparator)
        assertEquals("\n", TextCodec.decode("single line".toByteArray()).lineSeparator)
    }

    @Test fun editsKeepEncodingAndBom() {
        val original = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "old\r\n".toByteArray(Charsets.UTF_16LE)
        val doc = TextCodec.decode(original)
        val saved = TextCodec.encode(doc, "new\r\n")
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "new\r\n".toByteArray(Charsets.UTF_16LE), saved)
    }

    @Test fun invalidUtf8IsViewableButNotEditable() {
        val latin1 = "café crème".toByteArray(Charsets.ISO_8859_1)
        val doc = TextCodec.decode(latin1)
        assertFalse(doc.editable)
        assertEquals("café crème", doc.text)
    }
}
