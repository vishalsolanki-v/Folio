package com.folio.app.formats.sheet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class XlsxReaderTest {
    private fun xlsx(parts: Map<String, String>): XlsxReader {
        val file = File.createTempFile("folio", ".xlsx").apply { deleteOnExit() }
        ZipOutputStream(file.outputStream()).use { zip ->
            parts.forEach { (name, body) -> zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry() }
        }
        return XlsxReader(ZipFile(file))
    }

    private val ns = """xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships""""

    private fun workbook(sheets: Map<String, String>, date1904: Boolean = false, styles: String? = null, shared: String? = null) = xlsx(buildMap {
        put("xl/workbook.xml", """<?xml version="1.0"?><workbook $ns>${if (date1904) "<workbookPr date1904=\"1\"/>" else ""}<sheets>""" +
            sheets.keys.mapIndexed { i, n -> """<sheet name="$n" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") + "</sheets></workbook>")
        put("xl/_rels/workbook.xml.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            sheets.keys.mapIndexed { i, _ -> """<Relationship Id="rId${i + 1}" Type="ws" Target="${if (i == 0) "worksheets/sheet${i + 1}.xml" else "/xl/worksheets/sheet${i + 1}.xml"}"/>""" }.joinToString("") +
            "</Relationships>")
        sheets.values.forEachIndexed { i, body -> put("xl/worksheets/sheet${i + 1}.xml", """<?xml version="1.0"?><worksheet $ns><sheetData>$body</sheetData></worksheet>""") }
        styles?.let { put("xl/styles.xml", """<?xml version="1.0"?><styleSheet $ns>$it</styleSheet>""") }
        shared?.let { put("xl/sharedStrings.xml", """<?xml version="1.0"?><sst $ns>$it</sst>""") }
    })

    @Test fun sheetNamesInWorkbookOrder() {
        val book = workbook(linkedMapOf("Summary" to "", "Data" to "<row r=\"1\"><c r=\"A1\"><v>5</v></c></row>", "Notes" to ""))
        assertEquals(listOf("Summary", "Data", "Notes"), book.sheets.map { it.name })
        assertEquals("5", book.readSheet(book.sheets[1]).rows[0][0]) // absolute "/xl/..." target resolves too
    }

    @Test fun sharedStringsIncludingRichTextButNotPhonetics() {
        val book = workbook(
            mapOf("S" to """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>"""),
            shared = """<si><t>Plain</t></si><si><r><t>Ri</t></r><r><rPr><b/></rPr><t xml:space="preserve">ch text</t></r><rPh><t>ignored</t></rPh></si>""",
        )
        val sheet = book.readSheet(book.sheets[0])
        assertArrayEquals(arrayOf<String?>("Plain", null, "Rich text"), sheet.rows[0])
        assertEquals(3, sheet.columnCount)
    }

    @Test fun cellTypesAndSparseRows() {
        val book = workbook(mapOf("S" to """
            <row r="2"><c r="B2" t="inlineStr"><is><t>inline</t></is></c><c r="C2" t="str"><f>A1&amp;"x"</f><v>calc</v></c></row>
            <row r="4"><c r="A4" t="b"><v>1</v></c><c r="B4" t="e"><v>#DIV/0!</v></c><c r="C4"><v>0.30000000000000004</v></c><c r="D4"><v>1.0</v></c><c r="E4"><v>-1.5E-3</v></c></row>
            <row><c><v>7</v></c><c><v>8</v></c></row>
        """))
        val rows = book.readSheet(book.sheets[0]).rows
        assertEquals(0, rows[0].size)
        assertArrayEquals(arrayOf<String?>(null, "inline", "calc"), rows[1])
        assertEquals(0, rows[2].size)
        assertArrayEquals(arrayOf<String?>("TRUE", "#DIV/0!", "0.3", "1", "-0.0015"), rows[3])
        assertArrayEquals(arrayOf<String?>("7", "8"), rows[4]) // rows and cells without r= follow on
    }

    @Test fun datesTimesAndPercentFromStyles() {
        val styles = """<numFmts count="2"><numFmt numFmtId="164" formatCode="dd/mm/yyyy\ hh:mm"/><numFmt numFmtId="165" formatCode="0.0%"/></numFmts>
            <cellStyleXfs count="1"><xf numFmtId="14"/></cellStyleXfs>
            <cellXfs count="5"><xf numFmtId="0"/><xf numFmtId="14"/><xf numFmtId="164"/><xf numFmtId="165"/><xf numFmtId="20"/></cellXfs>"""
        val book = workbook(mapOf("S" to """<row r="1">
            <c r="A1" s="1"><v>45000</v></c><c r="B1" s="2"><v>45000.5</v></c><c r="C1" s="3"><v>0.125</v></c>
            <c r="D1" s="4"><v>0.75</v></c><c r="E1" s="1"><v>59</v></c><c r="F1" s="1"><v>61</v></c><c r="G1"><v>45000</v></c></row>"""), styles = styles)
        assertArrayEquals(
            arrayOf<String?>("2023-03-15", "2023-03-15 12:00", "12.5%", "18:00", "1900-02-28", "1900-03-01", "45000"),
            book.readSheet(book.sheets[0]).rows[0],
        )
    }

    @Test fun date1904System() {
        val book = workbook(mapOf("S" to """<row r="1"><c r="A1" s="1"><v>0</v></c></row>"""), date1904 = true,
            styles = """<cellXfs><xf numFmtId="0"/><xf numFmtId="14"/></cellXfs>""")
        assertEquals("1904-01-01", book.readSheet(book.sheets[0]).rows[0][0])
    }

    @Test fun rowCapMarksTruncated() {
        val body = """<row r="${XlsxReader.MAX_ROWS}"><c r="A${XlsxReader.MAX_ROWS}"><v>1</v></c></row><row r="${XlsxReader.MAX_ROWS + 1}"><c r="A1"><v>2</v></c></row>"""
        val sheet = workbook(mapOf("S" to body)).let { it.readSheet(it.sheets[0]) }
        assertTrue(sheet.truncated)
        assertEquals(XlsxReader.MAX_ROWS, sheet.rows.size)
    }

    @Test fun formatCodes() {
        assertEquals(CellFormat.General, classifyFormatCode("General"))
        assertEquals(CellFormat.General, classifyFormatCode("[Red]#,##0.00"))
        assertEquals(CellFormat.General, classifyFormatCode("_(\$* #,##0_);_(\$* (#,##0)"))
        assertEquals(CellFormat.General, classifyFormatCode("0 \"days\""))
        assertEquals(CellFormat.Percent, classifyFormatCode("0.00%"))
        assertEquals(CellFormat.Date, classifyFormatCode("d-mmm-yy"))
        assertEquals(CellFormat.Date, classifyFormatCode("yyyy\\-mm\\-dd"))
        assertEquals(CellFormat.Date, classifyFormatCode("[$-409]mmmm d, yyyy"))
        assertEquals(CellFormat.TimeSeconds, classifyFormatCode("[h]:mm:ss"))
        assertEquals(CellFormat.TimeSeconds, classifyFormatCode("mm:ss"))
        assertEquals(CellFormat.Time, classifyFormatCode("h:mm AM/PM"))
        assertEquals(CellFormat.DateTime, classifyFormatCode("m/d/yy h:mm"))
    }

    @Test fun columnNames() {
        assertEquals(listOf("A", "Z", "AA", "AZ", "BA", "ZZ", "AAA"), listOf(0, 25, 26, 51, 52, 701, 702).map(::columnName))
        listOf(0, 25, 26, 51, 52, 701, 702, 16383).forEach { assertEquals(it, columnIndex(columnName(it) + "12")) }
    }
}
