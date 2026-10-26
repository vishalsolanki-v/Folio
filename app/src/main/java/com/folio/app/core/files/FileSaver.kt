package com.folio.app.core.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Where a save goes. The app uses [ContentUriTarget]; tests use fakes that fail on purpose. */
interface SaveTarget {
    fun openRead(): InputStream
    /** Opens for writing and truncates (ContentResolver mode "wt"). */
    fun openWriteTruncate(): OutputStream
}

sealed interface SaveResult {
    data object Saved : SaveResult

    /**
     * @param originalIntact the file holds exactly what it held before the save
     * @param backup copy of the original, kept only when it could not be put back
     */
    data class Failed(val originalIntact: Boolean, val backup: File?, val cause: Throwable) : SaveResult
}

/**
 * Overwrites a file without ever leaving it half-written (plan §4.6):
 * back up the original, write with truncate, read back and compare, restore the backup on any failure.
 * Never falls back to mode "w", which doesn't truncate and can leave stale bytes at the end.
 */
object FileSaver {
    const val MAX_BACKUP_BYTES = 50L * 1024 * 1024

    fun overwrite(target: SaveTarget, content: ByteArray, workDir: File): SaveResult {
        workDir.mkdirs()
        val backup = File(workDir, "${UUID.randomUUID()}.bak")
        try {
            target.openRead().use { input -> backup.outputStream().use { copyLimited(input, it, MAX_BACKUP_BYTES) } }
        } catch (e: Exception) {
            backup.delete()
            return SaveResult.Failed(originalIntact = true, backup = null, cause = e)
        }

        var truncated = false
        try {
            target.openWriteTruncate().use { out ->
                truncated = true
                out.write(content)
                out.flush()
            }
            if (!target.openRead().use { sameContent(it, content) }) throw IOException("File content differs after saving")
            backup.delete()
            return SaveResult.Saved
        } catch (e: Exception) {
            if (!truncated) {
                backup.delete()
                return SaveResult.Failed(originalIntact = true, backup = null, cause = e)
            }
            val restored = restore(target, backup)
            if (restored) backup.delete()
            return SaveResult.Failed(originalIntact = restored, backup = backup.takeUnless { restored }, cause = e)
        }
    }

    private fun restore(target: SaveTarget, backup: File): Boolean = try {
        val original = backup.readBytes()
        target.openWriteTruncate().use { it.write(original); it.flush() }
        target.openRead().use { sameContent(it, original) }
    } catch (_: Exception) {
        false
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return
            total += n
            if (total > limit) throw IOException("File is too large to back up safely before saving")
            output.write(buffer, 0, n)
        }
    }

    private fun sameContent(input: InputStream, expected: ByteArray): Boolean {
        val buffer = ByteArray(64 * 1024)
        var offset = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return offset == expected.size
            if (offset + n > expected.size) return false
            for (i in 0 until n) if (buffer[i] != expected[offset + i]) return false
            offset += n
        }
    }
}
