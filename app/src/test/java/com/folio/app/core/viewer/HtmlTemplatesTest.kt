package com.folio.app.core.viewer

import com.folio.app.core.designsystem.Paper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTemplatesTest {
    private val desktopPage = "<!DOCTYPE html><HTML><Head><title>t</title></Head><body><p>hi</p></body></HTML>"

    @Test fun fitToScreenAddsViewportInsideHead() {
        val out = HtmlTemplates.page(desktopPage, Paper.Light, fitToScreen = true)
        assertTrue(out, out.startsWith("<!DOCTYPE html><HTML><Head><meta name=\"viewport\""))
    }

    @Test fun pagesWithTheirOwnViewportAreLeftAlone() {
        val mobile = "<html><head><meta name='viewport' content='width=device-width'></head><body>x</body></html>"
        assertEquals(mobile, HtmlTemplates.page(mobile, Paper.Light, fitToScreen = true))
    }

    @Test fun asWrittenWhenFitIsOffAndPaperIsLight() {
        assertEquals(desktopPage, HtmlTemplates.page(desktopPage, Paper.Auto, fitToScreen = false))
    }

    @Test fun darkPaperTintsAfterTheHtmlTagWhenThereIsNoHead() {
        val out = HtmlTemplates.page("<html><body>x</body></html>", Paper.Dark, fitToScreen = false)
        assertTrue(out, out.startsWith("<html><style>html { background: #1A1A1A; } body {"))
    }

    @Test fun stripsScriptsInAnyCase() {
        assertEquals("<p>a</p><p>b</p>", HtmlTemplates.stripScripts("<p>a</p><SCRIPT type=\"x\">alert(1)\n</Script ><p>b</p>"))
    }

    @Test fun unclosedScriptRemovesTheRest() {
        assertEquals("<p>a</p>", HtmlTemplates.stripScripts("<p>a</p><script>alert(1)<p>b</p>"))
    }

    @Test fun leavesOtherTagsAlone() {
        val html = "<noscript>off</noscript><p class=\"script\">script text</p>"
        assertEquals(html, HtmlTemplates.stripScripts(html))
    }
}
