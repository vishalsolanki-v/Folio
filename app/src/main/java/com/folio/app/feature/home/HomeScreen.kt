package com.folio.app.feature.home

import android.net.Uri
import com.folio.app.core.designsystem.components.SheetRow
import com.folio.app.core.designsystem.components.FolioBottomSheet
import com.folio.app.core.designsystem.components.ConfirmDialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.annotation.DrawableRes
import android.content.Intent
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.navigationBarsPadding
import com.folio.app.feature.viewer.RenameDialog
import androidx.activity.result.ActivityResult
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(
    onOpenFile: (String) -> Unit,
    onEditFile: (String) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container.library, container.context) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    // Saved with selectionMode so a rotation keeps both.
    var selected by rememberSaveable(saver = listSaver({ it.value.toList() }, { mutableStateOf(it.toSet()) })) { mutableStateOf(emptySet<String>()) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    // Saved: the system save dialog may outlive this activity (rotation, or Android reclaiming Folio meanwhile).
    var pendingCreate by rememberSaveable { mutableStateOf<String?>(null) }
    var fileToRename by remember { mutableStateOf<RecentFile?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.filter, state.query, state.sort) { selected = emptySet() }

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
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val uri = result.data?.data
        val option = pendingCreate?.let { name -> CreateOption.entries.firstOrNull { it.name == name } }
        pendingCreate = null
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null && option != null) {
            scope.launch {
                val written = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(option.initial.toByteArray(Charsets.UTF_8)) }
                            ?: error("Could not create this file")
                    }
                }
                if (written.isSuccess) {
                    UriAccess.persist(context, uri)
                    container.fileResolver.resolve(uri).getOrNull()?.let { file ->
                        vm.recordCreatedFile(file, UriAccess.isPersisted(context, uri))
                    }
                    onEditFile(uri.toString())
                } else snackbar.showSnackbar("Couldn't create the file in that location")
            }
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

    val visibleRows = state.pinned + state.recents
    fun toggleSelection(uri: String) { selected = if (uri in selected) selected - uri else selected + uri }
    fun exitSelection() { selectionMode = false; selected = emptySet() }
    BackHandler(enabled = selectionMode) { exitSelection() }
    fun create(option: CreateOption) {
        pendingCreate = option.name
        createDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = option.mime
            putExtra(Intent.EXTRA_TITLE, option.filename)
        })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
        bottomBar = {
            if (selectionMode) Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                HorizontalDivider(thickness = Spacing.hairline, color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    val chosenRows = visibleRows.filter { it.uri in selected }
                    val hasSelection = selected.isNotEmpty()
                    val allPinned = hasSelection && chosenRows.all { it.pinned }
                    BarAction(if (allPinned) R.drawable.ic_push_pin_filled else R.drawable.ic_push_pin, if (allPinned) "Unpin" else "Pin", hasSelection) {
                        vm.bulkSetPinned(selected, !allPinned)
                        exitSelection()
                    }
                    BarAction(R.drawable.ic_share, "Share", hasSelection) {
                        scope.launch {
                            val available = withContext(Dispatchers.IO) {
                                chosenRows.mapNotNull { row ->
                                    Sharing.readableUri(context, Uri.parse(row.uri), row.localCopyPath?.let(::File))?.let { row to it }
                                }
                            }
                            when (available.size) {
                                0 -> snackbar.showSnackbar("Selected files are no longer available")
                                1 -> Sharing.share(context, available.single().second, available.single().first.mime, available.single().first.name)
                                else -> Sharing.shareMultiple(context, available.map { it.second })
                            }
                        }
                        exitSelection()
                    }
                    BarAction(R.drawable.ic_playlist_remove, "Remove", hasSelection) { showRemoveConfirm = true }
                }
            }
        },
        floatingActionButton = {
            if (state.loaded && !state.noRecentsAtAll && !selectionMode) {
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
                    if (selectionMode) {
                        Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selected = if (selected.size == visibleRows.size) emptySet() else visibleRows.map { it.uri }.toSet() }) {
                            Text(if (visibleRows.isNotEmpty() && selected.size == visibleRows.size) "Deselect all" else "Select all")
                        }
                        IconAction(R.drawable.ic_close, "Done selecting", ::exitSelection)
                    } else {
                        Text("Folio", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        IconAction(R.drawable.ic_note_add, "New file", { showCreateSheet = true })
                        IconAction(R.drawable.ic_settings, "Settings", onOpenSettings)
                    }
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(start = Spacing.screen - Spacing.sm)) {
                        // Shows the active order, so it's clear why the list is in this order.
                        TextButton(
                            onClick = { sortMenu = true },
                            modifier = Modifier.semantics { contentDescription = "Sort: ${state.sort.label}" },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        ) {
                            Icon(painterResource(R.drawable.ic_sort), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(state.sort.short, style = MaterialTheme.typography.labelLarge)
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            HomeSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    leadingIcon = { if (state.sort == option) Icon(painterResource(R.drawable.ic_check), null) },
                                    onClick = { sortMenu = false; vm.setSort(option) },
                                )
                            }
                        }
                    }
                    FilterChipsRow(
                        labels = HomeFilter.entries.map { it.label },
                        selected = state.filter.ordinal,
                        onSelect = { vm.setFilter(HomeFilter.entries[it]) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = Spacing.xs, end = Spacing.screen),
                    )
                }
                Spacer(Modifier.height(Spacing.sm))

                val now = remember { System.currentTimeMillis() }
                val recentRow: @Composable (RecentFile) -> Unit = { row ->
                    RecentRow(
                        row = row,
                        now = now,
                        onOpen = onOpenFile,
                        onPin = vm::setPinned,
                        onShare = ::share,
                        onRemove = ::remove,
                        onRename = { fileToRename = it },
                        selectionMode = selectionMode,
                        selected = row.uri in selected,
                        onSelect = ::toggleSelection,
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                toggleSelection(row.uri)
                            }
                        },
                    )
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item { FoldersRow(state.folders, onOpenFolder, onAdd = { addFolder.launch(null) }) }
                    if (state.pinned.isNotEmpty()) {
                        item { SectionLabel("Pinned") }
                        items(state.pinned, key = { "p:" + it.uri }) { recentRow(it) }
                        if (state.recents.isNotEmpty()) item { SectionLabel("Recent") }
                    }
                    items(state.recents, key = { it.uri }) { recentRow(it) }
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

    if (showRemoveConfirm) ConfirmDialog(
        title = "Remove from recents?",
        text = "${selected.size} ${if (selected.size == 1) "file" else "files"} will be removed from Folio's recent list. The original files stay where they are.",
        confirmLabel = "Remove",
        onConfirm = { vm.bulkRemove(selected); showRemoveConfirm = false; exitSelection() },
        dismissLabel = "Cancel",
        onDismiss = { showRemoveConfirm = false },
    )
    if (showCreateSheet) FolioBottomSheet(onDismiss = { showCreateSheet = false }, title = "New file") {
        CreateOption.entries.forEach { option ->
            SheetRow(option.icon, option.label, { showCreateSheet = false; create(option) }, supporting = option.filename)
        }
    }

    if (fileToRename != null) {
        RenameDialog(
            currentName = fileToRename!!.name,
            error = renameError,
            onConfirm = { newName ->
                val target = fileToRename ?: return@RenameDialog
                vm.rename(target, newName) { success ->
                    if (success) {
                        fileToRename = null
                        renameError = null
                    } else {
                        renameError = "This file's storage doesn't allow renaming it."
                    }
                }
            },
            onDismiss = {
                fileToRename = null
                renameError = null
            },
        )
    }
}

private enum class CreateOption(@DrawableRes val icon: Int, val label: String, val filename: String, val mime: String, val initial: String) {
    Text(R.drawable.ic_description, "Plain text", "Untitled.txt", "text/plain", ""),
    Markdown(R.drawable.ic_description, "Markdown", "Untitled.md", "text/markdown", "# Untitled\n\n"),
    Json(R.drawable.ic_code, "JSON", "Untitled.json", "application/json", "{\n  \n}\n"),
    Csv(R.drawable.ic_table, "Spreadsheet (CSV)", "Untitled.csv", "text/csv", ""),
}

/** One action in the selection bar: icon over a short label. */
@Composable
private fun BarAction(@DrawableRes icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
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
    onRename: (RecentFile) -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onSelect: (String) -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val format = FormatInfo.describe(row.mime, MimeSniffer.extensionOf(row.name))
    // Plain remember, not the saveable rememberSwipeToDismissBoxState(): a row brought back by Undo must
    // start settled, or its restored "dismissed" state removes it again at once.
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val swipe = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    LaunchedEffect(swipe.currentValue) {
        if (!selectionMode && swipe.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove(row)
    }
    var menu by remember { mutableStateOf(false) }

    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !selectionMode,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(end = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(painterResource(R.drawable.ic_playlist_remove), contentDescription = "Remove from recents", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    ) {
        FileRow(
            name = row.name,
            subtitle = fileSubtitle(context, row.sizeBytes, row.lastOpenedAt, now, row.sourceApp),
            leading = { ExtBadge(format.badge, format.tint, format.friendlyName) },
            onClick = { if (selectionMode) onSelect(row.uri) else onOpen(row.uri) },
            onLongClick = onLongClick,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            trailing = {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onSelect(row.uri) })
                } else Box {
                    IconAction(R.drawable.ic_more_vert, "More options for ${row.name}", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        // Only files Folio keeps access to can be renamed; a storage that refuses says so in the dialog.
                        if (row.persisted) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_rename), null) },
                                onClick = { menu = false; onRename(row) },
                            )
                        }
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
                            leadingIcon = { Icon(painterResource(R.drawable.ic_playlist_remove), null) },
                            onClick = { menu = false; onRemove(row) },
                        )
                    }
                }
            },
        )
    }
}
