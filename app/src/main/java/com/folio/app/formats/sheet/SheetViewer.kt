package com.folio.app.formats.sheet

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.FilterChipsRow
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.files.RandomAccess
import com.folio.app.core.files.TextCodec
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

/** XLSX, CSV and TSV in one grid (plan M11). CSV/TSV can also be edited as text. */
object SheetViewer : FormatViewer {
    private const val EDIT_LIMIT_BYTES = 5L * 1024 * 1024
    private const val CSV_READ_LIMIT = 50L * 1024 * 1024

    override val families = setOf(FileFamily.SHEET)
    override val supportsSearch = true
    override val supportsPaper = false

    override fun canEdit(file: OpenedFile) = isDelimited(file) && file.sizeBytes in 0..EDIT_LIMIT_BYTES

    private fun isDelimited(file: OpenedFile) = file.extension in setOf("csv", "tsv") || file.mime.startsWith("text/")

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val workbook by produceState<Result<Workbook>?>(null, file.uri) {
            value = runCatching { withContext(Dispatchers.IO) { open(context, file) } }
                .recoverCatching { if (it is OutOfMemoryError) error("too large") else throw it }
        }
        val result = workbook
        when {
            result == null -> LoadingSkeleton(modifier)
            result.isFailure -> ViewerError(
                if (result.exceptionOrNull()?.message == "too large") "This file is too large to preview on this phone."
                else "This file looks damaged, so Folio can't show it.",
                modifier,
            )
            else -> {
                val book = result.getOrThrow()
                DisposableEffect(book) { onDispose { book.close() } }
                LaunchedEffect(book) { book.editBlockedReason?.let { state.editBlockedReason = it } }
                WorkbookContent(book, state, modifier)
            }
        }
    }

    private fun open(context: Context, file: OpenedFile): Workbook {
        if (isDelimited(file)) {
            val bytes = context.contentResolver.openInputStream(file.uri)!!.use { input ->
                input.readNBytesCompat(CSV_READ_LIMIT + 1)
            }
            val cut = bytes.size > CSV_READ_LIMIT
            val doc = TextCodec.decode(if (cut) bytes.copyOf(CSV_READ_LIMIT.toInt()) else bytes)
            val text = if (cut) doc.text.substringBeforeLast('\n') else doc.text
            val delimiter = if (file.extension == "tsv" || file.mime == "text/tab-separated-values") '\t' else CsvReader.detect(text)
            val sheet = CsvReader.parse(text, file.name, delimiter).let { if (cut) SheetData(it.name, it.rows, it.columnCount, true) else it }
            val blocked = when {
                file.sizeBytes > EDIT_LIMIT_BYTES || cut -> "This file is too big to edit (over 5 MB)."
                !doc.editable -> "This file uses an unusual text encoding, so saving could change it."
                else -> null
            }
            return Workbook(listOf(file.name), { sheet }, {}, blocked)
        }
        val reader = XlsxReader(ZipFile(RandomAccess.file(context, file.uri, "xlsx")))
        val refs = try {
            reader.sheets.also { if (it.isEmpty()) error("No sheets") }
        } catch (e: Throwable) {
            reader.close()
            throw e
        }
        return Workbook(refs.map { it.name }, { i -> reader.readSheet(refs[i]) }, reader::close, null)
    }
}

/** An opened workbook: sheet names plus a loader per sheet (XLSX reads sheets lazily; CSV has one). */
private class Workbook(
    val names: List<String>,
    private val load: (Int) -> SheetData,
    private val onClose: () -> Unit,
    val editBlockedReason: String?,
) {
    private val lock = Any()
    fun sheet(index: Int): SheetData = synchronized(lock) { load(index) }
    fun close() = synchronized(lock) { onClose() }
}

@Composable
private fun WorkbookContent(book: Workbook, state: ViewerState, modifier: Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val loaded = remember(book) { mutableStateMapOf<Int, Result<SheetData>>() }
    LaunchedEffect(book, selected) {
        if (selected !in loaded) loaded[selected] = runCatching { withContext(Dispatchers.IO) { book.sheet(selected) } }
    }
    val sheet = loaded[selected]

    // Search: every non-empty cell, row by row; the grid scrolls to the current one.
    var matches by remember(book, selected) { mutableStateOf<List<CellPos>>(emptyList()) }
    LaunchedEffect(sheet, state.query) {
        val data = sheet?.getOrNull()
        val query = state.query
        if (data == null || query.isBlank()) {
            matches = emptyList()
            state.matchCount = 0
            return@LaunchedEffect
        }
        delay(250)
        matches = withContext(Dispatchers.Default) {
            buildList {
                data.rows.forEachIndexed { r, cells -> cells.forEachIndexed { c, v -> if (v != null && v.contains(query, ignoreCase = true)) add(CellPos(r, c)) } }
            }
        }
        state.matchCount = matches.size
        if (state.matchIndex >= matches.size) state.matchIndex = 0
    }
    LaunchedEffect(sheet) {
        val data = sheet?.getOrNull() ?: return@LaunchedEffect
        state.infoExtras = buildMap {
            if (book.names.size > 1) put("Sheets", book.names.size.toString())
            put("Rows", "%,d".format(data.rows.size) + if (data.truncated) "+" else "")
            put("Columns", "%,d".format(data.columnCount))
        }
    }

    Column(modifier) {
        val data = sheet?.getOrNull()
        when {
            sheet == null -> LoadingSkeleton(Modifier.weight(1f))
            data == null -> ViewerError("This sheet looks damaged, so Folio can't show it.", Modifier.weight(1f))
            else -> {
                if (data.truncated) {
                    Text(
                        "Showing the first %,d rows".format(minOf(data.rows.size, XlsxReader.MAX_ROWS)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    )
                }
                if (data.rows.isEmpty()) {
                    ViewerError("This sheet is empty.", Modifier.weight(1f))
                } else {
                    SheetGrid(data, matches.getOrNull(state.matchIndex), Modifier.weight(1f))
                }
            }
        }
        if (book.names.size > 1) {
            FilterChipsRow(
                book.names,
                selected,
                { selected = it; state.matchIndex = 0 },
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().padding(vertical = Spacing.sm),
            )
        }
    }
}

/** InputStream.readNBytes is API 33+; reads at most [limit] bytes. */
private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toByteArray()
}
