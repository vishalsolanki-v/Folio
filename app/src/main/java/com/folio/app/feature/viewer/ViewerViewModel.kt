package com.folio.app.feature.viewer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.folio.app.AppContainer
import com.folio.app.core.data.RecentFile
import com.folio.app.core.files.LocalCopies
import com.folio.app.core.files.UriAccess
import com.folio.app.core.model.OpenedFile
import com.folio.app.navigation.Viewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ViewerUiState {
    data object Loading : ViewerUiState
    data class Ready(val file: OpenedFile) : ViewerUiState
    /** The original is gone and no private copy exists (e.g. an old WhatsApp file). */
    data object NoLongerAvailable : ViewerUiState
}

/**
 * Resolves the file to show and records it in Recents (plan §4.7). When the original can't be read any more,
 * shows the private copy instead, still recorded under the original URI so Recents keeps one row.
 */
class ViewerViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<Viewer>()

    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val uri = Uri.parse(route.uri)
        val direct = container.fileResolver.resolve(uri, route.mimeHint)
        val file = direct.getOrNull()
            ?: container.localCopies.find(route.uri)?.let { copy ->
                container.fileResolver.resolve(Uri.fromFile(copy)).getOrNull()?.also { container.localCopies.touch(route.uri) }
            }
        if (file == null) {
            _state.value = ViewerUiState.NoLongerAvailable
            return
        }
        _state.value = ViewerUiState.Ready(file)
        record(file, fromCopy = direct.isFailure)
    }

    private suspend fun record(file: OpenedFile, fromCopy: Boolean) = withContext(Dispatchers.IO) {
        val context = container.context
        val uri = Uri.parse(route.uri)
        val persisted = !fromCopy && UriAccess.isPersisted(context, uri)
        val copyPath = when {
            fromCopy -> container.localCopies.find(route.uri)?.path
            persisted || UriAccess.isInsideTree(uri) || uri.scheme != "content" -> null
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
