package com.folio.app.feature.viewer

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.folio.app.appContainer
import com.folio.app.core.designsystem.components.ExtBadge
import com.folio.app.core.designsystem.components.FileRow
import com.folio.app.core.designsystem.components.FolioTopBar
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.model.FormatInfo
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.model.fileSubtitle
import com.folio.app.navigation.SharedFiles
import com.folio.app.navigation.Viewer

/** Several files shared at once (ACTION_SEND_MULTIPLE): tap one to view it; Back returns here. */
@Composable
fun SharedFilesScreen(route: SharedFiles, onBack: () -> Unit, onOpen: (Viewer) -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val files by produceState<List<Pair<String, OpenedFile?>>?>(null, route) {
        value = route.uris.map { it to container.fileResolver.resolve(Uri.parse(it), source = route.source).getOrNull() }
    }
    Scaffold(topBar = { FolioTopBar("${route.uris.size} shared files", onBack = onBack) }) { padding ->
        val list = files
        if (list == null) {
            LoadingSkeleton(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(list, key = { it.first }) { (uri, file) ->
                val format = file?.let { FormatInfo.describe(it.mime, it.extension) }
                FileRow(
                    name = file?.name ?: "Unreadable file",
                    subtitle = file?.let { fileSubtitle(context, it.sizeBytes, 0, 0, it.sourceApp) }.orEmpty(),
                    leading = { if (format != null) ExtBadge(format.badge, format.tint, format.friendlyName) },
                    onClick = { onOpen(Viewer(uri, source = route.source)) },
                )
            }
        }
    }
}
