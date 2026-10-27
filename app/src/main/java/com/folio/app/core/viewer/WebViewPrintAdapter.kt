package com.folio.app.core.viewer

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Prints HTML (DOCX, web pages) through an off-screen SafeWebView. The page is loaded on the first layout pass, then
 * the WebView's own print adapter takes over. One instance can serve several print jobs.
 */
class WebViewPrintAdapter(
    private val context: Context,
    private val name: String,
    private val loadHtml: suspend () -> String,
) : PrintDocumentAdapter() {
    private var scope: CoroutineScope? = null
    private var webView: WebView? = null
    private var delegate: PrintDocumentAdapter? = null
    private var pending: Pair<LayoutResultCallback, (PrintDocumentAdapter) -> Unit>? = null

    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
        delegate?.let { return it.onLayout(old, new, cancel, callback, extras) }
        // Still loading: answer this (latest) layout request once the page is ready.
        pending = callback to { d: PrintDocumentAdapter -> d.onLayout(old, new, cancel, callback, extras) }
        if (scope != null) return
        val main = MainScope().also { scope = it }
        main.launch {
            val html = runCatching { withContext(Dispatchers.IO) { loadHtml() } }.getOrElse {
                pending?.first?.onLayoutFailed("Folio couldn't read this file.")
                return@launch
            }
            webView = SafeWeb.newWebView(context, object : SafeWeb.SafeClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    if (delegate != null) return
                    val ready = view.createPrintDocumentAdapter(name).also { it.onStart() }
                    delegate = ready
                    pending?.second?.invoke(ready)
                    pending = null
                }
            }).also { SafeWeb.load(it, html) }
        }
    }

    override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback) {
        delegate?.onWrite(pages, destination, cancel, callback) ?: callback.onWriteFailed("Not ready")
    }

    override fun onFinish() {
        delegate?.onFinish()
        delegate = null
        pending = null
        webView?.destroy()
        webView = null
        scope?.cancel()
        scope = null
    }
}
