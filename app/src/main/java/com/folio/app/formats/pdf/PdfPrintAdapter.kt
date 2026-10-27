package com.folio.app.formats.pdf

import android.content.Context
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
import java.io.FileOutputStream
import kotlin.concurrent.thread

/** Prints a PDF by handing the print system the original bytes, unchanged (plan M9). */
class PdfPrintAdapter(private val context: Context, private val uri: Uri, private val name: String) : PrintDocumentAdapter() {
    private val main = Handler(Looper.getMainLooper())

    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, false)
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback) {
        thread(name = "folio-print-pdf") {
            val outcome = runCatching {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
                }
            }
            main.post {
                when {
                    cancel.isCanceled -> callback.onWriteCancelled()
                    outcome.isSuccess -> callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    else -> callback.onWriteFailed(outcome.exceptionOrNull()?.message)
                }
            }
        }
    }
}
