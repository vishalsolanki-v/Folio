package com.folio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folio.app.core.designsystem.FolioTheme
import com.folio.app.core.designsystem.components.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        Surface(Modifier.fillMaxSize()) {
            content()
            CrashPrompt()
        }
    }
}

/** After a crash, offers once to email the report; either answer deletes it from the phone. */
@Composable
private fun CrashPrompt() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { report = withContext(Dispatchers.IO) { Support.CrashLog.read(context) } }
    val text = report ?: return
    fun done() {
        report = null
        Support.CrashLog.clear(context)
    }
    ConfirmDialog(
        title = "Folio closed unexpectedly",
        text = "Send a crash report by email? It opens in your email app, so you can see everything in it first. Nothing is sent otherwise.",
        confirmLabel = "Send report",
        onConfirm = { Support.CrashLog.send(context, text); done() },
        dismissLabel = "Not now",
        onDismiss = { done() },
    )
}
