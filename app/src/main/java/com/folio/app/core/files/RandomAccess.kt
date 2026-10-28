package com.folio.app.core.files

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

/** Zip-based formats need random access (`ZipFile`); content URIs are copied into cacheDir/open first. */
object RandomAccess {
    fun file(context: Context, uri: Uri, extension: String): File {
        if (uri.scheme == "file") return File(uri.path!!)
        val name = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        val copy = File(context.cacheDir, "open/$name.$extension").apply { parentFile!!.mkdirs() }
        context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
        return copy
    }
}
