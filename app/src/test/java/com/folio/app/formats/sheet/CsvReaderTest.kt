package com.folio.app.formats.sheet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvReaderTest {
    private fun rows(text: String) = CsvReader.parse(text, "t").rows.map { it.toList() }

    @Test fun quotesEscapesAndNewlinesInsideQuotes() {
        assertEquals(
            listOf(listOf("name", "note"), listOf("Ann", "said \"hi\", twice"), listOf("Bob", "line one\nline two")),
            rows("name,note\r\nAnn,\"said \"\"hi\"\", twice\"\r\nBob,\"line one\nline two\"\r\n"),
        )
    }

    @Test fun emptyFieldsAndNoTrailingNewline() {
        assertEquals(listOf(listOf("a", "", "c"), listOf("", "", "")), rows("a,,c\n,,"))
    }

    @Test fun bomIsDropped() {
        assertEquals(listOf(listOf("x", "y")), rows("\uFEFFx,y\n"))
    }

    @Test fun detectsDelimiters() {
        assertEquals(';', CsvReader.detect("a;b;c\n1,5;2,5;3\n4;5;6\n"))
        assertEquals('\t', CsvReader.detect("a\tb\n1\t2\n"))
        assertEquals('|', CsvReader.detect("a|b|c\n1|2|3\n"))
        assertEquals(',', CsvReader.detect("\"x;y\",b\n\"p;q\",d\n"))
        assertEquals(',', CsvReader.detect("single column\nvalues\n"))
    }

    @Test fun semicolonFileParsesWithDetectedDelimiter() {
        val sheet = CsvReader.parse("Name;Amount\nTea;2,50\n", "t")
        assertArrayEquals(arrayOf<String?>("Tea", "2,50"), sheet.rows[1])
        assertEquals(2, sheet.columnCount)
    }

    @Test fun rowCap() {
        val text = buildString { repeat(XlsxReader.MAX_ROWS + 5) { append(it).append('\n') } }
        val sheet = CsvReader.parse(text, "t")
        assertEquals(XlsxReader.MAX_ROWS, sheet.rows.size)
        assertTrue(sheet.truncated)
        assertFalse(CsvReader.parse("a\nb\n", "t").truncated)
    }
}
