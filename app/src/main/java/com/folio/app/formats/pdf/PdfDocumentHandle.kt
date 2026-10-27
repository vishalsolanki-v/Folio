package com.folio.app.formats.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.api.PdfPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

/** The page size in PDF points (1/72 inch), after the page's own rotation. */
data class PageSize(val width: Int, val height: Int) {
    val aspect: Float get() = width.toFloat() / height.coerceAtLeast(1)
}

/** One search hit. [rects] are fractions (0..1) of the page box, so they scale with any render size. */
class PdfMatch(val page: Int, val rects: List<RectF>)

/**
 * The only class that touches PDFium (plan M9). Every call runs on one shared single-thread dispatcher,
 * so renders, text extraction and close never overlap; queued work that is cancelled never starts.
 */
class PdfDocumentHandle private constructor(
    private val doc: PdfDocument,
    private val fd: ParcelFileDescriptor,
    val pages: List<PageSize>,
) : Closeable {

    class NeedsPassword(val wrongPassword: Boolean) : Exception()

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val pdfium = Dispatchers.IO.limitedParallelism(1)
        private val core by lazy { PdfiumCore() }

        /** Opens [uri]. Throws [NeedsPassword] for encrypted files (and wrong passwords), anything else = damaged/unreadable. */
        suspend fun open(context: Context, uri: Uri, password: String?): PdfDocumentHandle = withContext(pdfium) {
            val fd = seekableDescriptor(context, uri)
            try {
                val doc = core.newDocument(fd, password)
                val sizes = doc.getPageSizes(72).map { PageSize(it.width, it.height) }
                if (sizes.isEmpty()) {
                    doc.close()
                    error("No pages")
                }
                PdfDocumentHandle(doc, fd, sizes)
            } catch (e: PdfPasswordException) {
                runCatching { fd.close() }
                throw NeedsPassword(wrongPassword = password != null)
            } catch (e: Throwable) {
                runCatching { fd.close() }
                throw e
            }
        }

        /**
         * PDFium needs random access. Most providers give a real file descriptor; for streamed ones (pipes,
         * statSize -1) copy into cacheDir/open first.
         */
        private fun seekableDescriptor(context: Context, uri: Uri): ParcelFileDescriptor {
            val direct = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            if (direct != null && direct.statSize >= 0) return direct
            runCatching { direct?.close() }
            val name = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
            val copy = File(context.cacheDir, "open/$name.pdf").apply { parentFile!!.mkdirs() }
            context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
            return ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
        }
    }

    /** Renders page [index] [widthPx] wide on white (PDF pages have no background of their own). */
    suspend fun render(index: Int, widthPx: Int): Bitmap = withContext(pdfium) {
        val size = pages[index]
        val heightPx = (widthPx / size.aspect).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        // true = draw annotations (form fields, highlights) too
        doc.openPage(index)!!.use { page -> page.renderPageBitmap(bitmap, 0, 0, widthPx, heightPx, true, false) }
        bitmap
    }

    suspend fun info(): Map<String, String> = withContext(pdfium) {
        val meta = runCatching { doc.getDocumentMeta() }.getOrNull()
        buildMap {
            put("Pages", pages.size.toString())
            meta?.title?.takeIf { it.isNotBlank() }?.let { put("Title", it) }
            meta?.author?.takeIf { it.isNotBlank() }?.let { put("Author", it) }
            meta?.producer?.takeIf { it.isNotBlank() }?.let { put("Producer", it) }
        }
    }

    /** Case-insensitive search over every page's text, in page order. Yields between pages so rendering stays responsive. */
    suspend fun search(query: String): List<PdfMatch> = withContext(pdfium) {
        val matches = mutableListOf<PdfMatch>()
        for (index in pages.indices) {
            yield()
            doc.openPage(index)!!.use { page ->
                page.openTextPage().use { text ->
                    val count = text.textPageCountChars()
                    if (count <= 0) return@use
                    val content = text.textPageGetText(0, count) ?: return@use
                    val size = pages[index]
                    var from = content.indexOf(query, ignoreCase = true)
                    while (from >= 0) {
                        val rects = (0 until text.textPageCountRects(from, query.length)).mapNotNull { r ->
                            text.textPageGetRect(r)?.let { pageRect ->
                                // Device mapping handles page rotation and crop-box offsets for us.
                                val device = page.mapRectToDevice(0, 0, size.width * 10, size.height * 10, 0, pageRect)
                                RectF(
                                    device.left / (size.width * 10f), device.top / (size.height * 10f),
                                    device.right / (size.width * 10f), device.bottom / (size.height * 10f),
                                )
                            }
                        }
                        matches += PdfMatch(index, rects)
                        from = content.indexOf(query, from + query.length, ignoreCase = true)
                    }
                }
            }
        }
        matches
    }

    /** Closes on the PDFium thread without waiting (after any queued work), so it is safe to call from onDispose. */
    override fun close() {
        CoroutineScope(pdfium).launch {
            runCatching { doc.close() }
            runCatching { fd.close() }
        }
    }
}
