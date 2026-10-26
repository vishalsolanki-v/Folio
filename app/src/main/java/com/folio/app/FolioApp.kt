package com.folio.app

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class FolioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** The app's [AppContainer], for screens building their ViewModels (plan §4.9). */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as FolioApp).container
