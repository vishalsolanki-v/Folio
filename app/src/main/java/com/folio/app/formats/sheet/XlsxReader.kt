package com.folio.app.formats.sheet

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

/** One sheet's cells as display strings. [rows] index = row number - 1; short arrays mean empty trailing cells. */
class SheetData(val name: String, val rows: List<Array<String?>>, val columnCount: Int, val truncated: Boolean)

/**
 * Reads XLSX workbooks with XmlPullParser (plan M11): sheet names and order, shared strings, number formats
 * (dates, percent), inline strings, cached formula results. Sheets stream, so only the cell strings stay in memory.
 */
class XlsxReader(private val zip: ZipFile) : Closeable {
    class SheetRef(val name: String, val path: String)

    companion object {
        const val MAX_ROWS = 100_000
        const val MAX_COLUMNS = 1_000
        private const val MAX_PART_BYTES = 50L * 1024 * 1024
        private const val MAX_SHEET_BYTES = 512L * 1024 * 1024
        private val BUILTIN_DATE_FORMATS = (14..22).toSet() + (45..47)
        private val BUILTIN_PERCENT_FORMATS = setOf(9, 10)
        private val PRECISION = MathContext(15) // Excel keeps 15 significant digits
    }

    private var date1904 = false
    val sheets: List<SheetRef> by lazy { readWorkbook() }
    private val sharedStrings: List<String> by lazy { readSharedStrings() }
    /** Number format code per cell style index (cellXfs order). */
    private val styleFormats: List<CellFormat> by lazy { readStyles() }

    fun readSheet(ref: SheetRef): SheetData {
        val rows = ArrayList<Array<String?>>()
        var columns = 0
        var truncated = false
        parse(ref.path, MAX_SHEET_BYTES) { p ->
            var rowIndex = -1
            var cells = ArrayList<String?>()
            var col = -1
            var type: String? = null
            var style = 0
            var value: String? = null
            var inline = StringBuilder()
            var inInline = false
            fun finishRow() {
                if (rowIndex < 0) return
                while (rows.size < rowIndex) rows.add(emptyArray())
                while (cells.isNotEmpty() && cells.last() == null) cells.removeAt(cells.lastIndex)
                rows.add(cells.toTypedArray())
                columns = maxOf(columns, cells.size)
            }
            loop@ while (true) {
                when (p.next()) {
                    XmlPullParser.END_DOCUMENT -> break@loop
                    XmlPullParser.START_TAG -> when (p.local) {
                        "row" -> {
                            val r = p.attr("r")?.toIntOrNull()?.minus(1) ?: (maxOf(rowIndex, rows.size - 1) + 1)
                            if (r >= MAX_ROWS) { truncated = true; break@loop }
                            rowIndex = r
                            cells = ArrayList()
                            col = -1
                        }
                        "c" -> {
                            col = p.attr("r")?.let(::columnIndex) ?: (col + 1)
                            type = p.attr("t")
                            style = p.attr("s")?.toIntOrNull() ?: 0
                            value = null
                            inline = StringBuilder()
                        }
                        "v" -> value = p.nextText()
                        "is" -> inInline = true
                        "t" -> if (inInline) inline.append(p.nextText())
                        "rPh" -> p.skipElement() // phonetic hints, not cell text
                    }
                    XmlPullParser.END_TAG -> when (p.local) {
                        "is" -> inInline = false
                        "c" -> {
                            if (col >= MAX_COLUMNS) { truncated = true; continue@loop }
                            val text = display(type, style, value, inline.toString())
                            if (text != null && col >= 0) {
                                while (cells.size <= col) cells.add(null)
                                cells[col] = text
                            }
                        }
                        "row" -> { finishRow(); rowIndex = -1 }
                    }
                }
            }
        }
        while (rows.isNotEmpty() && rows.last().isEmpty()) rows.removeAt(rows.lastIndex)
        return SheetData(ref.name, rows, columns, truncated)
    }

    private fun display(type: String?, style: Int, value: String?, inline: String): String? = when (type) {
        "s" -> value?.trim()?.toIntOrNull()?.let { sharedStrings.getOrNull(it) }
        "inlineStr" -> inline
        "str" -> value
        "b" -> if (value?.trim() == "1") "TRUE" else "FALSE"
        "e" -> value
        else -> value?.trim()?.takeIf { it.isNotEmpty() }?.let { number(it, styleFormats.getOrElse(style) { CellFormat.General }) }
    }

    private fun number(raw: String, format: CellFormat): String {
        val n = raw.toBigDecimalOrNull() ?: return raw
        return when (format) {
            CellFormat.General -> plain(n)
            CellFormat.Percent -> plain(n.movePointRight(2)) + "%"
            else -> runCatching { date(n.toDouble(), format) }.getOrElse { plain(n) }
        }
    }

    private fun plain(n: BigDecimal): String {
        val s = n.round(PRECISION).stripTrailingZeros().toPlainString()
        return if (s == "-0") "0" else s
    }

    private fun date(serial: Double, format: CellFormat): String {
        val days = Math.floor(serial).toLong()
        val seconds = Math.round((serial - days) * 86_400)
        val base = when {
            date1904 -> LocalDate.of(1904, 1, 1)
            days < 60 -> LocalDate.of(1899, 12, 31) // before Excel's fictional 1900-02-29
            else -> LocalDate.of(1899, 12, 30)
        }
        val time = LocalDateTime.of(base, java.time.LocalTime.MIDNIGHT).plusDays(days).plusSeconds(seconds)
        val pattern = when (format) {
            CellFormat.Date -> "yyyy-MM-dd"
            CellFormat.DateTime -> "yyyy-MM-dd HH:mm"
            CellFormat.Time -> "HH:mm"
            else -> "HH:mm:ss"
        }
        return time.format(DateTimeFormatter.ofPattern(pattern))
    }

    private fun readWorkbook(): List<SheetRef> {
        val targets = HashMap<String, String>()
        parse("xl/_rels/workbook.xml.rels", MAX_PART_BYTES) { p ->
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.local == "Relationship") {
                    val id = p.attr("Id") ?: continue
                    val target = p.attr("Target") ?: continue
                    targets[id] = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
                }
            }
        }
        val refs = ArrayList<SheetRef>()
        parse("xl/workbook.xml", MAX_PART_BYTES) { p ->
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.local) {
                    "workbookPr" -> date1904 = p.attr("date1904").let { it == "1" || it == "true" }
                    "sheet" -> {
                        val id = (0 until p.attributeCount).firstOrNull { p.getAttributeName(it).substringAfter(':') == "id" }
                            ?.let { p.getAttributeValue(it) }
                        val path = targets[id] ?: continue
                        refs += SheetRef(p.attr("name") ?: "Sheet ${refs.size + 1}", path)
                    }
                }
            }
        }
        return refs
    }

    private fun readSharedStrings(): List<String> {
        val strings = ArrayList<String>()
        if (zip.getEntry("xl/sharedStrings.xml") == null) return strings
        parse("xl/sharedStrings.xml", MAX_PART_BYTES) { p ->
            var current: StringBuilder? = null
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.local) {
                        "si" -> current = StringBuilder()
                        "t" -> current?.append(p.nextText())
                        "rPh" -> p.skipElement()
                    }
                    XmlPullParser.END_TAG -> if (p.local == "si") { strings += current.toString(); current = null }
                }
            }
        }
        return strings
    }

    private fun readStyles(): List<CellFormat> {
        if (zip.getEntry("xl/styles.xml") == null) return emptyList()
        val custom = HashMap<Int, String>()
        val formats = ArrayList<CellFormat>()
        parse("xl/styles.xml", MAX_PART_BYTES) { p ->
            var inCellXfs = false
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.local) {
                        "numFmt" -> p.attr("numFmtId")?.toIntOrNull()?.let { custom[it] = p.attr("formatCode").orEmpty() }
                        "cellXfs" -> inCellXfs = true
                        "xf" -> if (inCellXfs) formats += classify(p.attr("numFmtId")?.toIntOrNull() ?: 0, custom)
                    }
                    XmlPullParser.END_TAG -> if (p.local == "cellXfs") inCellXfs = false
                }
            }
        }
        return formats
    }

    private fun classify(id: Int, custom: Map<Int, String>): CellFormat {
        if (id in BUILTIN_PERCENT_FORMATS) return CellFormat.Percent
        val code = custom[id] ?: return when (id) {
            in 14..17 -> CellFormat.Date
            22 -> CellFormat.DateTime
            18, 20 -> CellFormat.Time
            19, 21, 45, 46, 47 -> CellFormat.TimeSeconds
            else -> CellFormat.General
        }
        return classifyFormatCode(code)
    }

    private fun parse(path: String, limit: Long, block: (XmlPullParser) -> Unit) {
        val entry = zip.getEntry(path) ?: throw IOException("Missing $path")
        LimitedStream(zip.getInputStream(entry), limit).use { input ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            // Never process DOCTYPEs (no entity expansion). Off by default; some parsers reject setting it at all.
            runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
            parser.setInput(input, null)
            block(parser)
        }
    }

    override fun close() = zip.close()
}

internal enum class CellFormat { General, Date, DateTime, Time, TimeSeconds, Percent }

/** Date/time/percent from a custom number format code, ignoring quoted text, escapes, padding and [colour]/[locale] tags. */
internal fun classifyFormatCode(code: String): CellFormat {
    val bare = code.substringBefore(';')
        .replace(Regex("\"[^\"]*\""), "")
        .replace(Regex("[\\\\_*]."), "")
        .replace(Regex("\\[(?![hms]+])[^]]*]", RegexOption.IGNORE_CASE), "")
        .lowercase()
    val hasDate = bare.any { it in "dy" } || ('m' in bare && 'h' !in bare && 's' !in bare)
    val hasTime = bare.any { it in "hs" }
    return when {
        '%' in bare -> CellFormat.Percent
        hasDate -> if (hasTime) CellFormat.DateTime else CellFormat.Date
        hasTime -> if ('s' in bare) CellFormat.TimeSeconds else CellFormat.Time
        else -> CellFormat.General
    }
}

/** "B3" -> 1 (0-based column). */
internal fun columnIndex(ref: String): Int {
    var col = 0
    for (ch in ref) {
        if (ch !in 'A'..'Z' && ch !in 'a'..'z') break
        col = col * 26 + (ch.uppercaseChar() - 'A' + 1)
    }
    return col - 1
}

/** 0 -> "A", 26 -> "AA". */
fun columnName(index: Int): String {
    var n = index + 1
    val sb = StringBuilder()
    while (n > 0) {
        val rem = (n - 1) % 26
        sb.append('A' + rem)
        n = (n - 1) / 26
    }
    return sb.reverse().toString()
}

private val XmlPullParser.local: String get() = name.substringAfter(':')

private fun XmlPullParser.attr(name: String): String? {
    for (i in 0 until attributeCount) if (getAttributeName(i) == name) return getAttributeValue(i)
    return null
}

private fun XmlPullParser.skipElement() {
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

/** Stops a hostile (zip-bomb) part from being read forever: throws once more than [limit] bytes come out. */
private class LimitedStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
    private fun bump(n: Long) {
        count += n
        if (count > limit) throw IOException("Part too large")
    }
}
