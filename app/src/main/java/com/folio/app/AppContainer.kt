package com.folio.app

import android.content.Context
import com.folio.app.core.files.FileResolver
import com.folio.app.core.files.LocalCopies
import java.io.File

/** App-wide singletons (plan §4.1). Database, settings and viewer registry are added as milestones land. */
class AppContainer(val context: Context) {
    val fileResolver = FileResolver(context)
    val localCopies = LocalCopies(File(context.filesDir, "recent_copies"))
}
