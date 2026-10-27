package com.folio.app.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.folio.app.AppContainer
import com.folio.app.core.data.RecentFile
import com.folio.app.core.files.ContentUriTarget
import com.folio.app.core.files.Drafts
import com.folio.app.core.files.FileSaver
import com.folio.app.core.files.SaveResult
import com.folio.app.core.files.TextCodec
import com.folio.app.core.files.UriAccess
import com.folio.app.core.files.resolveOrCopy
import com.folio.app.core.model.OpenedFile
import com.folio.app.formats.text.TextLoader
import com.folio.app.formats.text.TextViewer
import com.folio.app.navigation.Editor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface EditorUiState {
    data object Loading : EditorUiState
    data class Failed(val message: String) : EditorUiState
    data class Ready(
        val file: OpenedFile,
        val doc: TextCodec.TextDoc,
        /** Draft from an earlier session that differs from the file; offered as Restore / Discard. */
        val earlierDraft: String? = null,
        val dirty: Boolean = false,
        val saving: Boolean = false,
        /** Shown in the validation sheet; Save anyway skips the check. */
        val validationError: String? = null,
        /** One-off message for the snackbar. */
        val message: String? = null,
        /** The UI should open the system "Save as" picker. */
        val askSaveCopy: Boolean = false,
        /** Increments on every successful save so the viewer behind can reload. */
        val savedCount: Int = 0,
    ) : EditorUiState
}

/** Loads, edits and saves text-like files without changing anything the user didn't touch (plan §2.4, §4.5, §4.6). */
class EditorViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<Editor>()
    private val drafts = Drafts(File(container.context.filesDir, "drafts"))
    private val saveDir = File(container.context.cacheDir, "save")

    /** Drafts and Recents follow the file: the original URI first, the new one after "Save copy". */
    private var key = route.uri

    private val _state = MutableStateFlow<EditorUiState>(EditorUiState.Loading)
    val state: StateFlow<EditorUiState> = _state

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val (file, _) = container.fileResolver.resolveOrCopy(route.uri, container.localCopies)
            ?: return fail("This file is no longer available.")
        if (file.sizeBytes > TextViewer.EDIT_LIMIT_BYTES) return fail("This file is too big to edit (over 5 MB).")
        val loaded = runCatching { TextLoader.load(container.context, file.uri, TextViewer.EDIT_LIMIT_BYTES) }.getOrNull()
            ?: return fail("Folio couldn't read this file.")
        val doc = loaded.doc
        if (loaded.truncated || doc == null) return fail("This file is too big to edit (over 5 MB).")
        if (!doc.editable) return fail("This file uses an unusual text encoding, so saving could change it.")
        val draft = withContext(Dispatchers.IO) { drafts.load(key) }?.takeIf { it != doc.text }
        _state.value = EditorUiState.Ready(file, doc, earlierDraft = draft)
    }

    private fun fail(message: String) {
        _state.value = EditorUiState.Failed(message)
    }

    private inline fun ready(block: (EditorUiState.Ready) -> EditorUiState.Ready) =
        _state.update { s -> if (s is EditorUiState.Ready) block(s) else s }

    fun markDirty() = ready { if (it.dirty) it else it.copy(dirty = true) }

    fun dismissEarlierDraft(restored: Boolean) {
        ready { it.copy(earlierDraft = null, dirty = it.dirty || restored) }
        if (!restored) viewModelScope.launch(Dispatchers.IO) { drafts.delete(key) }
    }

    fun writeDraft(text: String) = viewModelScope.launch(Dispatchers.IO) { drafts.save(key, text) }

    fun discard() = viewModelScope.launch(Dispatchers.IO) { drafts.delete(key) }

    fun messageShown() = ready { it.copy(message = null) }
    fun dismissValidation() = ready { it.copy(validationError = null) }
    fun saveCopyHandled() = ready { it.copy(askSaveCopy = false) }

    /** Saves over the file when allowed, otherwise asks for "Save copy". Validates JSON/XML unless [force]. */
    fun save(text: String, force: Boolean = false) {
        val s = _state.value as? EditorUiState.Ready ?: return
        if (s.saving) return
        if (!force) {
            Validation.check(text, s.file.extension, s.file.mime)?.let { error ->
                ready { it.copy(validationError = error) }
                return
            }
        }
        ready { it.copy(validationError = null) }
        if (!s.file.writable) {
            ready { it.copy(askSaveCopy = true) }
            return
        }
        ready { it.copy(saving = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                FileSaver.overwrite(ContentUriTarget(container.context.contentResolver, s.file.uri), TextCodec.encode(s.doc, text), saveDir)
            }
            when (result) {
                SaveResult.Saved -> {
                    withContext(Dispatchers.IO) { drafts.delete(key) }
                    ready { it.copy(saving = false, dirty = false, message = "Saved", savedCount = it.savedCount + 1) }
                }
                is SaveResult.Failed -> {
                    writeDraft(text)
                    ready { it.copy(saving = false, message = "Folio couldn't save over this file. Your changes are safe.", askSaveCopy = true) }
                }
            }
        }
    }

    /** Writes [text] to a new document the user picked, then continues editing that one. */
    fun saveCopy(target: Uri, text: String) {
        val s = _state.value as? EditorUiState.Ready ?: return
        ready { it.copy(saving = true) }
        viewModelScope.launch {
            val context = container.context
            val result = withContext(Dispatchers.IO) {
                FileSaver.overwrite(ContentUriTarget(context.contentResolver, target), TextCodec.encode(s.doc, text), saveDir)
            }
            if (result !is SaveResult.Saved) {
                ready { it.copy(saving = false, message = "Folio couldn't save the copy. Your changes are safe.") }
                return@launch
            }
            UriAccess.persist(context, target)
            val copy = container.fileResolver.resolve(target).getOrNull()
            withContext(Dispatchers.IO) { drafts.delete(key) }
            if (copy != null) {
                key = target.toString()
                container.library.recordOpen(
                    RecentFile(target.toString(), copy.name, copy.mime, copy.family.name, copy.sizeBytes, 0, persisted = UriAccess.isPersisted(context, target)),
                )
            }
            ready {
                it.copy(
                    file = copy ?: it.file,
                    saving = false,
                    dirty = false,
                    message = "Saved a copy",
                    savedCount = it.savedCount + 1,
                )
            }
        }
    }
}
