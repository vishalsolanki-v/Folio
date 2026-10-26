package com.folio.app.core.files

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Hands files to other apps (Share, Open with another app) with a read grant. */
object Sharing {
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

    fun share(context: Context, uri: Uri, mime: String, name: String) {
        val out = outgoingUri(context, uri)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, out)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newUri(context.contentResolver, name, out)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
