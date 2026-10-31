package com.folio.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import java.io.File
import java.util.Date

/**
 * Feedback, crash reports and updates without any network access: everything goes through the user's own email app
 * or browser, and they see it before anything leaves the phone.
 */
object Support {
    const val EMAIL = "workwithvishalsolanki@gmail.com"
    private const val PAGE = "https://www.vishalsolanki.online/apps/folio"

    fun version(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** Opens the email app with a draft; the user can edit or discard it. */
    fun email(context: Context, subject: String, body: String) {
        val uri = "mailto:$EMAIL?subject=${android.net.Uri.encode(subject)}&body=${android.net.Uri.encode(body)}".toUri()
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No email app found. Write to $EMAIL", Toast.LENGTH_LONG).show()
        }
    }

    fun sendFeedback(context: Context) =
        email(context, "Folio feedback", "\n\n\n--\n${deviceLine(context)}")

    /** The website shows the latest version and compares it with [version] from the link. */
    fun checkForUpdates(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, "$PAGE?v=${version(context)}#download".toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found. Visit $PAGE", Toast.LENGTH_LONG).show()
        }
    }

    private fun deviceLine(context: Context) =
        "Folio ${version(context)} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

    /** The last crash, kept on this phone only until the user sends or dismisses it. */
    object CrashLog {
        private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

        fun install(context: Context) {
            val file = file(context)
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                // Capped so the email link stays well under what mail apps accept.
                runCatching { file.writeText("${Date()} · thread ${thread.name}\n\n${error.stackTraceToString().take(6_000)}") }
                previous?.uncaughtException(thread, error)
            }
        }

        fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

        fun clear(context: Context) {
            file(context).delete()
        }

        fun send(context: Context, report: String) = email(
            context,
            "Folio crash report",
            "What were you doing when it closed? (optional)\n\n\n--\n${deviceLine(context)}\n\n$report",
        )
    }
}
