package com.folio.app

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.preferencesDataStore
import com.folio.app.core.data.FolioDatabase
import com.folio.app.core.data.Library
import com.folio.app.core.data.SettingsRepository
import com.folio.app.core.files.FileResolver
import com.folio.app.core.files.LocalCopies
import com.folio.app.core.files.UriAccess
import java.io.File

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** App-wide singletons (plan §4.1). The viewer registry is added in M6. */
class AppContainer(val context: Context) {
    val fileResolver = FileResolver(context)
    val localCopies = LocalCopies(File(context.filesDir, "recent_copies"))
    val settings = SettingsRepository(context.settingsStore)

    private val database by lazy { FolioDatabase.create(context) }
    val library by lazy {
        Library(database.recentDao(), database.folderDao(), localCopies, releasePermission = { UriAccess.release(context, Uri.parse(it)) })
    }
}
