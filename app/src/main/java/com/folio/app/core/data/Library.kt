package com.folio.app.core.data

import android.os.Build
import com.folio.app.core.files.LocalCopies
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Recents, pinned files and added folders, plus the limits from plan §4.7:
 * at most [maxRows] recents, and persisted URI grants (recents + folders) kept under [persistedBudget],
 * because Android silently drops the oldest grants past 128 (API < 30) / 512.
 */
class Library(
    private val recents: RecentDao,
    private val folders: PinnedFolderDao,
    private val copies: LocalCopies,
    /** Releases our persisted permission for a URI string. */
    private val releasePermission: (String) -> Unit,
    private val persistedBudget: Int = defaultPersistedBudget(),
    private val maxRows: Int = 500,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    companion object {
        fun defaultPersistedBudget(): Int = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 100 else 450
    }

    fun observeRecents(): Flow<List<RecentFile>> = recents.observeAll()

    fun observeFolders(): Flow<List<PinnedFolder>> = folders.observeAll()

    /** Records that [file] was just opened. Keeps its pinned state and any earlier copy or grant. */
    suspend fun recordOpen(file: RecentFile) {
        val existing = recents.get(file.uri)
        // One file can arrive under several URIs (e.g. two forms from the Downloads provider). Keep only the newest
        // row; it inherits a pin. shortcut: matches on name + size + type, so two distinct identical-looking files share a row.
        val duplicates = if (file.sizeBytes >= 0) recents.sameFileOtherUri(file.uri, file.name, file.sizeBytes, file.mime) else emptyList()
        duplicates.forEach { forget(it) }
        recents.upsert(
            file.copy(
                lastOpenedAt = clock(),
                pinned = existing?.pinned ?: (file.pinned || duplicates.any { it.pinned }),
                sourceApp = file.sourceApp ?: existing?.sourceApp,
                localCopyPath = file.localCopyPath ?: existing?.localCopyPath,
                persisted = file.persisted || existing?.persisted == true,
            ),
        )
        enforceLimits()
    }

    suspend fun setPinned(uri: String, pinned: Boolean) = recents.setPinned(uri, pinned)

    suspend fun setPinnedAll(uris: Collection<String>, pinned: Boolean) {
        uris.forEach { setPinned(it, pinned) }
    }

    /** Removes from Recents, dropping its private copy and permission. */
    suspend fun remove(uri: String) {
        recents.get(uri)?.let { forget(it) }
    }

    suspend fun removeAll(uris: Collection<String>) {
        uris.forEach { remove(it) }
    }

    suspend fun rename(oldUri: String, newName: String, newUri: String? = null) {
        recents.updateAfterRename(oldUri, newUri ?: oldUri, newName)
    }

    /** "Clear recents and copies" in Settings. Folders stay. */
    suspend fun clearRecents() {
        recents.all().filter { it.persisted }.forEach { releasePermission(it.uri) }
        recents.deleteAll()
        copies.clear()
    }

    suspend fun addFolder(folder: PinnedFolder) {
        folders.upsert(folder)
        enforceLimits()
    }

    suspend fun removeFolder(treeUri: String) {
        releasePermission(treeUri)
        folders.delete(treeUri)
    }

    suspend fun enforceLimits() {
        recents.unpinnedBeyond(maxOf(0, maxRows - recents.pinnedCount())).forEach { forget(it) }

        val over = recents.persistedCount() + folders.count() - persistedBudget
        if (over > 0) {
            recents.oldestPersistedUnpinned(over).forEach { row ->
                releasePermission(row.uri)
                // Without the grant the row only stays useful if a private copy can stand in for it.
                if (row.localCopyPath != null && File(row.localCopyPath).exists()) recents.clearPersisted(row.uri)
                else forget(row.copy(persisted = false))
            }
        }

        copies.evict(keep = recents.all().filter { it.pinned }.map { it.uri }.toSet())
    }

    private suspend fun forget(row: RecentFile) {
        if (row.persisted) releasePermission(row.uri)
        copies.delete(row.uri)
        recents.delete(row.uri)
    }
}
