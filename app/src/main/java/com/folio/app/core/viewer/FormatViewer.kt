package com.folio.app.core.viewer

import android.content.Context
import android.print.PrintDocumentAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile

/** State shared between the viewer shell (top bar, find bar, pills, sheets) and the format's content (plan §4.2). */
@Stable
class ViewerState(paper: Paper = Paper.Auto) {
    /** Set by the find bar; "" means not searching. */
    var query by mutableStateOf("")
    /** Set by the viewer. */
    var matchCount by mutableIntStateOf(0)
    /** 0-based; the find bar's arrows change it, the viewer scrolls to it. */
    var matchIndex by mutableIntStateOf(0)
    var paper by mutableStateOf(paper)
    /** "4 / 28" for paged formats, else null (no page pill). */
    var pageLabel by mutableStateOf<String?>(null)
    /** Set by the page dialog (0-based); the viewer consumes it and sets it back to null. */
    var jumpToPage by mutableStateOf<Int?>(null)
    var immersive by mutableStateOf(false)
    /** Extra rows for the File info sheet, e.g. "Pages" to "28". */
    var infoExtras by mutableStateOf<Map<String, String>>(emptyMap())
}

/** One per format family. Each format's content handles its own loading and error UI (plan §4.2). */
interface FormatViewer {
    val families: Set<FileFamily>
    val supportsSearch: Boolean
    val supportsPaper: Boolean

    fun canEdit(file: OpenedFile): Boolean = false

    /** Null hides the Print row. */
    fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter? = null

    @Composable
    fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier)
}

class ViewerRegistry(private val viewers: List<FormatViewer>, val fallback: FormatViewer) {
    fun forFile(file: OpenedFile): FormatViewer = viewers.firstOrNull { file.family in it.families } ?: fallback

    fun hasViewerFor(family: FileFamily): Boolean = viewers.any { family in it.families }
}

/** Shell actions a format's content may offer (e.g. on its error screen). Provided by the viewer screen. */
class ViewerActions(
    val openWithAnotherApp: () -> Unit,
    val showInfo: () -> Unit,
    /** Null when there is no text viewer to switch to. */
    val openAsText: (() -> Unit)?,
)

val LocalViewerActions = staticCompositionLocalOf { ViewerActions({}, {}, null) }
