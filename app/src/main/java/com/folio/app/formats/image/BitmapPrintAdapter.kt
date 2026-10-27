package com.folio.app.formats.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
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
import java.io.FileOutputStream
import kotlin.concurrent.thread

/** Prints one image on one page, fitted inside the margins (also "Save as PDF"). Decodes at most ~2400 px. */
class BitmapPrintAdapter(private val context: Context, private val uri: Uri, private val name: String) : PrintDocumentAdapter() {
    private companion object {
        const val MAX_PX = 2400
        const val MARGIN_PT = 36f
    }

    private val main = Handler(Looper.getMainLooper())
    private var attributes: PrintAttributes? = null

    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        attributes = new
        val info = PrintDocumentInfo.Builder(name.substringBeforeLast('.') + ".pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(1)
            .build()
        callback.onLayoutFinished(info, old != new)
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback) {
        val attrs = attributes ?: return callback.onWriteFailed("No attributes")
        thread(name = "folio-print-image") {
            val outcome = runCatching {
                val bitmap = decodeSampled() ?: error("Couldn't read the image")
                val pdf = PrintedPdfDocument(context, attrs)
                try {
                    val page = pdf.startPage(0)
                    val box = RectF(MARGIN_PT, MARGIN_PT, page.info.pageWidth - MARGIN_PT, page.info.pageHeight - MARGIN_PT)
                    val scale = minOf(box.width() / bitmap.width, box.height() / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val left = box.left + (box.width() - w) / 2
                    val top = box.top + (box.height() - h) / 2
                    page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), null)
                    pdf.finishPage(page)
                    FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
                } finally {
                    pdf.close()
                    bitmap.recycle()
                }
            }
            main.post {
                outcome.fold(
                    onSuccess = { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) },
                    onFailure = { callback.onWriteFailed(it.message) },
                )
            }
        }
    }

    private fun decodeSampled(): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PX) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
