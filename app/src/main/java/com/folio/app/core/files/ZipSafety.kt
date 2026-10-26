package com.folio.app.core.files

import java.io.File
import java.io.IOException

/** Guards against zip slip and zip bombs when extracting entries from untrusted archives (plan §4.10). */
object ZipSafety {
    const val MAX_TOTAL_BYTES = 500L * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    private const val BOMB_MIN_BYTES = 50L * 1024 * 1024
    private const val BOMB_RATIO = 100

    class UnsafeArchiveException(message: String) : IOException(message)

    /** Where [entryName] may be extracted under [root], or null if the name is absolute, empty or escapes root. */
    fun safeTarget(root: File, entryName: String): File? {
        val name = entryName.replace('\\', '/')
        if (name.isEmpty() || name.endsWith("/") || name.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(name)) return null
        if (name.split('/').any { it == ".." }) return null
        val base = root.canonicalFile
        val target = File(base, name).canonicalFile
        return target.takeIf { it.path.startsWith(base.path + File.separator) }
    }

    /** Tracks bytes written across one extraction so a bomb is stopped while it is being written. */
    class Budget(private val maxTotalBytes: Long = MAX_TOTAL_BYTES) {
        private var total = 0L
        private var entryBytes = 0L
        private var entryCompressed = -1L

        /** Call before writing each entry; [compressedSize] is -1 when the archive doesn't say. */
        fun startEntry(compressedSize: Long) {
            entryBytes = 0
            entryCompressed = compressedSize
        }

        /** Call after writing each chunk of [bytes]. */
        fun written(bytes: Long) {
            entryBytes += bytes
            total += bytes
            if (total > maxTotalBytes) throw UnsafeArchiveException("Archive expands beyond ${maxTotalBytes / (1024 * 1024)} MB")
            if (entryCompressed > 0 && entryBytes > BOMB_MIN_BYTES && entryBytes / entryCompressed > BOMB_RATIO) {
                throw UnsafeArchiveException("Archive entry looks like a compression bomb")
            }
        }
    }
}
