package com.folio.app.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One file in Recents (plan §4.7). [family] is a FileFamily name. */
@Entity(tableName = "recent", indices = [Index("lastOpenedAt")])
data class RecentFile(
    @PrimaryKey val uri: String,
    val name: String,
    val mime: String,
    val family: String,
    val sizeBytes: Long,
    val lastOpenedAt: Long,
    val pinned: Boolean = false,
    val sourceApp: String? = null,
    /** Private copy for temporarily shared files, see LocalCopies. */
    val localCopyPath: String? = null,
    /** We hold a persisted URI permission for [uri]. */
    val persisted: Boolean = false,
)

@Entity(tableName = "folder")
data class PinnedFolder(
    @PrimaryKey val treeUri: String,
    val name: String,
    val addedAt: Long,
)
