package com.folio.app.core.files

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Private copies of files whose access is temporary (opened from WhatsApp, Gmail...), so Recents can reopen them
 * (plan §4.7). Layout: `<root>/<sha1(key)>/<name>`. A copy's folder mtime is its last use, for LRU eviction.
 */
class LocalCopies(private val root: File) {
    companion object {
        const val MAX_FILE_BYTES = 25L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 300L * 1024 * 1024

        fun keyHash(key: String): String =
            MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

        /** A display name that is safe as a single file name. */
        fun safeName(name: String): String =
            name.substringAfterLast('/').substringAfterLast('\\')
                .filterNot { it.isISOControl() || it in "<>:\"|?*" }
                .trim().trimStart('.')
                .take(120)
                .ifEmpty { "file" }
    }

    /**
     * Copies [input] as the copy for [key]. Returns null (and keeps nothing) if it is bigger than [maxBytes].
     * Writes to a temp file first so a half-copied file never looks valid.
     */
    fun store(key: String, name: String, input: InputStream, maxBytes: Long = MAX_FILE_BYTES): File? {
        val dir = File(root, keyHash(key)).apply { mkdirs() }
        val temp = File(dir, ".partial")
        try {
            temp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw IOException("too large to keep")
                    out.write(buffer, 0, n)
                }
            }
            dir.listFiles()?.filter { it != temp }?.forEach { it.delete() }
            val file = File(dir, safeName(name))
            if (!temp.renameTo(file)) throw IOException("rename failed")
            dir.setLastModified(System.currentTimeMillis())
            return file
        } catch (_: IOException) {
            dir.deleteRecursively()
            return null
        }
    }

    fun find(key: String): File? =
        File(root, keyHash(key)).listFiles()?.firstOrNull { it.isFile && it.name != ".partial" }

    /** Marks a copy as just used so eviction keeps it longer. */
    fun touch(key: String) {
        File(root, keyHash(key)).setLastModified(System.currentTimeMillis())
    }

    fun delete(key: String) {
        File(root, keyHash(key)).deleteRecursively()
    }

    /** Deletes least-recently-used copies until the total is at most [maxTotalBytes]. Keys in [keep] (pinned) stay. */
    fun evict(keep: Set<String> = emptySet(), maxTotalBytes: Long = MAX_TOTAL_BYTES) {
        val kept = keep.map(::keyHash).toSet()
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        var total = dirs.sumOf(::sizeOf)
        for (dir in dirs.sortedBy { it.lastModified() }) {
            if (total <= maxTotalBytes) break
            if (dir.name in kept) continue
            total -= sizeOf(dir)
            dir.deleteRecursively()
        }
    }

    fun clear() {
        root.deleteRecursively()
    }

    private fun sizeOf(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
