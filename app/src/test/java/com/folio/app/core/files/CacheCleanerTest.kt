package com.folio.app.core.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CacheCleanerTest {
    @Test fun removesOnlyOldCopies() {
        val cache = Files.createTempDirectory("folio-cache").toFile()
        val now = System.currentTimeMillis()
        val old = File(cache, "archive/abc/docs/old.pdf").apply { parentFile!!.mkdirs(); writeText("x"); setLastModified(now - 25 * 3_600_000L) }
        val fresh = File(cache, "open/def.xlsx").apply { parentFile!!.mkdirs(); writeText("y") }
        val other = File(cache, "save/keep.bak").apply { parentFile!!.mkdirs(); writeText("z"); setLastModified(now - 48 * 3_600_000L) }
        CacheCleaner.clean(cache, now)
        assertFalse(old.exists())
        assertFalse(File(cache, "archive/abc").exists()) // emptied folders go too
        assertTrue(File(cache, "archive").exists())
        assertTrue(fresh.exists())
        assertTrue(other.exists())
    }
}
