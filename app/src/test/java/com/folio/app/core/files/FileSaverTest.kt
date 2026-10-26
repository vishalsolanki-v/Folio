package com.folio.app.core.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FileSaverTest {
    @get:Rule val tmp = TemporaryFolder()

    /** In-memory file whose writes can fail after N bytes, refuse to open, or silently drop bytes. */
    private class FakeTarget(
        var data: ByteArray,
        val failAfterBytes: Int = Int.MAX_VALUE,
        val refuseOpenWrite: Boolean = false,
        val dropLastByte: Boolean = false,
        val failRestore: Boolean = false,
    ) : SaveTarget {
        var writes = 0

        override fun openRead(): InputStream = data.inputStream()

        override fun openWriteTruncate(): OutputStream {
            writes++
            if (refuseOpenWrite) throw IllegalArgumentException("mode wt not supported")
            if (failRestore && writes > 1) throw IOException("provider died")
            data = ByteArray(0) // truncation happens on open, like a real "wt"
            return object : OutputStream() {
                val buf = ByteArrayOutputStream()
                override fun write(b: Int) {
                    if (writes == 1 && buf.size() >= failAfterBytes) throw IOException("disk full")
                    buf.write(b)
                    data = buf.toByteArray()
                }
                override fun close() {
                    if (dropLastByte && writes == 1 && data.isNotEmpty()) data = data.copyOf(data.size - 1)
                }
            }
        }
    }

    private val original = "original contents\n".toByteArray()
    private val edited = "edited contents, a bit longer\n".toByteArray()

    private fun work() = tmp.root.resolve("save")

    @Test fun savesAndCleansUp() {
        val target = FakeTarget(original.copyOf())
        assertEquals(SaveResult.Saved, FileSaver.overwrite(target, edited, work()))
        assertArrayEquals(edited, target.data)
        assertTrue(work().listFiles().isNullOrEmpty())
    }

    @Test fun shorterContentLeavesNoStaleBytes() {
        val target = FakeTarget(edited.copyOf())
        assertEquals(SaveResult.Saved, FileSaver.overwrite(target, "short".toByteArray(), work()))
        assertArrayEquals("short".toByteArray(), target.data)
    }

    @Test fun failureMidWriteRestoresOriginal() {
        val target = FakeTarget(original.copyOf(), failAfterBytes = 5)
        val result = FileSaver.overwrite(target, edited, work()) as SaveResult.Failed
        assertTrue(result.originalIntact)
        assertNull(result.backup)
        assertArrayEquals(original, target.data)
        assertTrue(work().listFiles().isNullOrEmpty())
    }

    @Test fun silentTruncationIsCaughtByReadBack() {
        val target = FakeTarget(original.copyOf(), dropLastByte = true)
        val result = FileSaver.overwrite(target, edited, work()) as SaveResult.Failed
        assertTrue(result.originalIntact)
        assertArrayEquals(original, target.data)
    }

    @Test fun unsupportedTruncateModeNeverTouchesFile() {
        val target = FakeTarget(original.copyOf(), refuseOpenWrite = true)
        val result = FileSaver.overwrite(target, edited, work()) as SaveResult.Failed
        assertTrue(result.originalIntact)
        assertArrayEquals(original, target.data)
    }

    @Test fun failedRestoreKeepsBackupOfOriginal() {
        val target = FakeTarget(original.copyOf(), failAfterBytes = 5, failRestore = true)
        val result = FileSaver.overwrite(target, edited, work()) as SaveResult.Failed
        assertFalse(result.originalIntact)
        assertNotNull(result.backup)
        assertArrayEquals(original, result.backup!!.readBytes())
    }

    @Test fun unreadableOriginalIsNotOverwritten() {
        val target = object : SaveTarget {
            var written = false
            override fun openRead(): InputStream = throw SecurityException("no read permission")
            override fun openWriteTruncate(): OutputStream { written = true; return ByteArrayOutputStream() }
        }
        val result = FileSaver.overwrite(target, edited, work()) as SaveResult.Failed
        assertTrue(result.originalIntact)
        assertFalse(target.written)
    }
}
