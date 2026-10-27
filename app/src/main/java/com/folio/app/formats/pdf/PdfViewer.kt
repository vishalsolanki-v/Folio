package com.folio.app.formats.pdf

import android.content.Context
import android.graphics.Bitmap
import android.print.PrintDocumentAdapter
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.colors
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.sqrt

/** PDF through PDFium (plan M9): continuous pages, pinch zoom with sharp re-render, search with highlights, passwords. */
object PdfViewer : FormatViewer {
    override val families = setOf(FileFamily.PDF)
    override val supportsSearch = true
    override val supportsPaper = true

    override fun printJob(context: Context, file: OpenedFile): PrintDocumentAdapter = PdfPrintAdapter(context, file.uri, file.name)

    /** Passwords live in memory for this process only (never saved state), so rotation doesn't ask again. */
    private val passwords = mutableMapOf<String, String>()

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val key = file.uri.toString()
        var attempt by remember(key) { mutableIntStateOf(0) }
        var load by remember(key) { mutableStateOf<PdfLoad>(PdfLoad.Loading) }

        LaunchedEffect(key, attempt) {
            load = PdfLoad.Loading
            load = try {
                PdfLoad.Ready(PdfDocumentHandle.open(context, file.uri, passwords[key]))
            } catch (e: CancellationException) {
                throw e
            } catch (e: PdfDocumentHandle.NeedsPassword) {
                if (e.wrongPassword) passwords.remove(key)
                PdfLoad.Locked(wrong = e.wrongPassword)
            } catch (_: OutOfMemoryError) {
                PdfLoad.Failed("This file is too large to preview on this phone.")
            } catch (_: Throwable) {
                PdfLoad.Failed("This file looks damaged, so Folio can't show it.")
            }
        }

        when (val l = load) {
            PdfLoad.Loading -> LoadingSkeleton(modifier)
            is PdfLoad.Failed -> ViewerError(l.message, modifier)
            is PdfLoad.Locked -> PasswordPrompt(l.wrong, modifier) { password ->
                passwords[key] = password
                attempt++
            }
            is PdfLoad.Ready -> {
                DisposableEffect(l.handle) { onDispose { l.handle.close() } }
                LaunchedEffect(l.handle) { state.infoExtras = l.handle.info() }
                PdfPages(l.handle, state, onTap, modifier)
            }
        }
    }
}

private sealed interface PdfLoad {
    data object Loading : PdfLoad
    class Ready(val handle: PdfDocumentHandle) : PdfLoad
    class Locked(val wrong: Boolean) : PdfLoad
    class Failed(val message: String) : PdfLoad
}

private const val MAX_ZOOM = 4f
private const val MAX_RENDER_WIDTH = 4096
private const val MAX_RENDER_PIXELS = 8_000_000 // ~32 MB per page bitmap; zoomed pages above this get slightly soft

@OptIn(FlowPreview::class)
@Composable
private fun PdfPages(handle: PdfDocumentHandle, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
    val dark = LocalFolioDark.current
    val paper = state.paper
    val filter = remember(paper, dark) { paperFilter(paper, dark) }
    val placeholder = if (filter == null) Color.White else paper.colors(dark).background
    val backdrop = if (dark) Color(0xFF0F0F0E) else Color(0xFFF3F1EC)
    val accent = MaterialTheme.colorScheme.tertiary
    val listState = rememberLazyListState()
    val cache = remember(handle) {
        object : LruCache<Long, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
            override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
        }
    }
    DisposableEffect(cache) { onDispose { cache.evictAll() } }

    var zoom by remember(handle) { mutableFloatStateOf(1f) }
    var panX by remember(handle) { mutableFloatStateOf(0f) }
    // The zoom pages are rendered for; follows [zoom] once a pinch has settled.
    var renderZoom by remember(handle) { mutableFloatStateOf(1f) }
    LaunchedEffect(handle) {
        snapshotFlow { zoom }.debounce(250).distinctUntilChanged().collect { renderZoom = it }
    }

    // Search: one pass over all pages, then highlight every hit and scroll to the current one.
    var matches by remember(handle) { mutableStateOf<List<PdfMatch>>(emptyList()) }
    LaunchedEffect(handle, state.query) {
        val query = state.query
        if (query.isBlank()) {
            matches = emptyList()
            state.matchCount = 0
            return@LaunchedEffect
        }
        delay(300)
        matches = handle.search(query)
        state.matchCount = matches.size
        if (state.matchIndex >= matches.size) state.matchIndex = 0
    }
    val current = matches.getOrNull(state.matchIndex)

    // Page pill: the page under the top quarter of the screen (so a page you just jumped to counts).
    val centerPage by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val middle = info.viewportStartOffset + (info.viewportEndOffset - info.viewportStartOffset) / 4
            info.visibleItemsInfo.firstOrNull { it.offset <= middle && it.offset + it.size >= middle }?.index
                ?: listState.firstVisibleItemIndex
        }
    }
    LaunchedEffect(centerPage, handle) { state.pageLabel = "${centerPage + 1} / ${handle.pages.size}" }
    DisposableEffect(Unit) { onDispose { state.pageLabel = null } }
    LaunchedEffect(state.jumpToPage) {
        state.jumpToPage?.let { listState.scrollToItem(it.coerceIn(0, handle.pages.lastIndex)) }
        state.jumpToPage = null
    }

    BoxWithConstraints(modifier.clipToBounds().background(backdrop)) {
        val density = LocalDensity.current
        val pageGap = 8.dp
        val widthPx = constraints.maxWidth
        val viewportHeight = constraints.maxHeight
        val pageWidthPx = with(density) { (maxWidth - Spacing.sm * 2).roundToPx() }.coerceAtLeast(1)

        LaunchedEffect(current) {
            val match = current ?: return@LaunchedEffect
            val pageHeight = pageWidthPx / handle.pages[match.page].aspect
            val top = match.rects.minOfOrNull { it.top } ?: 0f
            listState.scrollToItem(match.page, (top * pageHeight - viewportHeight / 3f).toInt().coerceAtLeast(0))
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(pageGap),
            modifier = Modifier
                .fillMaxSize()
                .pinchToZoom(widthPx, { zoom }, { z, p -> zoom = z; panX = p }, { panX })
                .pointerInput(onTap) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tap ->
                            // Toggle 1x <-> 2.5x around the tapped point (horizontally; the list scroll handles vertical).
                            if (zoom > 1.01f) {
                                zoom = 1f; panX = 0f
                            } else {
                                zoom = 2.5f
                                panX = ((widthPx / 2f - tap.x) * (zoom - 1)).coerceIn(-maxPan(widthPx, zoom), maxPan(widthPx, zoom))
                            }
                        },
                    )
                }
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = panX
                },
        ) {
            itemsIndexed(handle.pages, key = { index, _ -> index }) { index, size ->
                PdfPage(
                    handle = handle,
                    index = index,
                    size = size,
                    baseWidthPx = pageWidthPx,
                    renderZoom = renderZoom,
                    cache = cache,
                    filter = filter,
                    placeholder = placeholder,
                    pageMatches = matches.filter { it.page == index },
                    current = current,
                    accent = accent,
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    handle: PdfDocumentHandle,
    index: Int,
    size: PageSize,
    baseWidthPx: Int,
    renderZoom: Float,
    cache: LruCache<Long, Bitmap>,
    filter: ColorFilter?,
    placeholder: Color,
    pageMatches: List<PdfMatch>,
    current: PdfMatch?,
    accent: Color,
) {
    val targetWidth = renderWidth(baseWidthPx, renderZoom, size)
    val cacheKey = index.toLong() shl 32 or targetWidth.toLong()
    // Keep showing the last bitmap (e.g. the 1x one) until a sharper one for the new zoom is ready.
    var bitmap by remember(handle, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(handle, index, targetWidth) {
        val cached = cache.get(cacheKey)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val rendered = runCatching { handle.render(index, targetWidth) }.getOrNull() ?: return@LaunchedEffect
        cache.put(cacheKey, rendered)
        bitmap = rendered
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(size.aspect)
            .background(placeholder)
            .semantics { contentDescription = "Page ${index + 1}" },
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, colorFilter = filter, modifier = Modifier.fillMaxSize())
        }
        if (pageMatches.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                pageMatches.forEach { match ->
                    val color = accent.copy(alpha = if (match === current) 0.45f else 0.2f)
                    match.rects.forEach { r ->
                        drawRect(color, Offset(r.left * this.size.width, r.top * this.size.height), Size(r.width() * this.size.width, r.height() * this.size.height))
                    }
                }
            }
        }
    }
}

/** Width to render a page at for [zoom], capped so one page bitmap stays under [MAX_RENDER_PIXELS]. */
private fun renderWidth(baseWidth: Int, zoom: Float, size: PageSize): Int {
    if (zoom < 1.2f) return baseWidth
    val wanted = (baseWidth * zoom).toInt().coerceAtMost(MAX_RENDER_WIDTH)
    val byPixels = sqrt(MAX_RENDER_PIXELS * size.aspect).toInt()
    // Round down to 256 px steps so small zoom changes reuse the same cached render.
    return (minOf(wanted, byPixels) / 256 * 256).coerceAtLeast(baseWidth)
}

private fun maxPan(widthPx: Int, zoom: Float) = widthPx * (zoom - 1) / 2f

/**
 * Two-finger pinch zooms the whole page column (around the pinch point horizontally) and one finger pans sideways
 * while zoomed. Watches the Initial pass so it sees touches before the list; it only consumes multi-touch, so
 * one-finger vertical scrolling stays with the list.
 */
private fun Modifier.pinchToZoom(widthPx: Int, zoom: () -> Float, onChange: (zoom: Float, panX: Float) -> Unit, panX: () -> Float) =
    pointerInput(widthPx) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val oldZoom = zoom()
                    val newZoom = (oldZoom * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                    val centroid = event.calculateCentroid(useCurrent = true)
                    // Keep the content under the pinch centre where it is: x' = c - (c - x) * new/old.
                    val center = widthPx / 2f
                    val anchored = (panX() - (centroid.x - center)) * (newZoom / oldZoom) + (centroid.x - center)
                    onChange(newZoom, anchored.coerceIn(-maxPan(widthPx, newZoom), maxPan(widthPx, newZoom)))
                    event.changes.forEach { it.consume() }
                } else if (pressed.size == 1 && zoom() > 1f) {
                    val dx = pressed[0].positionChange().x
                    onChange(zoom(), (panX() + dx).coerceIn(-maxPan(widthPx, zoom()), maxPan(widthPx, zoom())))
                }
            } while (event.changes.any { it.pressed })
        }
    }

/**
 * Paper themes for rendered pages: remaps white paper / black ink to the paper's own background and ink colours,
 * channel by channel (out = ink + (bg - ink) * in). Light paper needs no filter.
 */
private fun paperFilter(paper: Paper, dark: Boolean): ColorFilter? {
    val resolved = if (paper == Paper.Auto) (if (dark) Paper.Dark else Paper.Light) else paper
    if (resolved == Paper.Light) return null
    val (bg, ink) = resolved.colors(dark)
    val m = floatArrayOf(
        bg.red - ink.red, 0f, 0f, 0f, ink.red * 255,
        0f, bg.green - ink.green, 0f, 0f, ink.green * 255,
        0f, 0f, bg.blue - ink.blue, 0f, ink.blue * 255,
        0f, 0f, 0f, 1f, 0f,
    )
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

@Composable
private fun PasswordPrompt(wrong: Boolean, modifier: Modifier, onOpen: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    // imePadding keeps the field and Open button above the keyboard.
    Box(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(Spacing.lg))
            Text("This PDF is password protected", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                isError = wrong,
                supportingText = if (wrong) ({ Text("Wrong password") }) else null,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onOpen(password) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
            PillButton("Open", onClick = { onOpen(password) }, enabled = password.isNotEmpty())
        }
    }
}
