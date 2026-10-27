package com.folio.app.core.data

import androidx.room.Dao
import androidx.room.Query
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
