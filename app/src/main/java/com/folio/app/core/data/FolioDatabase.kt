package com.folio.app.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentFile::class, PinnedFolder::class], version = 1, exportSchema = true)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun recentDao(): RecentDao
    abstract fun folderDao(): PinnedFolderDao

    companion object {
        fun create(context: Context): FolioDatabase =
            Room.databaseBuilder(context, FolioDatabase::class.java, "folio.db").build()
    }
}
