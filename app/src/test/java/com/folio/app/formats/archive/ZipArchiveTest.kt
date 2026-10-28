package com.folio.app.formats.archive

import com.folio.app.core.files.ZipSafety
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("folio", ".zip").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return file
    }

    private val out: File = Files.createTempDirectory("folio-extract").toFile().apply { deleteOnExit() }

    @Test fun buildsAFolderTree() {
        val archive = ZipArchive(zip(
            "docs/2024/report.pdf" to "pdf".toByteArray(),
            "docs/notes.txt" to "hello".toByteArray(),
            "readme.md" to "# hi".toByteArray(),
            "empty/" to ByteArray(0),
            "__MACOSX/docs/._notes.txt" to "junk".toByteArray(),
        ))
        assertEquals(3, archive.fileCount)
        assertEquals(12L, archive.totalSize)
        assertEquals(listOf("docs", "empty"), archive.root.folders.keys.toList())
        assertEquals(listOf("readme.md"), archive.root.files.map { it.displayName })
        val docs = archive.root.folders.getValue("docs")
        assertEquals(listOf("notes.txt"), docs.files.map { it.displayName })
        assertEquals("report.pdf", docs.folders.getValue("2024").files.single().displayName)
    }

    @Test fun extractsOneEntryAndReusesIt() {
        val archive = ZipArchive(zip("docs/notes.txt" to "hello".toByteArray()))
        val item = archive.root.folders.getValue("docs").files.single()
        val file = archive.extract(item, out)
        assertEquals("hello", file.readText())
        assertTrue(file.path.startsWith(out.canonicalPath))
        assertEquals(file, archive.extract(item, out))
    }

    @Test fun zipSlipEntriesAreListedButNeverExtracted() {
        val archive = ZipArchive(zip("../evil.txt" to "x".toByteArray(), "ok.txt" to "y".toByteArray()))
        val evil = archive.root.files.first { it.entryName == "../evil.txt" }
        assertFalse(evil.safe)
        assertEquals("../evil.txt", evil.displayName)
        try {
            archive.extract(evil, out)
            fail("extracted a zip-slip entry")
        } catch (_: ZipSafety.UnsafeArchiveException) {
        }
        assertFalse(File(out.parentFile, "evil.txt").exists())
    }

    @Test fun compressionBombStopsAndLeavesNothing() {
        val archive = ZipArchive(zip("bomb.bin" to ByteArray(60 * 1024 * 1024)))
        try {
            archive.extract(archive.root.files.single(), out)
            fail("bomb extracted")
        } catch (_: ZipSafety.UnsafeArchiveException) {
        }
        assertTrue(out.listFiles().orEmpty().isEmpty())
    }

    @Test fun encryptedEntriesAreDetected() {
        val file = zip("secret.txt" to "x".toByteArray(), "open.txt" to "y".toByteArray())
        // Set the "encrypted" flag on secret.txt's central-directory record (java.util.zip can't write encrypted zips).
        val bytes = file.readBytes()
        var p = bytes.size - 22
        while (p >= 0 && !(bytes[p] == 0x50.toByte() && bytes[p + 1] == 0x4b.toByte() && bytes[p + 2] == 0x01.toByte() && bytes[p + 3] == 0x02.toByte() &&
                String(bytes, p + 46, "secret.txt".length) == "secret.txt")) p--
        bytes[p + 8] = (bytes[p + 8].toInt() or 1).toByte()
        file.writeBytes(bytes)

        assertEquals(setOf("secret.txt"), encryptedNames(file, Charsets.UTF_8))
        try {
            ZipArchive(file)
            fail("opened a password-protected zip as normal")
        } catch (_: PasswordProtectedException) {
        }
    }
}
