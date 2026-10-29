package com.folio.app.core.files

import android.content.ClipData
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/** Hands files to other apps (Share, Open with another app) with a read grant. */
object Sharing {
    private fun findActivity(context: Context): android.app.Activity? {
        var current: Context? = context
        val visited = HashSet<Context>()
        while (current != null && visited.add(current)) {
            if (current is android.app.Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    private fun launchActivity(context: Context, intent: Intent) {
        val activity = findActivity(context)
        if (activity != null) activity.startActivity(intent)
        else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun launchChooser(context: Context, intent: Intent) {
        launchActivity(context, intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    /** Folio shouldn't offer files back to itself in its own share sheet. */
    private fun withoutFolio(context: Context, chooser: Intent): Intent = chooser.putExtra(
        Intent.EXTRA_EXCLUDE_COMPONENTS,
        arrayOf("com.folio.app.MainActivity", "com.folio.app.ExternalOpenActivity").map { ComponentName(context, it) }.toTypedArray(),
    )

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    /** Our own files (copies, extracted entries) must go out through the FileProvider, never as file://. */
    fun outgoingUri(context: Context, uri: Uri): Uri =
        if (uri.scheme == ContentResolver.SCHEME_FILE) FileProvider.getUriForFile(context, "${context.packageName}.files", File(uri.path!!))
        else uri

    /** The original if it can still be opened, else the private copy, else null. */
    fun readableUri(context: Context, original: Uri, copy: File?): Uri? {
        val readable = runCatching { context.contentResolver.openFileDescriptor(original, "r")?.use { true } }.getOrNull() == true
        return when {
            readable -> original
            copy != null && copy.exists() -> Uri.fromFile(copy)
            else -> null
        }
    }

    /** System chooser for viewing [uri] in another app; Folio's own entry points are left out. */
    fun openWith(context: Context, uri: Uri, mime: String) {
        val out = outgoingUri(context, uri)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(out, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val ours = arrayOf("com.folio.app.MainActivity", "com.folio.app.ExternalOpenActivity").map { ComponentName(context, it) }
        val chooser = Intent.createChooser(view, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, ours.toTypedArray())
        launchChooser(context, chooser)
    }

    fun share(context: Context, uri: Uri, mime: String, name: String) {
        val out = outgoingUri(context, uri)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, out)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(context.contentResolver, name, out)
        launchChooser(context, withoutFolio(context, Intent.createChooser(send, null)))
    }

    /** Shares multiple files at once. */
    fun shareMultiple(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            share(context, uris.first(), "*/*", "file")
            return
        }
        val outUris = ArrayList(uris.map { outgoingUri(context, it) })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, outUris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newUri(context.contentResolver, "files", outUris.first())
        for (i in 1 until outUris.size) clip.addItem(ClipData.Item(outUris[i]))
        send.clipData = clip
        launchChooser(context, withoutFolio(context, Intent.createChooser(send, null)))
    }

    /**
     * The browser to open [uri] in: the default browser if it accepts this file, else another installed browser that
     * does, else null (then Folio offers no "Open in browser" row). Chrome, for one, takes web pages and text but not
     * PDFs or images from other apps.
     */
    fun browserFor(context: Context, uri: Uri, mime: String): Intent? {
        val pm = context.packageManager
        // Browsers don't register Markdown, JSON or code types, but show the same bytes fine as plain text.
        val browserMime = if (mime == "text/html" || mime == "application/xhtml+xml" || mime == "application/pdf" || mime.startsWith("image/")) mime
            else if (mime.startsWith("text/") || mime in TEXT_LIKE) "text/plain" else return null
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(outgoingUri(context, uri), browserMime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val default = pm.resolveActivity(web, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val browsers = (listOfNotNull(default) + pm.queryIntentActivities(web, 0).map { it.activityInfo.packageName }).distinct()
            .filter { it != "android" && it != context.packageName } // "android" = no default chosen yet (the resolver)
        val target = browsers.firstOrNull { pm.queryIntentActivities(Intent(view).setPackage(it), 0).isNotEmpty() } ?: return null
        return view.setPackage(target)
    }

    fun openInBrowser(context: Context, intent: Intent): Boolean = runCatching { launchActivity(context, intent); true }.getOrDefault(false)

    private val TEXT_LIKE = setOf("application/json", "application/xml", "application/x-yaml", "application/yaml", "application/toml", "application/javascript", "application/x-sh")

    /** Renames the original document through its provider. Private viewer copies are never renamed as a substitute. */
    fun renameFile(context: Context, uri: Uri, newName: String): Uri? {
        val clean = LocalCopies.safeName(newName)
        if (!UriAccess.canRename(context, uri)) return null
        return runCatching { DocumentsContract.renameDocument(context.contentResolver, uri, clean) }.getOrNull()
    }
}
