package com.folio.app.core.files

import java.io.File

/** Deletes extracted archive entries and random-access copies older than a day (plan M13). */
object CacheCleaner {
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    fun clean(cacheDir: File, now: Long = System.currentTimeMillis()) {
        for (dir in listOf("archive", "open")) {
            File(cacheDir, dir).walkBottomUp().forEach { f ->
                when {
                    f.isFile && now - f.lastModified() > MAX_AGE_MS -> f.delete()
                    f.isDirectory && f.name != dir && f.list().isNullOrEmpty() -> f.delete()
                }
            }
        }
    }
}
