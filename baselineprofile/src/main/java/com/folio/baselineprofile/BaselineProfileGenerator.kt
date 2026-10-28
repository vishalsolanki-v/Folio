package com.folio.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code Folio runs on its common paths (plan M14): cold start of Home, and opening a PDF and a text
 * file from another app (Open with), then scrolling. Expects the sample files pushed to
 * /sdcard/Download/FolioSamples and media-scanned (see docs/decisions.md).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Folio")), 5_000)

        open("long-200-pages.pdf", "application/pdf")
        device.wait(Until.hasObject(By.textContains("/ 200")), 10_000)
        repeat(3) { device.findObject(By.scrollable(true))?.fling(Direction.DOWN) ?: device.swipe(540, 1800, 540, 500, 20) }
        device.pressBack()

        open("utf16-crlf.txt", "text/plain")
        device.wait(Until.hasObject(By.text("Edit")), 10_000)
        device.pressBack()
    }

    /** Opens a media-store file the way other apps do (ACTION_VIEW with a read grant). */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.open(name: String, mime: String) {
        val id = device.executeShellCommand("content query --uri content://media/external/file --projection _id --where _display_name='$name'")
            .substringAfter("_id=", "").trim().takeWhile { it.isDigit() }
        require(id.isNotEmpty()) { "Sample $name not found in the media store" }
        device.executeShellCommand(
            "am start -W -a android.intent.action.VIEW -t $mime -d content://media/external/file/$id --grant-read-uri-permission -n $PACKAGE/.ExternalOpenActivity",
        )
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.folio.app"
    }
}
