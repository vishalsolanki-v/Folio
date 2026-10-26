package com.folio.app.libcheck

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.svg.SvgDecoder
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.eclipse.tm4e.core.registry.IThemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.zwobble.mammoth.DocumentConverter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** M1: proves each risky dependency actually works on this device before any UI is built on it. */
@RunWith(AndroidJUnit4::class)
class LibrarySmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun pdfium_rendersPageAndExtractsText() = runBlocking {
        val file = Samples.pdf("Folio")
        val core = PdfiumCoreKt(Dispatchers.IO)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            core.newDocument(pfd).use { doc ->
                assertEquals(1, doc.getPageCount())
                doc.openPage(0)!!.use { page ->
                    val w = page.getPageWidthPoint()
                    val h = page.getPageHeightPoint()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.renderPageBitmap(bmp, 0, 0, w, h)
                    val pixels = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                    assertTrue("page rendered blank", pixels.any { Color.red(it) < 128 })
                    page.openTextPage().use { text ->
                        val chars = text.textPageCountChars()
                        assertTrue(text.textPageGetText(0, chars).orEmpty().contains("Folio"))
                    }
                }
            }
        }
    }

    @Test
    fun mammoth_convertsDocxToHtml() {
        val html = Samples.docx("Hello Folio").inputStream().use { DocumentConverter().convertToHtml(it).value }
        assertTrue(html, html.contains("<strong>Hello Folio</strong>"))
    }

    @Test
    fun mammoth_doesNotResolveExternalEntities() {
        val secret = context.filesDir.resolve("secret.txt").apply { writeText("TOPSECRET") }
        val xxe = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE w:document [<!ENTITY xxe SYSTEM "file://${secret.absolutePath}">]>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body><w:p><w:r><w:t>before &xxe; after</w:t></w:r></w:p></w:body>
</w:document>"""
        val result = runCatching {
            Samples.docx("", documentXml = xxe).inputStream().use { DocumentConverter().convertToHtml(it).value }
        }
        assertTrue("DOCTYPE must be rejected, got: ${result.getOrNull()}", result.isFailure)
    }

    @Test
    fun sora_highlightsWithTextMate() {
        val testAssets = instrumentation.context.assets
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(testAssets))
        val themePath = "textmate/quietlight.json"
        ThemeRegistry.getInstance().loadTheme(
            ThemeModel(
                IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(themePath), themePath, null),
                "quietlight",
            )
        )
        ThemeRegistry.getInstance().setTheme("quietlight")
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

        var lines = 0
        instrumentation.runOnMainSync {
            val editor = CodeEditor(context)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
            editor.setEditorLanguage(TextMateLanguage.create("source.python", true))
            editor.setText("def hello():\n    return 'Folio'\n")
            editor.isEditable = false
            lines = editor.text.lineCount
            editor.release()
        }
        assertEquals(3, lines)
    }

    @Test
    fun coil_decodesPngAndSvg() = runBlocking {
        val loader = ImageLoader.Builder(context).components { add(SvgDecoder.Factory()) }.build()
        for (file in listOf(Samples.png(), Samples.svg())) {
            val result = loader.execute(ImageRequest.Builder(context).data(file).build())
            assertTrue("${file.name}: $result", result is SuccessResult)
        }
    }

    @Test
    fun media3_preparesAudio() {
        val uri = Uri.fromFile(Samples.wav())
        val ready = CountDownLatch(1)
        lateinit var player: ExoPlayer
        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build()
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) ready.countDown()
                }
            })
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
        }
        val ok = ready.await(10, TimeUnit.SECONDS)
        instrumentation.runOnMainSync { player.release() }
        assertTrue("player never became ready", ok)
    }
}
