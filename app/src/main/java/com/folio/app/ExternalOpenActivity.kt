package com.folio.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.folio.app.core.designsystem.components.ErrorState
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.files.LocalCopies
import com.folio.app.navigation.FolioNavHost
import com.folio.app.navigation.SharedFiles
import com.folio.app.navigation.Viewer
import java.io.File

/**
 * "Open with" and "Share" entry (plan §4.8). Standard launch mode, so it sits on the sender's task and Back from
 * the viewer finishes it and returns to WhatsApp, Gmail or Files.
 */
class ExternalOpenActivity : ComponentActivity() {
    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = startDestination(intent)
        setContent {
            FolioRoot {
                if (start != null) {
                    FolioNavHost(startDestination = start, onExit = ::finish)
                } else {
                    ErrorState("There's nothing here Folio can open.") { PillButton("Close", ::finish) }
                }
            }
        }
    }

    private fun startDestination(intent: Intent): Any? {
        // The system "Open with" sheet hides the sender (referrer is null), so fall back to the URI's authority.
        fun source(uri: Uri?) = referrer?.host?.takeIf { it != packageName } ?: uri?.authority
        val type = intent.type
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
                when {
                    stream != null -> Viewer(stream.toString(), type, source(stream))
                    else -> sharedTextFile(intent)?.let { Viewer(Uri.fromFile(it).toString(), "text/plain", source(null), record = false) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                when (uris.size) {
                    0 -> null
                    1 -> Viewer(uris.single().toString(), source = source(uris.single()))
                    else -> SharedFiles(uris.map { it.toString() }, source(uris.first()))
                }
            }
            else -> intent.data?.let { Viewer(it.toString(), type, source(it)) }
        }
    }

    /**
     * Shared text without a file (e.g. a note shared from a chat) becomes a read-only text file in the cache.
     * The path depends only on the text, so recreating the activity (rotation) points at the same file.
     */
    private fun sharedTextFile(intent: Intent): File? {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotEmpty() } ?: return null
        val name = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let(LocalCopies::safeName)?.removeSuffix(".txt") ?: "Shared text"
        val dir = File(cacheDir, "shared_text/${LocalCopies.keyHash(text)}").apply { mkdirs() }
        return File(dir, "$name.txt").apply { if (!exists()) writeText(text) }
    }
}
