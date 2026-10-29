package com.folio.app.feature.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folio.app.core.data.Library
import com.folio.app.core.data.PinnedFolder
import com.folio.app.core.data.RecentFile
import com.folio.app.core.files.Sharing
import com.folio.app.core.files.LocalCopies
import com.folio.app.core.files.UriAccess
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.model.FileFamily.ARCHIVE
import com.folio.app.core.model.FileFamily.AUDIO
import com.folio.app.core.model.FileFamily.CODE
import com.folio.app.core.model.FileFamily.HTML
import com.folio.app.core.model.FileFamily.IMAGE
import com.folio.app.core.model.FileFamily.MARKDOWN
import com.folio.app.core.model.FileFamily.PDF
import com.folio.app.core.model.FileFamily.SHEET
import com.folio.app.core.model.FileFamily.SLIDES
import com.folio.app.core.model.FileFamily.TEXT
import com.folio.app.core.model.FileFamily.VIDEO
import com.folio.app.core.model.FileFamily.WORD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home chips (plan §2.1, M5). [families] null = no family filter. */
enum class HomeFilter(val label: String, val families: Set<FileFamily>?) {
    All("All", null),
    Pinned("Pinned", null),
    Documents("Documents", setOf(PDF, WORD, SLIDES, MARKDOWN, HTML)),
    Sheets("Sheets", setOf(SHEET)),
    TextCode("Text & code", setOf(TEXT, CODE)),
    Media("Media", setOf(IMAGE, AUDIO, VIDEO)),
    Archives("Archives", setOf(ARCHIVE)),
}

enum class HomeSort(val label: String, val short: String) {
    Recent("Recent", "Recent"),
    Oldest("Oldest", "Oldest"),
    Name("Name (A–Z)", "Name"),
    Size("Size (Largest)", "Size"),
}

data class HomeUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val filter: HomeFilter = HomeFilter.All,
    val sort: HomeSort = HomeSort.Recent,
    /** Shown as its own section only when filter = All and sort = Recent. */
    val pinned: List<RecentFile> = emptyList(),
    val recents: List<RecentFile> = emptyList(),
    val folders: List<PinnedFolder> = emptyList(),
    /** True when Recents is empty before any search/filter, which shows the empty state. */
    val noRecentsAtAll: Boolean = true,
)

/** [appContext] is the application context: this ViewModel outlives the screen. */
class HomeViewModel(private val library: Library, private val appContext: Context) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HomeFilter.All)
    private val sort = MutableStateFlow(HomeSort.Recent)
    /** Swiped-away rows hidden while the Undo snackbar shows; removed for real when it goes away. */
    private val pendingRemoval = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<HomeUiState> = combine(
        library.observeRecents(),
        library.observeFolders(),
        query,
        combine(filter, sort) { f, s -> f to s },
        pendingRemoval,
    ) { all, folders, q, (f, s), pending ->
        val visible = all.filter { it.uri !in pending && (q.isBlank() || it.name.contains(q.trim(), ignoreCase = true)) }
        val byFamily = visible.filter { row -> f.families == null || runCatching { FileFamily.valueOf(row.family) }.getOrNull() in f.families }
        val sorted = when (s) {
            HomeSort.Recent -> byFamily.sortedByDescending { it.lastOpenedAt }
            HomeSort.Oldest -> byFamily.sortedBy { it.lastOpenedAt }
            HomeSort.Name -> byFamily.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            HomeSort.Size -> byFamily.sortedByDescending { it.sizeBytes }
        }
        HomeUiState(
            loaded = true,
            query = q,
            filter = f,
            sort = s,
            pinned = if (f == HomeFilter.All && s == HomeSort.Recent) sorted.filter { it.pinned } else emptyList(),
            recents = when {
                f == HomeFilter.All && s == HomeSort.Recent -> sorted.filterNot { it.pinned }
                f == HomeFilter.Pinned -> sorted.filter { it.pinned }
                else -> sorted
            },
            folders = folders,
            noRecentsAtAll = all.none { it.uri !in pending },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: HomeFilter) { filter.value = value }
    fun setSort(value: HomeSort) { sort.value = value }
    fun setPinned(uri: String, pinned: Boolean) = viewModelScope.launch { library.setPinned(uri, pinned) }

    fun bulkSetPinned(uris: Set<String>, pinned: Boolean) = viewModelScope.launch {
        library.setPinnedAll(uris, pinned)
    }

    fun bulkRemove(uris: Set<String>) = viewModelScope.launch {
        library.removeAll(uris)
    }

    fun rename(row: RecentFile, newName: String, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val context = appContext
        val oldUri = Uri.parse(row.uri)
        val supported = withContext(Dispatchers.IO) { UriAccess.canRename(context, oldUri) }
        if (!supported) { onResult(false); return@launch }
        val newUri = withContext(Dispatchers.IO) { Sharing.renameFile(context, oldUri, newName) }
        if (newUri == null) {
            onResult(false)
            return@launch
        }
        val actualName = withContext(Dispatchers.IO) { Sharing.displayName(context, newUri) }
            ?.takeIf { it.isNotBlank() } ?: LocalCopies.safeName(newName)
        library.rename(row.uri, actualName, newUri.toString())
        onResult(true)
    }

    fun hide(uri: String) = pendingRemoval.update { it + uri }
    fun undoHide(uri: String) = pendingRemoval.update { it - uri }
    fun commitRemove(uri: String) = viewModelScope.launch {
        library.remove(uri)
        pendingRemoval.update { it - uri }
    }

    fun addFolder(folder: PinnedFolder) = viewModelScope.launch { library.addFolder(folder) }

    suspend fun recordCreatedFile(file: OpenedFile, persisted: Boolean) {
        library.recordOpen(
            RecentFile(
                uri = file.uri.toString(),
                name = file.name,
                mime = file.mime,
                family = file.family.name,
                sizeBytes = file.sizeBytes,
                lastOpenedAt = 0,
                persisted = persisted,
            ),
        )
    }
}
