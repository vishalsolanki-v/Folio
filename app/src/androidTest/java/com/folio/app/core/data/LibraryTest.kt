package com.folio.app.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.core.files.LocalCopies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: FolioDatabase
    private lateinit var copiesDir: File
    private lateinit var copies: LocalCopies
    private val released = mutableListOf<String>()
    private var now = 1_000L

    private fun library(budget: Int = 100, maxRows: Int = 500) =
        Library(db.recentDao(), db.folderDao(), copies, { released += it }, budget, maxRows, clock = { now++ })

    private fun recent(uri: String, persisted: Boolean = false, copy: String? = null) =
        RecentFile(uri, "$uri.pdf", "application/pdf", "PDF", 10, 0, persisted = persisted, localCopyPath = copy)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FolioDatabase::class.java).build()
        copiesDir = File(context.cacheDir, "copies-test").apply { deleteRecursively() }
        copies = LocalCopies(copiesDir)
    }

    @After fun tearDown() {
        db.close()
        copiesDir.deleteRecursively()
    }

    @Test fun newestFirstAndPinnedSurvivesReopen() = runTest {
        val lib = library()
        lib.recordOpen(recent("a"))
        lib.recordOpen(recent("b"))
        lib.setPinned("a", true)
        lib.recordOpen(recent("a"))
        val rows = lib.observeRecents().first()
        assertEquals(listOf("a", "b"), rows.map { it.uri })
        assertTrue(rows.first().pinned)
    }

    @Test fun trimsOldestUnpinnedAndCleansUp() = runTest {
        val lib = library(maxRows = 3)
        val copyPath = copies.store("old", "old.pdf", "x".byteInputStream())!!.path
        lib.recordOpen(recent("old", persisted = true, copy = copyPath))
        lib.recordOpen(recent("pinned")); lib.setPinned("pinned", true)
        lib.recordOpen(recent("b"))
        lib.recordOpen(recent("c"))
        val uris = lib.observeRecents().first().map { it.uri }.toSet()
        assertEquals(setOf("pinned", "b", "c"), uris)
        assertEquals(listOf("old"), released)
        assertNull(copies.find("old"))
    }

    @Test fun pinnedRowsAreNeverTrimmed() = runTest {
        val lib = library(maxRows = 2)
        lib.recordOpen(recent("p1")); lib.setPinned("p1", true)
        lib.recordOpen(recent("p2")); lib.setPinned("p2", true)
        lib.recordOpen(recent("x"))
        assertEquals(setOf("p1", "p2"), lib.observeRecents().first().map { it.uri }.toSet())
    }

    @Test fun persistedBudgetReleasesOldestAndKeepsRowsWithCopies() = runTest {
        val lib = library(budget = 2)
        val copyPath = copies.store("withCopy", "f.pdf", "x".byteInputStream())!!.path
        lib.recordOpen(recent("withCopy", persisted = true, copy = copyPath))
        lib.recordOpen(recent("noCopy", persisted = true))
        lib.recordOpen(recent("newest", persisted = true)) // 3 grants > budget 2
        lib.recordOpen(recent("newest2", persisted = true)) // 4 grants > budget 2
        val rows = lib.observeRecents().first().associateBy { it.uri }
        assertEquals(listOf("withCopy", "noCopy"), released)
        assertFalse(rows.getValue("withCopy").persisted) // kept, now relies on its copy
        assertNull(rows["noCopy"]) // nothing left to open it with
        assertTrue(rows.getValue("newest2").persisted)
    }

    @Test fun foldersCountTowardBudgetAndReleaseOnRemove() = runTest {
        val lib = library(budget = 1)
        lib.addFolder(PinnedFolder("content://tree/docs", "Docs", 1))
        lib.recordOpen(recent("file", persisted = true))
        assertEquals(listOf("file"), released)
        lib.removeFolder("content://tree/docs")
        assertEquals(listOf("file", "content://tree/docs"), released)
        assertTrue(lib.observeFolders().first().isEmpty())
    }

    @Test fun clearRecentsReleasesGrantsAndCopiesButKeepsFolders() = runTest {
        val lib = library()
        lib.addFolder(PinnedFolder("content://tree/docs", "Docs", 1))
        lib.recordOpen(recent("p", persisted = true))
        copies.store("t", "t.pdf", "x".byteInputStream())
        lib.recordOpen(recent("t"))
        lib.clearRecents()
        assertTrue(lib.observeRecents().first().isEmpty())
        assertEquals(listOf("p"), released)
        assertNull(copies.find("t"))
        assertNotNull(lib.observeFolders().first().singleOrNull())
    }
}
