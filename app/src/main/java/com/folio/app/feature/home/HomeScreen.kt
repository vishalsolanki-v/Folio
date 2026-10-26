package com.folio.app.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folio.app.R
import com.folio.app.appContainer
import com.folio.app.core.data.PinnedFolder
import com.folio.app.core.data.RecentFile
import com.folio.app.core.designsystem.CardShape
import com.folio.app.core.designsystem.PillShape
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.EmptyState
import com.folio.app.core.designsystem.components.ExtBadge
import com.folio.app.core.designsystem.components.FileRow
import com.folio.app.core.designsystem.components.FilterChipsRow
import com.folio.app.core.designsystem.components.IconAction
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.SearchField
import com.folio.app.core.files.DocumentTree
import com.folio.app.core.files.MimeSniffer
import com.folio.app.core.files.Sharing
import com.folio.app.core.files.UriAccess
import com.folio.app.core.model.FormatInfo
import com.folio.app.core.model.fileSubtitle
import com.folio.app.navigation.Folder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    onOpenFile: (String) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container.library) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            UriAccess.persist(context, uri)
            onOpenFile(uri.toString())
        }
    }
    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null && UriAccess.persist(context, tree)) {
            vm.addFolder(PinnedFolder(tree.toString(), DocumentTree.displayName(context, tree), System.currentTimeMillis()))
        }
    }

    fun remove(row: RecentFile) {
        vm.hide(row.uri)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = try {
                // With an action, M3 defaults to Indefinite; Long gives a 10 s undo window (stretched by accessibility settings).
                snackbar.showSnackbar("Removed from recents", actionLabel = "Undo", duration = SnackbarDuration.Long)
            } catch (e: CancellationException) {
                vm.commitRemove(row.uri) // left Home while the snackbar showed: the removal stands
                throw e
            }
            if (result == SnackbarResult.ActionPerformed) vm.undoHide(row.uri) else vm.commitRemove(row.uri)
        }
    }

    fun share(row: RecentFile) {
        val copy = row.localCopyPath?.let(::File)
        val uri = Sharing.readableUri(context, Uri.parse(row.uri), copy)
        if (uri == null) scope.launch { snackbar.showSnackbar("This file is no longer available") }
        else Sharing.share(context, uri, row.mime, row.name)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
        floatingActionButton = {
            if (state.loaded && !state.noRecentsAtAll) {
                // Not ExtendedFloatingActionButton: its label reaches accessibility services empty (seen via uiautomator).
                PillButton(
                    "Open file",
                    onClick = { openFile.launch(arrayOf("*/*")) },
                    icon = R.drawable.ic_add,
                    modifier = Modifier.height(56.dp).shadow(6.dp, PillShape),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = Spacing.contentMaxWidth).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Folio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    IconAction(R.drawable.ic_settings, "Settings", onOpenSettings)
                }
                if (!state.loaded) return@Column

                if (state.noRecentsAtAll) {
                    FoldersRow(state.folders, onOpenFolder, onAdd = { addFolder.launch(null) })
                    EmptyState(
                        title = "Open anything",
                        body = "Tap any file in WhatsApp, Gmail or Files and choose Folio. Or open one from here.",
                        actionLabel = "Open file",
                        onAction = { openFile.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                    )
                    return@Column
                }

                SearchField(state.query, vm::setQuery, "Search recent files", Modifier.padding(horizontal = Spacing.screen))
                Spacer(Modifier.height(Spacing.md))
                FilterChipsRow(HomeFilter.entries.map { it.label }, state.filter.ordinal, { vm.setFilter(HomeFilter.entries[it]) })
                Spacer(Modifier.height(Spacing.sm))

                val now = System.currentTimeMillis()
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item { FoldersRow(state.folders, onOpenFolder, onAdd = { addFolder.launch(null) }) }
                    if (state.pinned.isNotEmpty()) {
                        item { SectionLabel("Pinned") }
                        items(state.pinned, key = { "p:" + it.uri }) { row ->
                            RecentRow(row, now, onOpenFile, vm::setPinned, ::share, ::remove)
                        }
                        if (state.recents.isNotEmpty()) item { SectionLabel("Recent") }
                    }
                    items(state.recents, key = { it.uri }) { row ->
                        RecentRow(row, now, onOpenFile, vm::setPinned, ::share, ::remove)
                    }
                    if (state.pinned.isEmpty() && state.recents.isEmpty()) {
                        item {
                            Text(
                                if (state.filter == HomeFilter.Pinned && state.query.isBlank()) "Pin files to keep them here." else "No files match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg, bottom = Spacing.xs),
    )
}

@Composable
private fun FoldersRow(folders: List<PinnedFolder>, onOpen: (Folder) -> Unit, onAdd: () -> Unit) {
    if (folders.isEmpty()) {
        TextButton(onClick = onAdd, modifier = Modifier.padding(start = Spacing.md)) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Add a folder", style = MaterialTheme.typography.labelLarge)
        }
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(folders, key = { it.treeUri }) { folder ->
            FolderCard(folder.name, R.drawable.ic_folder) {
                val tree = Uri.parse(folder.treeUri)
                onOpen(Folder(folder.treeUri, DocumentTree.rootDocId(tree), folder.name))
            }
        }
        item { FolderCard("Add folder", R.drawable.ic_add, onAdd) }
    }
}

@Composable
private fun FolderCard(label: String, icon: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(148.dp).height(72.dp),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecentRow(
    row: RecentFile,
    now: Long,
    onOpen: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onShare: (RecentFile) -> Unit,
    onRemove: (RecentFile) -> Unit,
) {
    val context = LocalContext.current
    val format = FormatInfo.describe(row.mime, MimeSniffer.extensionOf(row.name))
    // Plain remember, not the saveable rememberSwipeToDismissBoxState(): a row brought back by Undo must
    // start settled, or its restored "dismissed" state removes it again at once.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val swipe = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    LaunchedEffect(swipe.currentValue) {
        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove(row)
    }
    var menu by remember { mutableStateOf(false) }

    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(end = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        FileRow(
            name = row.name,
            subtitle = fileSubtitle(context, row.sizeBytes, row.lastOpenedAt, now, row.sourceApp),
            leading = { ExtBadge(format.badge, format.tint, format.friendlyName) },
            onClick = { onOpen(row.uri) },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            trailing = {
                Box {
                    IconAction(R.drawable.ic_more_vert, "More options for ${row.name}", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (row.pinned) "Unpin" else "Pin") },
                            leadingIcon = { Icon(painterResource(if (row.pinned) R.drawable.ic_push_pin_filled else R.drawable.ic_push_pin), null) },
                            onClick = { menu = false; onPin(row.uri, !row.pinned) },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) },
                            onClick = { menu = false; onShare(row) },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from recents") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_close), null) },
                            onClick = { menu = false; onRemove(row) },
                        )
                    }
                }
            },
        )
    }
}
