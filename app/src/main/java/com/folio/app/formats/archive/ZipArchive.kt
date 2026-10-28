package com.folio.app.formats.archive

import com.folio.app.core.files.ZipSafety
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** A file inside an archive. [safe] is false when its path could escape the extraction folder (zip slip). */
class ArchiveItem(val entryName: String, val displayName: String, val size: Long, val time: Long, val safe: Boolean, val encrypted: Boolean)

class ArchiveFolder(val name: String) {
    val folders = sortedMapOf<String, ArchiveFolder>(String.CASE_INSENSITIVE_ORDER)
    val files = ArrayList<ArchiveItem>()
}

class PasswordProtectedException : IOException("Password-protected")

/**
 * A ZIP opened for browsing (plan M13). Reads the central directory only, so listing is instant even for huge or
 * hostile archives; entries are extracted one at a time through [ZipSafety].
 */
class ZipArchive(file: File) : Closeable {
    private val charset: Charset
    private val zip: ZipFile

    init {
        // Names are UTF-8 when the archive says so; older tools wrote the DOS code page.
        // java.util.zip refuses a whole archive that has any encrypted entry.
        fun open(cs: Charset) = try {
            ZipFile(file, cs) to cs
        } catch (e: ZipException) {
            if (e.message.orEmpty().contains("encrypted", ignoreCase = true)) throw PasswordProtectedException()
            throw e
        }
        val (opened, cs) = try {
            open(Charsets.UTF_8)
        } catch (e: PasswordProtectedException) {
            throw e
        } catch (e: Exception) {
            if (e !is ZipException && e !is IllegalArgumentException) throw e
            runCatching { open(CP437) }.getOrElse { throw if (it is PasswordProtectedException) it else e }
        }
        zip = opened
        charset = cs
    }

    private val encrypted: Set<String> = encryptedNames(file, charset)

    val root = ArchiveFolder("")
    var fileCount = 0
        private set
    var totalSize = 0L
        private set
    /** More than [ZipSafety.MAX_ENTRIES] entries; only the first ones are listed. */
    var truncated = false
        private set

    init {
        val entries = zip.entries()
        var seen = 0
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (++seen > ZipSafety.MAX_ENTRIES) {
                truncated = true
                break
            }
            val name = entry.name.replace('\\', '/')
            if (name.startsWith("__MACOSX/") || name.substringAfterLast('/') == ".DS_Store") continue
            val segments = name.split('/').filter { it.isNotEmpty() && it != "." }
            if (entry.isDirectory) {
                if (segments.none { it == ".." }) folder(segments)
                continue
            }
            val safe = ZipSafety.safeTarget(PROBE_ROOT, entry.name) != null
            // ZIP's "no date" is 1980-01-01 (the DOS epoch); show no time rather than that.
            val time = entry.time.takeIf { it > DOS_EPOCH_END } ?: -1L
            val item = ArchiveItem(entry.name, if (safe) segments.last() else entry.name, entry.size, time, safe, entry.name in encrypted)
            // Unsafe paths are listed as written, at the top level, so they're visible but never extracted.
            (if (safe) folder(segments.dropLast(1)) else root).files += item
            fileCount++
            totalSize += entry.size.coerceAtLeast(0)
        }
        sortFiles(root)
    }

    private fun folder(segments: List<String>): ArchiveFolder = segments.fold(root) { f, s -> f.folders.getOrPut(s) { ArchiveFolder(s) } }

    private fun sortFiles(f: ArchiveFolder) {
        f.files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        f.folders.values.forEach(::sortFiles)
    }

    /**
     * Extracts [item] under [root] (reusing an earlier extraction of the same size) and returns the file.
     * Throws [ZipSafety.UnsafeArchiveException] for zip slip or a compression bomb, [PasswordProtectedException] for
     * password-protected entries (when the platform let the archive open at all).
     */
    fun extract(item: ArchiveItem, root: File): File {
        if (item.encrypted) throw PasswordProtectedException()
        val target = ZipSafety.safeTarget(root, item.entryName) ?: throw ZipSafety.UnsafeArchiveException("Unsafe path")
        val entry = zip.getEntry(item.entryName) ?: throw IOException("Missing entry")
        if (target.isFile && target.length() == entry.size) return target
        target.parentFile!!.mkdirs()
        val part = File(target.path + ".part")
        val budget = ZipSafety.Budget().apply { startEntry(entry.compressedSize) }
        try {
            zip.getInputStream(entry).use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        budget.written(n.toLong())
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Couldn't save the extracted file")
            return target
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    override fun close() = zip.close()

    private companion object {
        val CP437: Charset = Charset.forName("IBM437")
        const val DOS_EPOCH_END = 315_619_200_000L // 1980-01-02 UTC
        /** Only used to ask [ZipSafety] whether a name stays inside a folder; nothing is written there. */
        val PROBE_ROOT = File("/folio-zip-probe")
    }
}

/**
 * Names of encrypted entries (general-purpose flag bit 0, or AES method 99), read straight from the central
 * directory because `java.util.zip` doesn't expose the flag. Empty if the directory can't be read (e.g. ZIP64).
 */
internal fun encryptedNames(file: File, charset: Charset): Set<String> = runCatching {
    RandomAccessFile(file, "r").use { raf ->
        val len = raf.length()
        val tailSize = minOf(len, 65_557L).toInt() // EOCD (22 bytes) + max comment (65,535)
        val tail = ByteArray(tailSize).also { raf.seek(len - tailSize); raf.readFully(it) }
        val eocd = (tailSize - 22 downTo 0).firstOrNull { i -> le32(tail, i) == 0x06054b50L } ?: return emptySet()
        val count = le16(tail, eocd + 10)
        val cdSize = le32(tail, eocd + 12)
        val cdOffset = le32(tail, eocd + 16)
        if (cdOffset == 0xFFFFFFFFL || cdSize > 64L * 1024 * 1024 || cdOffset + cdSize > len) return emptySet()
        val cd = ByteArray(cdSize.toInt()).also { raf.seek(cdOffset); raf.readFully(it) }
        buildSet {
            var p = 0
            repeat(count) {
                if (p + 46 > cd.size || le32(cd, p) != 0x02014b50L) return@buildSet
                val flags = le16(cd, p + 8)
                val method = le16(cd, p + 10)
                val nameLen = le16(cd, p + 28)
                val extraLen = le16(cd, p + 30)
                val commentLen = le16(cd, p + 32)
                val nameCs = if (flags and 0x800 != 0) Charsets.UTF_8 else charset
                if (flags and 1 != 0 || method == 99) add(String(cd, p + 46, nameLen, nameCs))
                p += 46 + nameLen + extraLen + commentLen
            }
        }
    }
}.getOrDefault(emptySet())

private fun le16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
private fun le32(b: ByteArray, i: Int) = (le16(b, i).toLong()) or (le16(b, i + 2).toLong() shl 16)
