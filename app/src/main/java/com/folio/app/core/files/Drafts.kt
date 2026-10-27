package com.folio.app.core.files

import java.io.File

/** Unsaved editor text kept on disk so a crash or process death never loses edits (plan §2.4). */
class Drafts(private val dir: File) {
    private fun file(key: String) = File(dir, LocalCopies.keyHash(key) + ".txt")

    /** Writes via a temp file + rename so a half-written draft never replaces a good one. */
    fun save(key: String, text: String) {
        dir.mkdirs()
        val temp = File(dir, LocalCopies.keyHash(key) + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file(key))) {
            file(key).delete()
            temp.renameTo(file(key))
        }
    }

    fun load(key: String): String? = file(key).takeIf { it.isFile }?.readText()

    fun delete(key: String) {
        file(key).delete()
    }

    fun move(fromKey: String, toKey: String) {
        val from = file(fromKey)
        if (from.isFile) {
            file(toKey).delete()
            from.renameTo(file(toKey))
        }
    }
}
