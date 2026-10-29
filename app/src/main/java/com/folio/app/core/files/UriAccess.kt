package com.folio.app.core.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/** What Folio may do with a URI it was handed (plan §4.6, §4.7). */
object UriAccess {
    /** Providers explicitly advertise rename support through DocumentsContract flags. */
    fun supportsRename(context: Context, uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return runCatching {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
                ?.use { cursor -> cursor.moveToFirst() && !cursor.isNull(0) &&
                    (cursor.getInt(0) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME) != 0 }
                ?: false
        }.getOrDefault(false)
    }

    /** Rename only where Folio can keep access after leaving the sender app. */
    fun canRename(context: Context, uri: Uri): Boolean =
        supportsRename(context, uri) && canWrite(context, uri) && (isPersisted(context, uri) || isInsideTree(uri))

    /**
     * True when we hold a write grant for the source. Our own copies (FileProvider, file://) never count.
     * Providers' FLAG_SUPPORTS_WRITE is not required: the Media documents provider omits it while honouring writes,
     * and FileSaver verifies every write and restores the original if a provider misbehaves.
     */
    fun canWrite(context: Context, uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        if (uri.authority == "${context.packageName}.files") return false
        return context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Keeps access across reboots for URIs from our own pickers. Returns false when the provider only gave
     * temporary access (WhatsApp, Gmail), in which case Recents relies on a local copy.
     */
    fun persist(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        return runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
    }

    /** True when we hold a persisted read grant for exactly this URI. */
    fun isPersisted(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    /** Document URIs inside a folder the user added: readable through the folder's grant. */
    fun isInsideTree(uri: Uri): Boolean = uri.scheme == ContentResolver.SCHEME_CONTENT && uri.pathSegments.firstOrNull() == "tree"

    /** Gives back a grant taken by [persist]. Safe to call when nothing is held. */
    fun release(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.recoverCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

/** [SaveTarget] backed by a content URI. */
class ContentUriTarget(private val resolver: ContentResolver, private val uri: Uri) : SaveTarget {
    override fun openRead(): InputStream = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
    override fun openWriteTruncate(): OutputStream = resolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
}
