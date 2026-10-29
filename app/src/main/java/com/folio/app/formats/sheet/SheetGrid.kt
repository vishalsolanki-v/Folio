package com.folio.app.formats.sheet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.CodeTextStyle
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.FolioBottomSheet
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle
import kotlin.math.roundToInt

/** A cell in the grid: 0-based row and column. */
data class CellPos(val row: Int, val col: Int)

private val NUMBER = Regex("^[-+]?[\\d.,\\s]*\\d[\\d.,]*%?$")

/**
 * Spreadsheet grid (plan M11): sticky column letters and row numbers, one shared horizontal offset for every row,
 * only the visible columns drawn. Tap a cell for its full value.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetGrid(sheet: SheetData, current: CellPos?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    // Cell sizes follow the font size (up to 2x) so large text isn't cut off in every cell.
    val scale = density.fontScale.coerceIn(1f, 2f)
    val rowHeight = 36.dp * scale
    val widths = remember(sheet, scale) { columnWidths(sheet).map { it * scale } }
    val widthsPx = remember(widths, density) { with(density) { widths.map { it.toPx() } } }
    val starts = remember(widthsPx) { widthsPx.runningFold(0f) { acc, w -> acc + w } }
    val totalWidth = starts.last()
    val rowNumberWidth = (24 + 9 * sheet.rows.size.toString().length).dp * scale
    var opened by remember { mutableStateOf<String?>(null) }
    val listState = remember(sheet) { LazyListState() }
    var offsetX by remember(sheet) { mutableFloatStateOf(0f) }
    val line = MaterialTheme.colorScheme.outlineVariant
    val headerColor = MaterialTheme.colorScheme.surfaceContainer
    val accent = MaterialTheme.colorScheme.tertiary
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val numberStyle = CodeTextStyle.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    val headStyle = CodeTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

    BoxWithConstraints(modifier) {
        val viewport = constraints.maxWidth - with(density) { rowNumberWidth.toPx() }
        val maxOffset = (totalWidth - viewport).coerceAtLeast(0f)
        val scrollState = rememberScrollableState { delta ->
            val next = (offsetX - delta).coerceIn(0f, maxOffset)
            val used = offsetX - next
            offsetX = next
            used
        }
        // Search: bring the current hit into view (row below the sticky header, column a third in from the left).
        LaunchedEffect(current) {
            val hit = current ?: return@LaunchedEffect
            listState.scrollToItem((hit.row + 1 - 3).coerceAtLeast(0))
            val colStart = starts[hit.col.coerceAtMost(starts.lastIndex)]
            if (colStart < offsetX || colStart + widthsPx.getOrElse(hit.col) { 0f } > offsetX + viewport) {
                offsetX = (colStart - viewport / 3).coerceIn(0f, maxOffset)
            }
        }
        // Only columns on screen (plus one each side) are composed; changes only when that range changes.
        val visible by remember(starts, viewport) {
            derivedStateOf {
                val first = (starts.indexOfFirst { it > offsetX } - 2).coerceAtLeast(0)
                val endX = offsetX + viewport
                val last = (starts.indexOfFirst { it >= endX }.takeIf { it >= 0 } ?: starts.lastIndex).coerceAtMost(widths.size)
                first until last
            }
        }

        val lineWidth = with(density) { 1.dp.toPx() }
        val pad = with(density) { Spacing.sm.toPx() }
        val highlightWidth = with(density) { 2.dp.toPx() }
        fun DrawScope.lines(x: Float, w: Float) {
            drawLine(line, Offset(x + w - lineWidth / 2, 0f), Offset(x + w - lineWidth / 2, size.height), lineWidth)
            drawLine(line, Offset(x, size.height - lineWidth / 2), Offset(x + w, size.height - lineWidth / 2), lineWidth)
        }
        fun DrawScope.text(value: String, style: TextStyle, x: Float, w: Float) {
            val layout = measurer.measure(
                value, style, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                constraints = Constraints.fixedWidth((w - 2 * pad).toInt().coerceAtLeast(1)),
            )
            drawText(layout, topLeft = Offset(x + pad, (size.height - layout.size.height) / 2))
        }
        // Each row is drawn in one pass (no composable per cell) and reads the horizontal offset only while drawing,
        // so scrolling sideways just redraws and scrolling down composes one small node per row.
        fun Modifier.cells(label: (Int) -> String?, style: (String) -> TextStyle, highlight: Int? = null) = drawBehind {
            for (col in visible) {
                val x = starts[col] - offsetX
                val w = widthsPx[col]
                lines(x, w)
                label(col)?.takeIf { it.isNotEmpty() }?.let { text(it, style(it), x, w) }
                if (col == highlight) drawRect(accent, Offset(x + highlightWidth / 2, highlightWidth / 2), Size(w - highlightWidth, size.height - highlightWidth), style = Stroke(highlightWidth))
            }
        }
        fun Modifier.rowNumber(label: String) = background(headerColor).drawBehind {
            lines(0f, size.width)
            if (label.isNotEmpty()) text(label, headStyle, 0f, size.width)
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 72.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            modifier = Modifier.fillMaxSize().scrollable(scrollState, Orientation.Horizontal),
        ) {
            stickyHeader {
                Row(Modifier.fillMaxWidth().height(rowHeight).background(headerColor)) {
                    Box(Modifier.width(rowNumberWidth).fillMaxHeight().rowNumber(""))
                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds().cells(::columnName, { headStyle }))
                }
            }
            items(sheet.rows.size, key = { it }) { r ->
                val cells = sheet.rows[r]
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    Box(Modifier.width(rowNumberWidth).fillMaxHeight().rowNumber("${r + 1}"))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds()
                            .pointerInput(cells) {
                                detectTapGestures { p ->
                                    val col = starts.indexOfFirst { it > p.x + offsetX } - 1
                                    cells.getOrNull(col)?.takeIf { it.isNotEmpty() }?.let { opened = it }
                                }
                            }
                            .semantics { contentDescription = "Row ${r + 1}: " + cells.take(20).joinToString(", ") { it.orEmpty() } }
                            .cells({ cells.getOrNull(it) }, { if (NUMBER.matches(it)) numberStyle else textStyle }, current?.takeIf { it.row == r }?.col),
                    )
                }
            }
        }
    }

    opened?.let { value ->
        val clipboard = LocalClipboardManager.current
        FolioBottomSheet(onDismiss = { opened = null }, title = "Cell") {
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))
            }
            PillButton(
                "Copy",
                onClick = { clipboard.setText(AnnotatedString(value)); opened = null },
                style = PillStyle.Tonal,
                icon = R.drawable.ic_content_copy,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            )
        }
    }
}

/** clamp(longest value in the first 200 rows × 8 dp + 24 dp, 64 dp, 280 dp), per column (plan M11). */
private fun columnWidths(sheet: SheetData) = List(maxOf(sheet.columnCount, 1)) { col ->
    val chars = sheet.rows.asSequence().take(200).maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
    (chars * 8 + 24).coerceIn(64, 280).dp
}
