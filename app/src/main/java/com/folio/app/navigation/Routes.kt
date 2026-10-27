package com.folio.app.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (plan §4.9). Only URI strings travel; screens re-resolve files themselves.

@Serializable data object Home

@Serializable data class Folder(val treeUri: String, val docId: String, val title: String)

/**
 * @param source package of the app that sent the file (Activity.referrer), for the "From WhatsApp" label
 * @param record false for files that shouldn't appear in Recents (shared text, archive entries)
 * @param asText show it with the text viewer whatever its type ("Open as text")
 */
@Serializable data class Viewer(
    val uri: String,
    val mimeHint: String? = null,
    val source: String? = null,
    val record: Boolean = true,
    val asText: Boolean = false,
)

/** Several files shared to Folio at once: pick one to view. */
@Serializable data class SharedFiles(val uris: List<String>, val source: String? = null)

@Serializable data class Editor(val uri: String)

@Serializable data object Settings

@Serializable data object Licenses
