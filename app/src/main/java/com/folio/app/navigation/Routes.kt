package com.folio.app.navigation

import kotlinx.serialization.Serializable

// Type-safe routes (plan §4.9). Only URI strings travel; screens re-resolve files themselves.

@Serializable data object Home

@Serializable data class Folder(val treeUri: String, val docId: String, val title: String)

@Serializable data class Viewer(val uri: String, val mimeHint: String? = null)

@Serializable data class Editor(val uri: String)

@Serializable data object Settings

@Serializable data object Licenses
