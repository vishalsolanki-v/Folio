package com.folio.app.formats.unknown

import org.junit.Assert.assertEquals
import org.junit.Test

class HexDumpTest {
    @Test fun formatsRowsWithOffsetHexAndAscii() {
        val dump = hexDump("%PDF-1.7\n".toByteArray() + ByteArray(9) { (it + 0x41).toByte() })
        assertEquals(
            "00000000  25 50 44 46 2d 31 2e 37 0a 41 42 43 44 45 46 47  %PDF-1.7.ABCDEFG\n" +
                "00000010  48 49                                            HI",
            dump,
        )
    }
}
