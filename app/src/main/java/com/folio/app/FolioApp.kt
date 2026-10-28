package com.folio.app

import android.app.Application
import android.os.Build
import com.folio.app.core.files.CacheCleaner
import dalvik.system.ZipPathValidator
import kotlin.concurrent.thread
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class FolioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Android 14+ rejects a whole ZIP if any entry name contains "..". Folio lists such archives and refuses only
        // those entries itself (ZipSafety), so it opts out of the platform check.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ZipPathValidator.clearCallback()
        thread(name = "folio-cache-clean", priority = Thread.MIN_PRIORITY) { CacheCleaner.clean(cacheDir) }
    }
}

/** The app's [AppContainer], for screens building their ViewModels (plan §4.9). */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as FolioApp).container
