package com.folio.app.formats.sheet

/** RFC 4180 CSV/TSV (plan M11): quoted fields, doubled quotes, line breaks inside quotes; delimiter auto-detect. */
object CsvReader {
    private val CANDIDATES = charArrayOf(',', ';', '\t', '|')

    fun parse(text: String, name: String, delimiter: Char = detect(text)): SheetData {
        val rows = ArrayList<Array<String?>>()
        var columns = 0
        var truncated = false
        val row = ArrayList<String?>()
        val field = StringBuilder()
        var quoted = false
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        fun endField() {
            row += field.toString()
            field.setLength(0)
        }
        fun endRow() {
            endField()
            if (row.size > XlsxReader.MAX_COLUMNS) { truncated = true; row.subList(XlsxReader.MAX_COLUMNS, row.size).clear() }
            rows += row.toTypedArray()
            columns = maxOf(columns, row.size)
            row.clear()
        }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else {
                    field.append(c)
                }
            } else when (c) {
                '"' -> if (field.isEmpty()) quoted = true else field.append(c)
                delimiter -> endField()
                '\r', '\n' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    endRow()
                    if (rows.size >= XlsxReader.MAX_ROWS) { truncated = i + 1 < text.length; return SheetData(name, rows, columns, truncated) }
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return SheetData(name, rows, columns, truncated)
    }

    /** The candidate that splits the first 5 lines into the same number (> 0) of fields, preferring the most fields. */
    fun detect(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(5).toList()
        if (lines.isEmpty()) return ','
        return CANDIDATES
            .map { d -> d to lines.map { countOutsideQuotes(it, d) } }
            .filter { (_, counts) -> counts.first() > 0 && counts.all { it == counts.first() } }
            .maxByOrNull { (_, counts) -> counts.first() }?.first
            ?: CANDIDATES.maxBy { d -> countOutsideQuotes(lines.first(), d) }.takeIf { countOutsideQuotes(lines.first(), it) > 0 }
            ?: ','
    }

    private fun countOutsideQuotes(line: String, d: Char): Int {
        var quoted = false
        var n = 0
        for (c in line) {
            if (c == '"') quoted = !quoted else if (c == d && !quoted) n++
        }
        return n
    }
}
