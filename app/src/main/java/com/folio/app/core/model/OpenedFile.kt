package com.folio.app.core.model

import android.net.Uri

enum class FileFamily { PDF, WORD, SHEET, SLIDES, TEXT, MARKDOWN, CODE, HTML, IMAGE, AUDIO, VIDEO, ARCHIVE, UNKNOWN }

/** A file Folio has resolved and is about to show. Built by FileResolver; never holds file contents. */
data class OpenedFile(
    val uri: Uri,
    val name: String,
    val extension: String, // lowercase, no dot, "" if none
    val mime: String, // final MIME after sniffing
    val family: FileFamily,
    val sizeBytes: Long, // -1 if unknown
    val writable: Boolean, // we can overwrite the source
    val sourceApp: String?, // e.g. "WhatsApp", null if unknown
)
