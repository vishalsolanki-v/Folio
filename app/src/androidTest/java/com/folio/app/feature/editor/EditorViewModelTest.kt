package com.folio.app.feature.editor

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.FolioApp
import com.folio.app.core.files.Drafts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Editor edge cases that used to lose or resurrect text (audit FEAT-1, FEAT-3, FEAT-4). */
@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container = (context.applicationContext as FolioApp).container
    private val drafts = Drafts(File(context.filesDir, "drafts"))

    private fun file(name: String, body: String): Uri {
        val f = File(context.cacheDir, "editor-test/$name").apply { parentFile!!.mkdirs(); writeText(body) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    private fun readText(uri: Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }

    private fun editorFor(uri: Uri): EditorViewModel {
        lateinit var vm: EditorViewModel
        instrumentation.runOnMainSync { vm = EditorViewModel(container, SavedStateHandle(mapOf("uri" to uri.toString()))) }
        runBlocking { withTimeout(10_000) { vm.state.first { it is EditorUiState.Ready } } }
        return vm
    }

    private fun ready(vm: EditorViewModel) = vm.state.value as EditorUiState.Ready

    private fun awaitSaved(vm: EditorViewModel, count: Int) =
        runBlocking { withTimeout(10_000) { vm.state.first { it is EditorUiState.Ready && it.savedCount >= count } } }

    @Test fun discardIsFinalEvenIfTheClosingScreenWritesADraft() {
        val uri = file("discard.txt", "original")
        drafts.delete(uri.toString())
        val vm = editorFor(uri)
        instrumentation.runOnMainSync {
            vm.markDirty()
            vm.writeDraft("typed")
            vm.discard()
            vm.writeDraft("typed") // ON_STOP of the closing editor
        }
        Thread.sleep(500) // draft I/O runs in order on its own thread
        assertNull(drafts.load(uri.toString()))
        assertTrue(!ready(vm).dirty)
    }

    @Test fun saveCopyWritesTheTextFromWhenSaveWasTapped() {
        val uri = file("copy.txt", "original")
        val target = file("copy-target.txt", "")
        val vm = editorFor(uri)
        instrumentation.runOnMainSync {
            vm.markDirty()
            vm.save("edited") // read-only source: asks for "Save as" and keeps this text
            vm.saveCopy(target, fallbackText = "") // the picker came back before the editor view existed
        }
        awaitSaved(vm, 1)
        assertEquals("edited", readText(target))
    }

    @Test fun saveCopyAfterProcessDeathUsesTheDraft() {
        val uri = file("revived.txt", "original")
        val target = file("revived-target.txt", "")
        drafts.save(uri.toString(), "from the draft") // written by ON_STOP as the picker opened
        val vm = editorFor(uri) // a brand-new ViewModel, as after process death
        instrumentation.runOnMainSync { vm.saveCopy(target, fallbackText = "original") }
        awaitSaved(vm, 1)
        assertEquals("from the draft", readText(target))
        assertNull(ready(vm).earlierDraft)
    }

    @Test fun typingWhileSavingKeepsTheEditorDirty() {
        val uri = file("busy.txt", "original")
        val target = file("busy-target.txt", "")
        val vm = editorFor(uri)
        instrumentation.runOnMainSync {
            vm.markDirty()
            vm.save("first")
            vm.saveCopy(target, fallbackText = "")
            vm.markDirty() // a keystroke lands while the copy is being written
        }
        awaitSaved(vm, 1)
        assertTrue(ready(vm).dirty)
    }
}
