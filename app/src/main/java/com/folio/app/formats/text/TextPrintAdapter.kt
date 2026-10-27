package com.folio.app.formats.text

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.folio.app.R
import kotlinx.coroutines.runBlocking
import java.io.FileOutputStream
import kotlin.concurrent.thread

/**
 * Prints a text file (or saves it as PDF through the system "Save as PDF" printer) without a WebView:
 * lays the text out with StaticLayout and draws page slices into a PrintedPdfDocument.
 */
class TextPrintAdapter(private val context: Context, private val uri: Uri, private val name: String) : PrintDocumentAdapter() {
    private companion object {
        const val PRINT_LIMIT_BYTES = 2L * 1024 * 1024
        const val MARGIN_PT = 48
        const val FONT_PT = 9f
    }

    private val main = Handler(Looper.getMainLooper())
    private var attributes: PrintAttributes? = null
    private var layout: StaticLayout? = null
    private var pageBreaks: List<Int> = emptyList() // first line of each page

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        attributes = newAttributes
        thread(name = "folio-print-layout") {
            val result = runCatching {
                val text = runBlocking { TextLoader.load(context, uri, PRINT_LIMIT_BYTES) }.text
                val media = newAttributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
                val pageWidth = (media.widthMils / 1000f * 72).toInt()
                val pageHeight = (media.heightMils / 1000f * 72).toInt()
                val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = FONT_PT
                    typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
                }
                val built = StaticLayout.Builder.obtain(text, 0, text.length, paint, pageWidth - 2 * MARGIN_PT)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
                val usable = pageHeight - 2 * MARGIN_PT
                val breaks = mutableListOf(0)
                for (line in 0 until built.lineCount) {
                    if (cancellationSignal.isCanceled) return@thread
                    if (built.getLineBottom(line) - built.getLineTop(breaks.last()) > usable) breaks += line
                }
                built to breaks
            }
            main.post {
                result.fold(
                    onSuccess = { (built, breaks) ->
                        layout = built
                        pageBreaks = breaks
                        val info = PrintDocumentInfo.Builder(name.substringBeforeLast('.') + ".pdf")
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(breaks.size)
                            .build()
                        callback.onLayoutFinished(info, true)
                    },
                    onFailure = { callback.onLayoutFailed(it.message) },
                )
            }
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val built = layout ?: return callback.onWriteFailed("Not laid out")
        val attrs = attributes ?: return callback.onWriteFailed("No attributes")
        // Writes every page (allowed by the framework, which then keeps the requested ones) off the main thread.
        thread(name = "folio-print-write") {
            val pdf = PrintedPdfDocument(context, attrs)
            val outcome = runCatching {
                for (page in pageBreaks.indices) {
                    if (cancellationSignal.isCanceled) return@runCatching false
                    val first = pageBreaks[page]
                    val last = (pageBreaks.getOrNull(page + 1) ?: built.lineCount) - 1
                    val pdfPage = pdf.startPage(page)
                    pdfPage.canvas.apply {
                        translate(MARGIN_PT.toFloat(), MARGIN_PT - built.getLineTop(first).toFloat())
                        clipRect(0, built.getLineTop(first), built.width, built.getLineBottom(last))
                        built.draw(this)
                    }
                    pdf.finishPage(pdfPage)
                }
                FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
                true
            }
            pdf.close()
            main.post {
                outcome.fold(
                    onSuccess = { done -> if (done) callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) else callback.onWriteCancelled() },
                    onFailure = { callback.onWriteFailed(it.message) },
                )
            }
        }
    }
}
