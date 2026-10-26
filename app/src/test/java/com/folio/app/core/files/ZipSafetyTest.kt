package com.folio.app.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipSafetyTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun normalPathsStayInsideRoot() {
        val root = tmp.newFolder("out")
        assertEquals(root.resolve("docs/2024/report.pdf").canonicalFile, ZipSafety.safeTarget(root, "docs/2024/report.pdf"))
        assertEquals(root.resolve("a b/c.txt").canonicalFile, ZipSafety.safeTarget(root, "a b\\c.txt"))
    }

    @Test fun escapingPathsAreRejected() {
        val root = tmp.newFolder("out")
        listOf("../evil", "a/../../b", "/abs/path", "\\abs", "C:/windows/x", "a/./../../x", "..", "", "dir/").forEach {
            assertNull("should reject '$it'", ZipSafety.safeTarget(root, it))
        }
    }

    @Test fun innocentDotsAreFine() {
        val root = tmp.newFolder("out")
        assertEquals(root.resolve("a..b/..c").canonicalFile, ZipSafety.safeTarget(root, "a..b/..c"))
    }

    @Test fun budgetStopsHugeTotals() {
        val budget = ZipSafety.Budget(maxTotalBytes = 1000)
        budget.startEntry(600); repeat(6) { budget.written(100) }
        budget.startEntry(500)
        budget.written(300)
        assertThrows(ZipSafety.UnsafeArchiveException::class.java) { budget.written(200) }
    }

    @Test fun budgetStopsCompressionBombsWhileWriting() {
        val mb = 1024L * 1024
        val budget = ZipSafety.Budget()
        budget.startEntry(compressedSize = 1024)
        repeat(50) { budget.written(mb) } // 50 MB at a huge ratio is still allowed
        assertThrows(ZipSafety.UnsafeArchiveException::class.java) { budget.written(mb) }

        val honest = ZipSafety.Budget()
        honest.startEntry(compressedSize = 30 * mb)
        repeat(60) { honest.written(mb) } // big entry with a normal ratio passes
        honest.startEntry(compressedSize = -1)
        repeat(60) { honest.written(mb) } // unknown compressed size: only the total cap applies
    }
}
