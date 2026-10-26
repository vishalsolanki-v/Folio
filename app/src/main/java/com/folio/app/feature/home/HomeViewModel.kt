package com.folio.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folio.app.core.data.Library
import com.folio.app.core.data.PinnedFolder
import com.folio.app.core.data.RecentFile
import com.folio.app.core.model.FileFamily
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

data class HomeUiState(
    val loaded: Boolean = false,
    val query: String = "",
    val filter: HomeFilter = HomeFilter.All,
    /** Shown as its own section only when filter = All. */
    val pinned: List<RecentFile> = emptyList(),
    val recents: List<RecentFile> = emptyList(),
    val folders: List<PinnedFolder> = emptyList(),
    /** True when Recents is empty before any search/filter, which shows the empty state. */
    val noRecentsAtAll: Boolean = true,
)

class HomeViewModel(private val library: Library) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HomeFilter.All)
    /** Swiped-away rows hidden while the Undo snackbar shows; removed for real when it goes away. */
    private val pendingRemoval = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<HomeUiState> = combine(
        library.observeRecents(), library.observeFolders(), query, filter, pendingRemoval,
    ) { all, folders, q, f, pending ->
        val visible = all.filter { it.uri !in pending && (q.isBlank() || it.name.contains(q.trim(), ignoreCase = true)) }
        val byFamily = visible.filter { row -> f.families == null || runCatching { FileFamily.valueOf(row.family) }.getOrNull() in f.families }
        HomeUiState(
            loaded = true,
            query = q,
            filter = f,
            pinned = if (f == HomeFilter.All) byFamily.filter { it.pinned } else emptyList(),
            recents = when (f) {
                HomeFilter.All -> byFamily.filterNot { it.pinned }
                HomeFilter.Pinned -> byFamily.filter { it.pinned }
                else -> byFamily
            },
            folders = folders,
            noRecentsAtAll = all.none { it.uri !in pending },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: HomeFilter) { filter.value = value }
    fun setPinned(uri: String, pinned: Boolean) = viewModelScope.launch { library.setPinned(uri, pinned) }

    fun hide(uri: String) = pendingRemoval.update { it + uri }
    fun undoHide(uri: String) = pendingRemoval.update { it - uri }
    fun commitRemove(uri: String) = viewModelScope.launch {
        library.remove(uri)
        pendingRemoval.update { it - uri }
    }

    fun addFolder(folder: PinnedFolder) = viewModelScope.launch { library.addFolder(folder) }
}
