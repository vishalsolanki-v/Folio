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
import com.folio.app.core.viewer.ViewerRegistry
import com.folio.app.formats.unknown.UnknownViewer
import java.io.File

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** App-wide singletons (plan §4.1). */
class AppContainer(val context: Context) {
    val fileResolver = FileResolver(context)
    val localCopies = LocalCopies(File(context.filesDir, "recent_copies"))
    val settings = SettingsRepository(context.settingsStore)

    /** Format viewers register here as their milestones land; everything else gets the Unknown viewer. */
    val viewerRegistry = ViewerRegistry(viewers = emptyList(), fallback = UnknownViewer)

    private val database by lazy { FolioDatabase.create(context) }
    val library by lazy {
        Library(database.recentDao(), database.folderDao(), localCopies, releasePermission = { UriAccess.release(context, Uri.parse(it)) })
    }
}
