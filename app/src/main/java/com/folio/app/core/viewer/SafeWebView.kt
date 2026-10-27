package com.folio.app.core.viewer

import android.content.Context
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * The only WebView in Folio (plan §4.10): no JavaScript, no network, no file or content access, links do nothing.
 * Every sub-resource except inline `data:` URIs and Folio's own bundled font is answered with an empty response.
 */
object SafeWeb {
    /** Base URL for documents, so templates can load the bundled Inter font as `url(inter.ttf)`. */
    const val BASE_URL = "file:///android_res/font/"

    fun newWebView(context: Context, client: WebViewClient = SafeClient()): WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = false
            blockNetworkLoads = true
            blockNetworkImage = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            setGeolocationEnabled(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        webViewClient = client
    }

    fun load(webView: WebView, html: String) = webView.loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null)

    open class SafeClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            return if (url.startsWith("data:") || url == BASE_URL + "inter.ttf") null
            else WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
    }
}

/** Shows [html] in a [SafeWeb] WebView on [background], wired to the find bar ([state]) and single taps ([onTap]). */
@Composable
fun SafeWebView(html: String, background: Color, state: ViewerState, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val tap by rememberUpdatedState(onTap)
    var webView by remember { mutableStateOf<WebView?>(null) }
    // The match the WebView last reported, so find-bar arrows can be turned into findNext(forward) steps.
    var shown by remember { mutableIntStateOf(0) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SafeWeb.newWebView(context).apply {
                val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean { tap(); return true }
                })
                setOnTouchListener { view, event ->
                    taps.onTouchEvent(event)
                    if (event.action == MotionEvent.ACTION_UP) view.performClick()
                    false
                }
                setFindListener { ordinal, count, done ->
                    if (done) {
                        state.matchCount = count
                        shown = ordinal.coerceAtLeast(0)
                        state.matchIndex = shown
                    }
                }
                webView = this
            }
        },
        update = { it.setBackgroundColor(background.toArgb()) },
        onRelease = { it.destroy() },
    )

    LaunchedEffect(webView, html) { webView?.let { SafeWeb.load(it, html) } }
    LaunchedEffect(webView, state.query) {
        val view = webView ?: return@LaunchedEffect
        if (state.query.isBlank()) {
            view.clearMatches()
            state.matchCount = 0
        } else {
            view.findAllAsync(state.query)
        }
    }
    LaunchedEffect(webView, state.matchIndex) {
        val view = webView ?: return@LaunchedEffect
        val count = state.matchCount
        if (count == 0 || state.matchIndex == shown) return@LaunchedEffect
        view.findNext(state.matchIndex == (shown + 1) % count)
    }
}
