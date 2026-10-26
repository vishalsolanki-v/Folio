package com.folio.app.feature.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.FolioApp
import com.folio.app.MainActivity
import com.folio.app.core.data.RecentFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRemoveUndoTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val library = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as FolioApp).container.library
    private lateinit var scenario: ActivityScenario<MainActivity>

    private fun row(name: String) = RecentFile("content://test/$name", name, "application/pdf", "PDF", 10, 0)

    @Before fun setUp() = runBlocking {
        library.clearRecents()
        library.recordOpen(row("alpha.pdf"))
        library.recordOpen(row("beta.pdf"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun tearDown() {
        scenario.close()
        runBlocking { library.clearRecents() }
    }

    private fun uris() = runBlocking { library.observeRecents().first().map { it.name }.toSet() }

    @Test fun undoBringsRowBack() {
        compose.onNodeWithText("alpha.pdf").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Removed from recents").assertExists()
        compose.onAllNodesWithText("alpha.pdf").assertCountEquals(0)
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("alpha.pdf").assertExists()
        compose.onAllNodesWithText("Removed from recents").assertCountEquals(0)
        assertEquals(setOf("alpha.pdf", "beta.pdf"), uris())
    }

    @Test fun removalCommitsWhenSnackbarTimesOut() {
        compose.onNodeWithText("alpha.pdf").performTouchInput { swipeLeft() }
        compose.waitUntil(15_000) { uris() == setOf("beta.pdf") }
        compose.onAllNodesWithText("alpha.pdf").assertCountEquals(0)
    }

    @Test fun secondSwipeCommitsFirstAndUndoRestoresSecond() {
        compose.onNodeWithText("alpha.pdf").performTouchInput { swipeLeft() }
        compose.onNodeWithText("beta.pdf").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("beta.pdf").assertExists()
        compose.onAllNodesWithText("alpha.pdf").assertCountEquals(0)
        compose.waitUntil(5_000) { uris() == setOf("beta.pdf") }
    }
}
