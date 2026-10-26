package com.folio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folio.app.core.designsystem.FolioTheme
import com.folio.app.navigation.FolioNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FolioRoot { FolioNavHost() } }
    }
}

/** Applies the saved theme; draws nothing until settings load so the theme never flashes. */
@Composable
fun FolioRoot(content: @Composable () -> Unit) {
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val theme = settings?.theme ?: return
    FolioTheme(theme) {
        Surface(Modifier.fillMaxSize()) { content() }
    }
}
