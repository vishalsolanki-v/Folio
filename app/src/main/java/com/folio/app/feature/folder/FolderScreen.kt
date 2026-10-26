package com.folio.app.feature.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folio.app.R
import com.folio.app.appContainer
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.EmptyState
import com.folio.app.core.designsystem.components.ErrorState
import com.folio.app.core.designsystem.components.ExtBadge
import com.folio.app.core.designsystem.components.FileRow
import com.folio.app.core.designsystem.components.FolderGlyph
import com.folio.app.core.designsystem.components.FolioTopBar
import com.folio.app.core.designsystem.components.IconAction
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle
import com.folio.app.core.files.MimeSniffer
import com.folio.app.core.model.FormatInfo
import com.folio.app.core.model.fileSubtitle
import com.folio.app.navigation.Folder

@Composable
fun FolderScreen(
    onBack: () -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenFile: (String) -> Unit,
    onFolderRemoved: () -> Unit,
) {
    val container = appContainer()
    val vm: FolderViewModel = viewModel { FolderViewModel(container.context, container.library, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            FolioTopBar(vm.route.title, onBack = onBack) {
                Box {
                    IconAction(R.drawable.ic_sort, "Sort", { sortMenu = true })
                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                        FolderSort.entries.forEach { option ->
                            val selected = (state as? FolderUiState.Ready)?.sort == option
                            DropdownMenuItem(
                                text = { Text(if (option == FolderSort.Name) "Name" else "Newest first") },
                                trailingIcon = { if (selected) Icon(painterResource(R.drawable.ic_check), null) },
                                onClick = { sortMenu = false; vm.setSort(option) },
                            )
                        }
                    }
                }
                if (vm.isRoot) {
                    Box {
                        IconAction(R.drawable.ic_more_vert, "More", { moreMenu = true })
                        DropdownMenu(moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove folder") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_close), null) },
                                onClick = { moreMenu = false; vm.removeFolder(onFolderRemoved) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = Spacing.contentMaxWidth)) {
                when (val s = state) {
                    FolderUiState.Loading -> LoadingSkeleton()
                    FolderUiState.Unavailable -> ErrorState("Folio can no longer open this folder.") {
                        if (vm.isRoot) PillButton("Remove folder", { vm.removeFolder(onFolderRemoved) }, style = PillStyle.Tonal)
                        else PillButton("Go back", onBack, style = PillStyle.Tonal)
                    }
                    is FolderUiState.Ready -> if (s.entries.isEmpty()) {
                        EmptyState("This folder is empty", "Tip: pick a folder inside Downloads, like WhatsApp Documents.")
                    } else {
                        FolderList(s, vm, onOpenFolder, onOpenFile)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderList(state: FolderUiState.Ready, vm: FolderViewModel, onOpenFolder: (Folder) -> Unit, onOpenFile: (String) -> Unit) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(state.entries, key = { it.docId }) { entry ->
            if (entry.isFolder) {
                FileRow(
                    name = entry.name,
                    subtitle = "",
                    leading = { FolderGlyph() },
                    onClick = { onOpenFolder(Folder(vm.route.treeUri, entry.docId, entry.name)) },
                )
            } else {
                val format = FormatInfo.describe(entry.mime, MimeSniffer.extensionOf(entry.name))
                FileRow(
                    name = entry.name,
                    subtitle = fileSubtitle(context, entry.sizeBytes, entry.lastModified, now),
                    leading = { ExtBadge(format.badge, format.tint, format.friendlyName) },
                    onClick = { onOpenFile(vm.documentUri(entry).toString()) },
                )
            }
        }
    }
}
