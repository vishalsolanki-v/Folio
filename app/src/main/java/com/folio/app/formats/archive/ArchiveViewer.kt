package com.folio.app.formats.archive

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.EmptyState
import com.folio.app.core.designsystem.components.ExtBadge
import com.folio.app.core.designsystem.components.FileRow
import com.folio.app.core.designsystem.components.FolderGlyph
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.files.MimeSniffer
import com.folio.app.core.files.RandomAccess
import com.folio.app.core.files.SourceApps
import com.folio.app.core.files.ZipSafety
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.FormatInfo
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.model.fileSubtitle
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.LocalViewerActions
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** ZIP archives (plan M13): browse folders inside, open any file in Folio's own viewers. Back goes up a level first. */
object ArchiveViewer : FormatViewer {
    override val families = setOf(FileFamily.ARCHIVE)
    override val supportsSearch = false
    override val supportsPaper = false

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val opened by produceState<Result<ZipArchive>?>(null, file.uri) {
            value = runCatching { withContext(Dispatchers.IO) { ZipArchive(RandomAccess.file(context, file.uri, "zip")) } }
        }
        val result = opened
        when {
            result == null -> LoadingSkeleton(modifier)
            result.isFailure -> ViewerError(
                if (result.exceptionOrNull() is PasswordProtectedException) "Password-protected ZIPs are coming soon."
                else "This file looks damaged, so Folio can't show it.",
                modifier,
            )
            else -> {
                val archive = result.getOrThrow()
                DisposableEffect(archive) { onDispose { archive.close() } }
                LaunchedEffect(archive) {
                    state.infoExtras = mapOf("Files" to "%,d".format(archive.fileCount), "Unpacked size" to Formatter.formatShortFileSize(context, archive.totalSize))
                }
                Browser(file, archive, modifier)
            }
        }
    }
}

@Composable
private fun Browser(file: OpenedFile, archive: ZipArchive, modifier: Modifier) {
    val context = LocalContext.current
    val actions = LocalViewerActions.current
    val scope = rememberCoroutineScope()
    var path by rememberSaveable(file.uri) { mutableStateOf(listOf<String>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf(false) }
    val folder = remember(path) { path.fold(archive.root as ArchiveFolder?) { f, name -> f?.folders?.get(name) } ?: archive.root }

    BackHandler(enabled = path.isNotEmpty()) { path = path.dropLast(1) }

    fun open(item: ArchiveItem) {
        when {
            !item.safe -> { message = "Folio won't open this item because its path points outside the archive."; return }
            item.encrypted -> { message = "Password-protected ZIPs are coming soon."; return }
            opening -> return
        }
        opening = true
        scope.launch {
            val extracted = runCatching { withContext(Dispatchers.IO) { archive.extract(item, extractRoot(context.cacheDir, file.uri)) } }
            opening = false
            extracted.fold(
                onSuccess = { actions.openFile(Uri.fromFile(it).toString(), SourceApps.ARCHIVE_PREFIX + file.name) },
                onFailure = {
                    message = when (it) {
                        is PasswordProtectedException -> "Password-protected ZIPs are coming soon."
                        is ZipSafety.UnsafeArchiveException -> "This item is too large to open safely."
                        else -> "Folio couldn't open this item. The archive may be damaged."
                    }
                },
            )
        }
    }

    Column(modifier) {
        Breadcrumbs(file.name, path) { depth -> path = path.take(depth) }
        if (path.isEmpty()) {
            Text(
                "%,d files · %s".format(archive.fileCount, Formatter.formatShortFileSize(context, archive.totalSize)) +
                    if (archive.truncated) " · showing the first %,d".format(ZipSafety.MAX_ENTRIES) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            )
        }
        if (folder.folders.isEmpty() && folder.files.isEmpty()) {
            EmptyState(if (path.isEmpty()) "This archive is empty" else "This folder is empty", "")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()), modifier = Modifier.weight(1f)) {
                items(folder.folders.values.toList(), key = { "d/" + it.name }) { sub ->
                    FileRow(
                        name = sub.name,
                        subtitle = countLabel(sub),
                        leading = { FolderGlyph() },
                        onClick = { path = path + sub.name },
                    )
                }
                items(folder.files, key = { "f/" + it.entryName }) { item ->
                    val format = FormatInfo.describe("", MimeSniffer.extensionOf(item.displayName))
                    FileRow(
                        name = item.displayName,
                        subtitle = fileSubtitle(context, item.size, item.time, System.currentTimeMillis()),
                        leading = { ExtBadge(format.badge, format.tint, format.friendlyName) },
                        onClick = { open(item) },
                    )
                }
            }
        }
    }

    if (opening) {
        AlertDialog(onDismissRequest = {}, confirmButton = {}, text = { Text("Opening…") }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton({ message = null }) { Text("OK") } },
            text = { Text(it, style = MaterialTheme.typography.bodyLarge) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }
}

/** "archive.zip › docs › 2024"; tap a crumb to go back up to it. */
@Composable
private fun Breadcrumbs(archiveName: String, path: List<String>, onCrumb: (depth: Int) -> Unit) {
    val crumbs = listOf(archiveName) + path
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen - Spacing.xs),
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
    ) {
        itemsIndexed(crumbs) { depth, name ->
            val last = depth == crumbs.lastIndex
            if (depth > 0) {
                // Its own item so it sits between crumbs and points the reading direction in RTL too.
                Text(
                    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "‹" else "›",
                    // Fixed direction, or bidi mirroring flips the chosen glyph back.
                    style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.md),
                )
            }
            Text(
                name,
                style = MaterialTheme.typography.labelLarge,
                color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(enabled = !last) { onCrumb(depth) }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.md),
            )
        }
    }
}

private fun countLabel(folder: ArchiveFolder): String {
    val n = folder.files.size + folder.folders.size
    return if (n == 1) "1 item" else "$n items"
}

/** cacheDir/archive/<sha1(uri)>/ — one folder per archive, cleaned after a day (FolioApp). */
private fun extractRoot(cacheDir: File, uri: Uri): File {
    val id = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    return File(cacheDir, "archive/$id").apply { mkdirs() }
}
