package com.folio.app.core.files

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalCopiesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun storesAndFindsCopy() {
        val copies = LocalCopies(tmp.root)
        val file = copies.store("content://whatsapp/1", "Invoice.pdf", "pdf bytes".byteInputStream())!!
        assertEquals("Invoice.pdf", file.name)
        assertEquals(file, copies.find("content://whatsapp/1"))
        assertArrayEquals("pdf bytes".toByteArray(), file.readBytes())
    }

    @Test fun replacesOldCopyForSameKey() {
        val copies = LocalCopies(tmp.root)
        copies.store("k", "a.txt", "one".byteInputStream())
        copies.store("k", "b.txt", "two".byteInputStream())
        assertEquals("b.txt", copies.find("k")!!.name)
        assertEquals(1, tmp.root.resolve(LocalCopies.keyHash("k")).listFiles()!!.size)
    }

    @Test fun tooLargeIsNotKept() {
        val copies = LocalCopies(tmp.root)
        assertNull(copies.store("big", "big.bin", ByteArray(100).inputStream(), maxBytes = 50))
        assertNull(copies.find("big"))
    }

    @Test fun hostileNamesAreSanitised() {
        assertEquals("passwd", LocalCopies.safeName("../../etc/passwd"))
        assertEquals("evil.txt", LocalCopies.safeName("..\\evil.txt"))
        assertEquals("file", LocalCopies.safeName(".."))
        assertEquals("ab.pdf", LocalCopies.safeName("a\u0000b.pdf"))
    }

    @Test fun evictsLeastRecentlyUsedButKeepsPinned() {
        val copies = LocalCopies(tmp.root)
        val now = System.currentTimeMillis()
        listOf("old-pinned", "old", "mid", "new").forEachIndexed { i, key ->
            copies.store(key, "$key.bin", ByteArray(100).inputStream())
            tmp.root.resolve(LocalCopies.keyHash(key)).setLastModified(now - (10 - i) * 60_000L)
        }
        copies.evict(keep = setOf("old-pinned"), maxTotalBytes = 250)
        assertNotNull(copies.find("old-pinned"))
        assertNull(copies.find("old"))
        assertNull(copies.find("mid"))
        assertNotNull(copies.find("new"))
    }
}
