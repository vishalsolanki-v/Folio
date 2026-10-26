package com.folio.app.core.files

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class MimeSnifferTest {
    private fun detect(bytes: ByteArray, name: String, intentMime: String? = null) =
        MimeSniffer.detect(bytes.copyOf(minOf(bytes.size, MimeSniffer.HEAD_SIZE)), name, intentMime) { bytes.inputStream() }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }
    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)
    private fun ftyp(brand: String) = bytes(0, 0, 0, 0x18) + ascii("ftyp$brand") + ByteArray(16)

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, body) -> z.putNextEntry(ZipEntry(n)); z.write(body.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun magicNumbers() {
        assertEquals("application/pdf", detect(ascii("%PDF-1.7\n..."), "x"))
        assertEquals("image/png", detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0), "x"))
        assertEquals("image/jpeg", detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0), "x"))
        assertEquals("image/gif", detect(ascii("GIF89a....."), "x"))
        assertEquals("image/webp", detect(ascii("RIFF\u0000\u0000\u0000\u0000WEBPVP8 "), "x"))
        assertEquals("audio/wav", detect(ascii("RIFF\u0000\u0000\u0000\u0000WAVEfmt "), "x"))
        assertEquals("image/heic", detect(ftyp("heic"), "x"))
        assertEquals("image/heic", detect(ftyp("mif1"), "x"))
        assertEquals("image/avif", detect(ftyp("avif"), "x"))
        assertEquals("video/mp4", detect(ftyp("isom"), "x"))
        assertEquals("audio/mp4", detect(ftyp("M4A "), "x"))
        assertEquals("video/quicktime", detect(ftyp("qt  "), "x"))
        assertEquals("video/3gpp", detect(ftyp("3gp5"), "x"))
        assertEquals("audio/mpeg", detect(ascii("ID3\u0004\u0000....."), "x"))
        assertEquals("audio/flac", detect(ascii("fLaC\u0000..."), "x"))
        assertEquals("audio/ogg", detect(ascii("OggS\u0000..."), "x"))
        assertEquals("video/webm", detect(bytes(0x1A, 0x45, 0xDF, 0xA3) + ascii("\u0000\u0000B\u0082webm"), "x"))
        assertEquals("video/x-matroska", detect(bytes(0x1A, 0x45, 0xDF, 0xA3) + ascii("\u0000\u0000B\u0082matroska"), "x"))
        assertEquals("application/vnd.rar", detect(ascii("Rar!\u001A\u0007\u0001\u0000"), "x"))
        assertEquals("application/x-7z-compressed", detect(bytes(0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C), "x"))
        assertEquals("application/gzip", detect(bytes(0x1F, 0x8B, 8, 0), "x"))
    }

    @Test fun bmpNeedsValidDibHeader() {
        val bmp = ascii("BM") + ByteArray(12) + bytes(40, 0, 0, 0) + ByteArray(20)
        assertEquals("image/bmp", detect(bmp, "x"))
        assertEquals("text/plain", detect(ascii("BM25 is a ranking function used by search engines.\n"), "noext"))
    }

    @Test fun weakSignaturesIgnoredForTextExtensions() {
        assertEquals("text/plain", detect(ascii("BM") + ByteArray(12) + bytes(40, 0, 0, 0), "notes.txt"))
        assertEquals("audio/mpeg", detect(bytes(0xFF, 0xFB, 0x90, 0x44, 0), "song"))
    }

    @Test fun ole2IsOlderOfficeOrExtensionSpecific() {
        val ole = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) + ByteArray(16)
        assertEquals("application/x-ole-storage", detect(ole, "mystery"))
        assertEquals("application/msword", detect(ole, "old.doc"))
        assertEquals("application/vnd.ms-excel", detect(ole, "old.xls"))
    }

    @Test fun zipContainersAreTellApart() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            detect(zip("[Content_Types].xml" to "x", "word/document.xml" to "x"), "whatsapp-file"),
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            detect(zip("xl/workbook.xml" to "x"), "a.zip"),
        )
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            detect(zip("ppt/presentation.xml" to "x"), "deck"),
        )
        assertEquals("application/epub+zip", detect(zip("mimetype" to "application/epub+zip", "META-INF/container.xml" to "x"), "b"))
        assertEquals("application/vnd.android.package-archive", detect(zip("AndroidManifest.xml" to "x", "classes.dex" to "x"), "c"))
        assertEquals("application/vnd.oasis.opendocument.text", detect(zip("mimetype" to "application/vnd.oasis.opendocument.text", "content.xml" to "x"), "d"))
        assertEquals("application/zip", detect(zip("docs/readme.txt" to "hi"), "archive.zip"))
    }

    @Test fun docxRenamedToZipIsStillDocx() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            detect(zip("word/document.xml" to "x"), "report.zip"),
        )
    }

    @Test fun truncatedZipFallsBackToZipBasedExtension() {
        val truncated = zip("word/document.xml" to "x".repeat(5000)).copyOf(40)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", detect(truncated, "report.docx"))
        assertEquals("application/zip", detect(truncated, "unknown"))
    }

    @Test fun wrongExtensionLosesToMagic() {
        assertEquals("image/png", detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0), "photo.pdf"))
    }

    @Test fun extensionThenIntentThenTextHeuristic() {
        assertEquals("text/markdown", detect(ascii("# Title\n"), "readme.md"))
        assertEquals("application/json", detect(ascii("{\"a\": 1}"), "data.json", intentMime = "application/octet-stream"))
        assertEquals("text/csv", detect(ascii("a,b\n1,2\n"), "export", intentMime = "text/csv"))
        assertEquals("text/plain", detect("Héllo wörld, plain UTF-8 text\n".toByteArray(), "noext"))
        assertEquals("application/octet-stream", detect(Random(1).nextBytes(4096), "noext"))
        assertEquals("application/octet-stream", detect(ByteArray(0), "empty"))
    }

    @Test fun bomTextIsText() {
        assertEquals("text/plain", detect(bytes(0xFF, 0xFE) + "hi\r\n".toByteArray(Charsets.UTF_16LE), "noext"))
        assertEquals("text/plain", detect(bytes(0xEF, 0xBB, 0xBF) + ascii("hi"), "noext"))
    }

    @Test fun zipInspectionStopsOnHugeArchives() {
        val many = zip(*Array(500) { "f$it.txt" to "" })
        assertEquals("application/zip", detect(many, "many"))
    }
}
