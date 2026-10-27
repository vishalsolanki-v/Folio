package com.folio.app.core.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTemplatesTest {
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
