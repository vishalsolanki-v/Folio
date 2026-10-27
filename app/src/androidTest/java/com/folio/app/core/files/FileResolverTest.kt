package com.folio.app.core.files

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.core.model.FileFamily
import com.folio.app.libcheck.Samples
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileResolverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = FileResolver(context)

    private fun providerUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    @Test fun resolvesPdfThroughContentUri() = runBlocking {
        val pdf = Samples.pdf("Folio")
        val opened = resolver.resolve(providerUri(pdf), source = "com.whatsapp").getOrThrow()
        assertEquals("sample.pdf", opened.name)
        assertEquals(pdf.length(), opened.sizeBytes)
        assertEquals("application/pdf", opened.mime)
        assertEquals(FileFamily.PDF, opened.family)
        assertEquals("WhatsApp", opened.sourceApp)
        assertFalse("our own copies are never writable", opened.writable)
    }

    @Test fun docxWithoutExtensionIsDetected() = runBlocking {
        val noExt = File(Samples.docx("Hi").parentFile, "WhatsApp-doc-123").also { Samples.docx("Hi").copyTo(it, overwrite = true) }
        val opened = resolver.resolve(providerUri(noExt), intentMime = "application/octet-stream").getOrThrow()
        assertEquals(FileFamily.WORD, opened.family)
    }

    @Test fun fileUriWorks() = runBlocking {
        val md = File(context.cacheDir, "notes.md").apply { writeText("# Notes\n") }
        val opened = resolver.resolve(Uri.fromFile(md)).getOrThrow()
        assertEquals(FileFamily.MARKDOWN, opened.family)
        assertFalse(opened.writable)
    }

    @Test fun missingFileIsAFailureNotACrash() = runBlocking {
        val result = resolver.resolve(Uri.fromFile(File(context.cacheDir, "does-not-exist.pdf")))
        assert(result.isFailure)
    }

    @Test fun saverWritesThroughRealContentProvider() {
        val file = File(context.cacheDir, "save-test.txt").apply { writeText("a much longer original line\n") }
        val target = ContentUriTarget(context.contentResolver, providerUri(file))
        val result = FileSaver.overwrite(target, "short\n".toByteArray(), File(context.cacheDir, "save"))
        assertEquals(SaveResult.Saved, result)
        assertArrayEquals("short\n".toByteArray(), file.readBytes())
    }
}
