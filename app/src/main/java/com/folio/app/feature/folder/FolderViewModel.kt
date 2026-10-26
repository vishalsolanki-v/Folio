package com.folio.app.feature.folder

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.folio.app.core.data.Library
import com.folio.app.core.files.DocumentTree
import com.folio.app.navigation.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FolderSort { Name, Newest }

sealed interface FolderUiState {
    data object Loading : FolderUiState
    data class Ready(val entries: List<DocumentTree.Entry>, val sort: FolderSort) : FolderUiState
    /** The folder's permission is gone (folder deleted, storage removed, grant revoked). */
    data object Unavailable : FolderUiState
}

class FolderViewModel(
    private val context: Context,
    private val library: Library,
    handle: SavedStateHandle,
) : ViewModel() {
    val route = handle.toRoute<Folder>()
    val treeUri: Uri = Uri.parse(route.treeUri)
    val isRoot: Boolean = route.docId == DocumentTree.rootDocId(treeUri)

    private val _state = MutableStateFlow<FolderUiState>(FolderUiState.Loading)
    val state: StateFlow<FolderUiState> = _state
    private var sort = FolderSort.Name

    init { reload() }

    fun reload() = viewModelScope.launch {
        val entries = withContext(Dispatchers.IO) { runCatching { DocumentTree.list(context, treeUri, route.docId) }.getOrNull() }
        _state.value = if (entries == null) FolderUiState.Unavailable else FolderUiState.Ready(sorted(entries), sort)
    }

    fun setSort(value: FolderSort) {
        sort = value
        _state.update { s -> if (s is FolderUiState.Ready) FolderUiState.Ready(sorted(s.entries), value) else s }
    }

    fun documentUri(entry: DocumentTree.Entry): Uri = DocumentTree.documentUri(treeUri, entry.docId)

    fun removeFolder(done: () -> Unit) = viewModelScope.launch {
        library.removeFolder(route.treeUri)
        done()
    }

    // Folders first, then by the chosen order.
    private fun sorted(entries: List<DocumentTree.Entry>) = entries.sortedWith(
        compareByDescending<DocumentTree.Entry> { it.isFolder }.then(
            when (sort) {
                FolderSort.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                FolderSort.Newest -> compareByDescending { it.lastModified }
            },
        ),
    )
}
