package com.folio.app.feature.viewer

import android.app.Activity
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folio.app.R
import com.folio.app.appContainer
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.ErrorState
import com.folio.app.core.designsystem.components.FindBar
import com.folio.app.core.designsystem.components.FloatingPill
import com.folio.app.core.designsystem.components.FolioTopBar
import com.folio.app.core.designsystem.components.IconAction
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle
import com.folio.app.core.files.Sharing
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.LocalViewerActions
import com.folio.app.core.viewer.ViewerActions
import com.folio.app.core.viewer.ViewerState
import com.folio.app.navigation.Viewer

private enum class ViewerSheet { More, Info }

/** One screen for every format (plan §2.3): top bar or find bar, the format's content, page and edit pills, sheets. */
@Composable
fun ViewerScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onOpen: (Viewer) -> Unit) {
    val container = appContainer()
    val vm: ViewerViewModel = viewModel { ViewerViewModel(container, createSavedStateHandle()) }
    val ui by vm.state.collectAsStateWithLifecycle()

    when (val s = ui) {
        ViewerUiState.Loading -> Column(Modifier.fillMaxSize()) {
            FolioTopBar("", onBack = onBack)
            LoadingSkeleton()
        }
        is ViewerUiState.Unavailable -> Column(Modifier.fillMaxSize()) {
            FolioTopBar("", onBack = onBack)
            ErrorState(s.message) {
                if (vm.route.record) PillButton("Remove from recents", { vm.removeFromRecents(onBack) }, style = PillStyle.Tonal)
                PillButton("Go back", onBack, style = PillStyle.Outline)
            }
        }
        is ViewerUiState.Ready -> ReadyViewer(vm, s.file, s.viewer, onBack, onEdit, onOpen)
    }
}

@Composable
private fun ReadyViewer(
    vm: ViewerViewModel,
    file: OpenedFile,
    viewer: FormatViewer,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpen: (Viewer) -> Unit,
) {
    val context = LocalContext.current
    val state = vm.viewerState
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val reload by vm.reloadToken.collectAsStateWithLifecycle()
    val canRename by vm.canRename.collectAsStateWithLifecycle()
    val renameError by vm.renameError.collectAsStateWithLifecycle()
    val renameCompleted by vm.renameCompleted.collectAsStateWithLifecycle()
    var finding by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<ViewerSheet?>(null) }
    var pageDialog by rememberSaveable { mutableStateOf(false) }
    var renameDialog by rememberSaveable { mutableStateOf(false) }
    val printJob = remember(viewer, file) { viewer.printJob(context, file) }
    // Only offered when an installed browser accepts this type (checked once per file, off the main thread).
    val browser by produceState<android.content.Intent?>(null, file.uri) {
        value = withContext(Dispatchers.IO) { Sharing.browserFor(context, file.uri, file.mime) }
    }
    LaunchedEffect(renameCompleted) { if (renameCompleted > 0) renameDialog = false }

    fun closeFind() {
        finding = false
        state.query = ""
    }
    BackHandler(enabled = finding, onBack = ::closeFind)
    SystemBarsHidden(state.immersive)

    val actions = remember(file) {
        ViewerActions(
            openWithAnotherApp = { Sharing.openWith(context, file.uri, file.mime) },
            showInfo = { sheet = ViewerSheet.Info },
            openAsText = if (vm.canOpenAsText) ({ onOpen(vm.route.copy(uri = vm.currentUri.value, asText = true, record = false)) }) else null,
            openFile = { uri, source -> onOpen(Viewer(uri, source = source, record = false)) },
        )
    }

    CompositionLocalProvider(LocalViewerActions provides actions) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            AnimatedVisibility(!state.immersive, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                if (finding) {
                    FindBar(
                        query = state.query,
                        onQueryChange = { state.query = it; state.matchIndex = 0 },
                        matchIndex = state.matchIndex,
                        matchCount = state.matchCount,
                        onPrevious = { if (state.matchCount > 0) state.matchIndex = (state.matchIndex - 1 + state.matchCount) % state.matchCount },
                        onNext = { if (state.matchCount > 0) state.matchIndex = (state.matchIndex + 1) % state.matchCount },
                        onClose = ::closeFind,
                        modifier = Modifier.statusBarsPadding(),
                    )
                } else {
                    FolioTopBar(file.name, onBack = onBack, onTitleClick = { sheet = ViewerSheet.Info }) {
                        if (viewer.supportsSearch) IconAction(R.drawable.ic_search, "Find in file", { finding = true })
                        IconAction(R.drawable.ic_more_vert, "More options", { sheet = ViewerSheet.More })
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                key(reload) {
                    viewer.Content(file, state, onTap = { state.immersive = !state.immersive }, modifier = Modifier.fillMaxSize())
                }
                ViewerPills(
                    state,
                    canEdit = viewer.canEdit(file) && state.editBlockedReason == null,
                    onPage = { pageDialog = true },
                    onEdit = { onEdit(vm.currentUri.value) },
                )
            }
        }
    }

    when (sheet) {
        ViewerSheet.More -> MoreSheet(
            file = file,
            paper = state.paper.takeIf { viewer.supportsPaper },
            pinned = pinned.takeIf { vm.route.record },
            canPrint = printJob != null,
            canRename = canRename,
            toggles = viewer.toggles(file).map { it to (state.toggles[it] == true) },
            editBlockedReason = state.editBlockedReason,
            onShare = { Sharing.share(context, file.uri, file.mime, file.name) },
            onOpenInBrowser = browser?.let { intent ->
                {
                    if (!Sharing.openInBrowser(context, intent)) {
                        android.widget.Toast.makeText(context, "The browser couldn't open this file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onOpenWith = actions.openWithAnotherApp,
            onRename = { renameDialog = true },
            onPrint = { printJob?.let { context.getSystemService(PrintManager::class.java).print(file.name, it, null) } },
            onPaper = { state.paper = it },
            onToggle = { label -> state.toggles[label] = state.toggles[label] != true },
            onTogglePin = vm::togglePin,
            onInfo = { sheet = ViewerSheet.Info },
            onDismiss = { if (sheet == ViewerSheet.More) sheet = null },
        )
        ViewerSheet.Info -> FileInfoSheet(file, { vm.lastModified(file) }, state.infoExtras, onDismiss = { sheet = null })
        null -> Unit
    }
    val label = state.pageLabel
    if (pageDialog && label != null) PageJumpDialog(label, onJump = { state.jumpToPage = it }, onDismiss = { pageDialog = false })
    if (renameDialog) RenameDialog(file.name, renameError, onConfirm = { vm.rename(it) }, onDismiss = { renameDialog = false })
}

@Composable
private fun BoxScope.ViewerPills(
    state: ViewerState,
    canEdit: Boolean,
    onPage: () -> Unit,
    onEdit: () -> Unit,
) {
    val label = state.pageLabel
    val pillModifier = Modifier.navigationBarsPadding().padding(Spacing.lg)
    AnimatedVisibility(!state.immersive && label != null, Modifier.align(Alignment.BottomStart), enter = fadeIn(), exit = fadeOut()) {
        FloatingPill(label.orEmpty(), onPage, pillModifier)
    }
    AnimatedVisibility(!state.immersive && canEdit, Modifier.align(Alignment.BottomEnd), enter = fadeIn(), exit = fadeOut()) {
        FloatingPill("Edit", onEdit, pillModifier, icon = R.drawable.ic_edit)
    }
}

/** Immersive reading: hide status and navigation bars; a swipe from the edge shows them briefly. */
@Composable
private fun SystemBarsHidden(hidden: Boolean) {
    val view = LocalView.current
    DisposableEffect(hidden) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hidden) controller?.hide(WindowInsetsCompat.Type.systemBars()) else controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
