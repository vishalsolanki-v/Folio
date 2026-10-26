package com.folio.app.libcheck

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikepenz.markdown.m3.Markdown
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownSmokeTest {
    @get:Rule val compose = createComposeRule()

    private fun shows(text: String) =
        compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun markdown_rendersHeadingAndList() {
        compose.setContent {
            MaterialTheme { Markdown("# Folio heading\n\n- first item\n- second item") }
        }
        // The renderer parses off the main thread, so slow devices need a moment.
        compose.waitUntil(5_000) { shows("Folio heading") && shows("second item") }
    }
}
