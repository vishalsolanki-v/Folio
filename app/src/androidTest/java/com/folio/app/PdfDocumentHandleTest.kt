package com.folio.app

import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.formats.pdf.PdfDocumentHandle
import com.folio.app.libcheck.Samples
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** M9: the PDFium wrapper renders, finds text with rects in the right place, and rejects damaged files. */
@RunWith(AndroidJUnit4::class)
class PdfDocumentHandleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun rendersAndFindsText() = runBlocking {
        // Samples.pdf draws the text at x=40, baseline y=80, 32 pt on a 300 x 400 page.
        val handle = PdfDocumentHandle.open(context, Uri.fromFile(Samples.pdf("Folio")), null)
        try {
            assertEquals(1, handle.pages.size)
            val bitmap = handle.render(0, 600)
            assertEquals(800, bitmap.height)
            val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
            assertTrue("page rendered blank", pixels.any { Color.red(it) < 128 })

            val matches = handle.search("folio")
            assertEquals(1, matches.size)
            val rect = matches[0].rects.first()
            assertTrue("left ${rect.left}", rect.left in 0.1f..0.2f)
            assertTrue("top ${rect.top}", rect.top in 0.1f..0.22f)
            assertTrue("bottom ${rect.bottom}", rect.bottom in rect.top..0.22f)
            assertEquals("1", handle.info()["Pages"])
        } finally {
            handle.close()
        }
    }

    @Test fun damagedFileFailsWithoutAskingForAPassword() = runBlocking {
        val bytes = Samples.pdf("Folio").readBytes()
        val broken = context.cacheDir.resolve("broken.pdf").apply { writeBytes(bytes.copyOf(bytes.size / 3)) }
        val error = runCatching { PdfDocumentHandle.open(context, Uri.fromFile(broken), null) }.exceptionOrNull()
        assertTrue("expected a failure, got $error", error != null && error !is PdfDocumentHandle.NeedsPassword)
    }
}
