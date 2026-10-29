package com.folio.app.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<RecentFile>>

    @Query("SELECT * FROM recent WHERE uri = :uri")
    suspend fun get(uri: String): RecentFile?

    @Upsert
    suspend fun upsert(file: RecentFile)

    @Query("UPDATE recent SET pinned = :pinned WHERE uri = :uri")
    suspend fun setPinned(uri: String, pinned: Boolean)

    @Query("UPDATE recent SET persisted = 0 WHERE uri = :uri")
    suspend fun clearPersisted(uri: String)

    @Query("DELETE FROM recent WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("UPDATE recent SET name = :newName WHERE uri = :uri")
    suspend fun rename(uri: String, newName: String)

    @Query("UPDATE recent SET name = :newName, uri = :newUri WHERE uri = :oldUri")
    suspend fun updateUriAndName(oldUri: String, newUri: String, newName: String)

    @Transaction
    suspend fun updateAfterRename(oldUri: String, newUri: String, newName: String) {
        if (oldUri == newUri) {
            rename(oldUri, newName)
            return
        }
        val previous = get(oldUri) ?: return
        val destination = get(newUri)
        if (destination == null) {
            updateUriAndName(oldUri, newUri, newName)
        } else {
            delete(oldUri)
            upsert(
                destination.copy(
                    name = newName,
                    pinned = destination.pinned || previous.pinned,
                    sourceApp = destination.sourceApp ?: previous.sourceApp,
                    localCopyPath = destination.localCopyPath ?: previous.localCopyPath,
                    persisted = destination.persisted || previous.persisted,
                ),
            )
        }
    }

    /** Rows that look like the same file reached through another URI. */
    @Query("SELECT * FROM recent WHERE name = :name AND sizeBytes = :sizeBytes AND mime = :mime AND uri != :uri")
    suspend fun sameFileOtherUri(uri: String, name: String, sizeBytes: Long, mime: String): List<RecentFile>

    @Query("SELECT * FROM recent")
    suspend fun all(): List<RecentFile>

    @Query("DELETE FROM recent")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM recent WHERE pinned = 1")
    suspend fun pinnedCount(): Int

    /** Unpinned rows beyond the newest [keep], oldest last. These are the ones to trim. */
    @Query("SELECT * FROM recent WHERE pinned = 0 ORDER BY lastOpenedAt DESC LIMIT -1 OFFSET :keep")
    suspend fun unpinnedBeyond(keep: Int): List<RecentFile>

    @Query("SELECT COUNT(*) FROM recent WHERE persisted = 1")
    suspend fun persistedCount(): Int

    /** Oldest unpinned rows that hold a persisted permission, oldest first. */
    @Query("SELECT * FROM recent WHERE persisted = 1 AND pinned = 0 ORDER BY lastOpenedAt ASC LIMIT :limit")
    suspend fun oldestPersistedUnpinned(limit: Int): List<RecentFile>
}

@Dao
interface PinnedFolderDao {
    @Query("SELECT * FROM folder ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<PinnedFolder>>

    @Upsert
    suspend fun upsert(folder: PinnedFolder)

    @Query("DELETE FROM folder WHERE treeUri = :treeUri")
    suspend fun delete(treeUri: String)

    @Query("SELECT COUNT(*) FROM folder")
    suspend fun count(): Int
}
