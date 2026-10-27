package com.folio.app.feature.viewer

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.folio.app.AppContainer
import com.folio.app.core.data.RecentFile
import com.folio.app.core.files.LocalCopies
import com.folio.app.core.files.UriAccess
import com.folio.app.core.files.resolveOrCopy
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.ViewerState
import com.folio.app.navigation.Viewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Ready(val file: OpenedFile, val viewer: FormatViewer) : ViewerUiState
    /** Nothing readable: the original is gone and there's no private copy, or the sender's URI can't be read. */
    data class Unavailable(val message: String) : ViewerUiState
}

/**
 * Resolves the file, picks its viewer and records it in Recents (plan §4.7). When the original can't be read any
 * more, shows the private copy instead, still recorded under the original URI so Recents keeps one row.
 */
class ViewerViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    companion object {
        const val RELOAD_KEY = "reload"
    }

    val route = handle.toRoute<Viewer>()
    val viewerState = ViewerState()

    /** Bumped by the editor after a save (via this entry's saved state) so the content reloads. */
    val reloadToken: StateFlow<Long> = handle.getStateFlow(RELOAD_KEY, 0L)

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state

    val pinned: StateFlow<Boolean> = container.library.observeRecents()
        .map { rows -> rows.any { it.uri == route.uri && it.pinned } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether "Open as text" can be offered (only once a text viewer exists). */
    val canOpenAsText: Boolean get() = !route.asText && container.viewerRegistry.hasViewerFor(FileFamily.TEXT)

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        viewerState.paper = container.settings.settings.first().paper
        val uri = Uri.parse(route.uri)
        val found = container.fileResolver.resolveOrCopy(route.uri, container.localCopies, route.mimeHint, route.source)
        if (found == null) {
            _state.value = ViewerUiState.Unavailable(
                if (uri.scheme == ContentResolver.SCHEME_FILE && route.source != null) {
                    "The other app shared this file in an old way Folio can't read. Try Share instead."
                } else {
                    "This file is no longer available."
                },
            )
            return
        }
        val (resolved, fromCopy) = found
        val file = if (route.asText) resolved.copy(family = FileFamily.TEXT) else resolved
        _state.value = ViewerUiState.Ready(file, container.viewerRegistry.forFile(file))
        if (route.record) record(file, fromCopy)
    }

    fun togglePin() = viewModelScope.launch { container.library.setPinned(route.uri, !pinned.value) }

    fun removeFromRecents(done: () -> Unit) = viewModelScope.launch {
        container.library.remove(route.uri)
        done()
    }

    /** Last-modified time if the provider reports one (document URIs do), else null. */
    suspend fun lastModified(file: OpenedFile): Long? = withContext(Dispatchers.IO) {
        if (file.uri.scheme == ContentResolver.SCHEME_FILE) return@withContext file.uri.path?.let { java.io.File(it).lastModified() }
        runCatching {
            container.context.contentResolver.query(file.uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        }.getOrNull()?.takeIf { it > 0 }
    }

    private suspend fun record(file: OpenedFile, fromCopy: Boolean) = withContext(Dispatchers.IO) {
        val context = container.context
        val uri = Uri.parse(route.uri)
        val persisted = !fromCopy && UriAccess.isPersisted(context, uri)
        val copyPath = when {
            fromCopy -> container.localCopies.find(route.uri)?.path
            persisted || UriAccess.isInsideTree(uri) || uri.scheme != ContentResolver.SCHEME_CONTENT -> null
            !container.settings.settings.first().keepCopies -> null
            file.sizeBytes !in 0..LocalCopies.MAX_FILE_BYTES -> null
            else -> runCatching {
                context.contentResolver.openInputStream(uri)?.use { container.localCopies.store(route.uri, file.name, it) }?.path
            }.getOrNull()
        }
        container.library.recordOpen(
            RecentFile(
                uri = route.uri,
                name = file.name,
                mime = file.mime,
                family = file.family.name,
                sizeBytes = file.sizeBytes,
                lastOpenedAt = 0, // set by Library
                sourceApp = file.sourceApp,
                localCopyPath = copyPath,
                persisted = persisted,
            ),
        )
    }
}
