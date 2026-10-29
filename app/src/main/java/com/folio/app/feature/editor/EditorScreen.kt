package com.folio.app.feature.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folio.app.R
import com.folio.app.appContainer
import com.folio.app.core.designsystem.CodeTextStyle
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.ConfirmDialog
import com.folio.app.core.designsystem.components.ErrorState
import com.folio.app.core.designsystem.components.FolioBottomSheet
import com.folio.app.core.designsystem.components.FolioTopBar
import com.folio.app.core.designsystem.components.IconAction
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle
import com.folio.app.core.model.FileFamily
import com.folio.app.formats.text.MarkdownBody
import com.folio.app.core.viewer.HtmlTemplates
import com.folio.app.core.viewer.SafeWebView
import com.folio.app.core.viewer.ViewerState
import com.folio.app.formats.text.SoraText
import com.folio.app.formats.text.TextMate
import com.folio.app.formats.text.soraLook
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SelectionMovement
import kotlinx.coroutines.delay

private val ACCESSORY_KEYS = listOf("Tab", "←", "→", "{", "}", "[", "]", "(", ")", ":", "\"", "'", "/", "=", "#", "-", "*", "`")

@Composable
fun EditorScreen(onClose: () -> Unit, onSaved: () -> Unit) {
    val container = appContainer()
    val vm: EditorViewModel = viewModel { EditorViewModel(container, createSavedStateHandle()) }
    val ui by vm.state.collectAsStateWithLifecycle()
    when (val s = ui) {
        EditorUiState.Loading -> Column(Modifier.fillMaxSize()) {
            FolioTopBar("", onBack = onClose)
            LoadingSkeleton()
        }
        is EditorUiState.Failed -> Column(Modifier.fillMaxSize()) {
            FolioTopBar("", onBack = onClose)
            ErrorState(s.message) { PillButton("Go back", onClose, style = PillStyle.Tonal) }
        }
        is EditorUiState.Ready -> ReadyEditor(vm, s, onClose, onSaved)
    }
}

@Composable
private fun ReadyEditor(vm: EditorViewModel, s: EditorUiState.Ready, onClose: () -> Unit, onSaved: () -> Unit) {
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }
    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var history by remember { mutableIntStateOf(0) } // bumps on edits so Undo/Redo enablement recomposes
    var confirmClose by remember { mutableStateOf(false) }
    var closeOnSave by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    val canPreview = s.file.family == FileFamily.MARKDOWN || s.file.family == FileFamily.HTML
    val state by rememberUpdatedState(s)
    // Keep the editor's input key stable during typing. SoraText recreates its AndroidView when this changes.
    // A fresh composition (such as after rotation) can still seed from the ViewModel's recent text snapshot.
    val editorInitialText = remember(s.file.uri) { vm.liveText ?: s.doc.text }
    fun text() = editor?.text?.toString().orEmpty()

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(s.file.mime)) { uri ->
        if (uri != null) vm.saveCopy(uri, text())
    }
    LaunchedEffect(s.askSaveCopy) {
        if (s.askSaveCopy) {
            vm.saveCopyHandled()
            saveAs.launch(s.file.name)
        }
    }
    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }
    LaunchedEffect(s.savedCount) {
        if (s.savedCount > 0) {
            onSaved()
            if (closeOnSave) onClose()
        }
    }

    // Drafts: every 5 s while there are unsaved edits, and whenever the app goes to the background.
    LaunchedEffect(editor) {
        while (true) {
            delay(5_000)
            if (state.dirty) vm.writeDraft(text())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (state.dirty) vm.writeDraft(text()) }
    LaunchedEffect(history) {
        if (history > 0 && state.dirty) {
            delay(400)
            vm.updateText(text())
        }
    }

    fun close() = if (state.dirty) confirmClose = true else onClose()
    BackHandler(onBack = ::close)

    Scaffold(snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            EditorTopBar(
                name = s.file.name,
                canUndo = history >= 0 && editor?.canUndo() == true,
                canRedo = history >= 0 && editor?.canRedo() == true,
                saveLabel = if (s.file.writable) "Save" else "Save copy",
                canSave = s.dirty && !s.saving,
                preview = preview.takeIf { canPreview },
                onPreview = { preview = !preview },
                onClose = ::close,
                onUndo = { editor?.undo(); history++ },
                onRedo = { editor?.redo(); history++ },
                onSave = { vm.save(text()) },
            )
            s.earlierDraft?.let { draft ->
                DraftBanner(
                    onRestore = { editor?.setText(draft); vm.dismissEarlierDraft(restored = true, draftText = draft) },
                    onDiscard = { vm.dismissEarlierDraft(restored = false) },
                )
            }
            val look = settings?.let { soraLook(Paper.Auto, it) }
            if (look == null) {
                LoadingSkeleton()
            } else Box(Modifier.weight(1f).fillMaxWidth()) {
                SoraText(
                    text = editorInitialText,
                    scope = TextMate.scopeFor(s.file.extension.ifEmpty { if (s.file.family == FileFamily.MARKDOWN) "md" else "" }),
                    readOnly = false,
                    look = look,
                    modifier = Modifier.fillMaxSize(),
                    lineSeparator = s.doc.lineSeparator,
                    onChange = { vm.markDirty(); history++ },
                    onReady = { editor = it },
                )
                // The editor stays composed underneath so nothing typed is lost while previewing.
                if (preview && canPreview) {
                    Surface(color = look.paper.background, modifier = Modifier.fillMaxSize()) {
                        if (s.file.family == FileFamily.HTML) {
                            SafeWebView(HtmlTemplates.page(text(), Paper.Light, fitToScreen = true), Color.White, remember { ViewerState() }, onTap = {})
                        } else {
                            MarkdownBody(text(), look.paper)
                        }
                    }
                }
            }
            if (s.file.family in setOf(FileFamily.CODE, FileFamily.MARKDOWN, FileFamily.HTML)) {
                AccessoryBar { key -> editor?.let { typeKey(it, key) } }
            }
        }
    }

    s.validationError?.let { error ->
        FolioBottomSheet(onDismiss = vm::dismissValidation, title = "Check this file") {
            Text(error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm))
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                PillButton("Save anyway", { vm.save(text(), force = true) }, style = PillStyle.Outline)
                PillButton("Fix it", vm::dismissValidation)
            }
        }
    }
    if (confirmClose) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "Your edits to ${s.file.name} haven't been saved.",
            confirmLabel = "Save",
            onConfirm = { confirmClose = false; closeOnSave = true; vm.save(text()) },
            dismissLabel = "Keep editing",
            onDismiss = { confirmClose = false },
            neutralLabel = "Discard",
            onNeutral = { confirmClose = false; vm.discard(); onClose() },
        )
    }
}

@Composable
private fun EditorTopBar(
    name: String,
    canUndo: Boolean,
    canRedo: Boolean,
    saveLabel: String,
    canSave: Boolean,
    preview: Boolean?,
    onPreview: () -> Unit,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = Spacing.xs, end = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconAction(R.drawable.ic_close, "Close", onClose)
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
        IconAction(R.drawable.ic_undo, "Undo", onUndo, enabled = canUndo)
        IconAction(R.drawable.ic_redo, "Redo", onRedo, enabled = canRedo)
        if (preview != null) {
            IconAction(
                if (preview) R.drawable.ic_edit else R.drawable.ic_visibility,
                if (preview) "Back to editing" else "Preview",
                onPreview,
            )
        }
        PillButton(saveLabel, onSave, enabled = canSave)
    }
}

@Composable
private fun DraftBanner(onRestore: () -> Unit, onDiscard: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = Spacing.screen, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text("You have unsaved changes from earlier", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onDiscard) { Text("Discard") }
            TextButton(onRestore) { Text("Restore") }
        }
    }
}

@Composable
private fun AccessoryBar(onKey: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(40.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            ACCESSORY_KEYS.forEach { key ->
                Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { onKey(key) }) { Text(key, style = CodeTextStyle, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

private fun typeKey(editor: CodeEditor, key: String) = when (key) {
    "Tab" -> editor.insertText("    ", 4)
    "←" -> editor.moveSelection(SelectionMovement.LEFT)
    "→" -> editor.moveSelection(SelectionMovement.RIGHT)
    else -> editor.insertText(key, key.length)
}
