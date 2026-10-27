package com.folio.app.core.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DraftsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun saveLoadReplaceDelete() {
        val drafts = Drafts(tmp.root.resolve("drafts"))
        assertNull(drafts.load("content://a"))
        drafts.save("content://a", "first")
        drafts.save("content://a", "second\r\nline")
        assertEquals("second\r\nline", drafts.load("content://a"))
        drafts.delete("content://a")
        assertNull(drafts.load("content://a"))
    }

    @Test fun moveFollowsSaveCopy() {
        val drafts = Drafts(tmp.root)
        drafts.save("old", "text")
        drafts.move("old", "new")
        assertNull(drafts.load("old"))
        assertEquals("text", drafts.load("new"))
    }
}
