package com.folio.app.formats.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderWidthTest {
    private fun pixels(width: Int, size: PageSize) = width.toLong() * (width / size.aspect).toLong()

    @Test fun tallPageStaysUnderThePixelCapAtEveryZoom() {
        val receipt = PageSize(595, 14_400) // one very long page
        for (base in listOf(1080, 2400)) for (zoom in listOf(1f, 1.5f, 2f, 4f)) {
            val w = renderWidth(base, zoom, receipt)
            assertTrue("base=$base zoom=$zoom width=$w", pixels(w, receipt) <= MAX_RENDER_PIXELS)
            assertTrue(w >= 1)
        }
    }

    @Test fun normalPagesRenderAtScreenWidthUnzoomed() {
        val a4 = PageSize(595, 842)
        assertEquals(1080, renderWidth(1080, 1f, a4))
        assertTrue(renderWidth(1080, 2f, a4) > 1080) // zooming still sharpens ordinary pages
    }
}
