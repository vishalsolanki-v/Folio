package com.folio.app.formats.slides

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.files.RandomAccess
import com.folio.app.core.model.FileFamily
import com.folio.app.core.model.OpenedFile
import com.folio.app.core.viewer.FormatViewer
import com.folio.app.core.viewer.SimplifiedNote
import com.folio.app.core.viewer.ViewerError
import com.folio.app.core.viewer.ViewerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile

/** PowerPoint (plan M12): slides one by one in a pager, or an Outline of all their text. View only. */
object SlidesViewer : FormatViewer {
    const val OUTLINE = "Outline view"

    override val families = setOf(FileFamily.SLIDES)
    override val supportsSearch = true
    override val supportsPaper = false

    override fun toggles(file: OpenedFile) = listOf(OUTLINE)

    @Composable
    override fun Content(file: OpenedFile, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
        val context = LocalContext.current
        val opened by produceState<Result<Pair<PptxReader, Deck>>?>(null, file.uri) {
            value = runCatching {
                withContext(Dispatchers.IO) {
                    val reader = PptxReader(ZipFile(RandomAccess.file(context, file.uri, "pptx")))
                    try {
                        reader to reader.read().also { require(it.slides.isNotEmpty()) }
                    } catch (e: Throwable) {
                        reader.close()
                        throw e
                    }
                }
            }
        }
        val result = opened
        when {
            result == null -> LoadingSkeleton(modifier)
            result.isFailure -> ViewerError(
                if (result.exceptionOrNull() is OutOfMemoryError) "This file is too large to preview on this phone."
                else "This file looks damaged, so Folio can't show it.",
                modifier,
            )
            else -> {
                val (reader, deck) = result.getOrThrow()
                DisposableEffect(reader) { onDispose { reader.close() } }
                LaunchedEffect(deck) { state.infoExtras = mapOf("Slides" to deck.slides.size.toString()) }
                Deck(deck, { reader.media(it) }, state, onTap, modifier)
            }
        }
    }
}

/** One search hit: the slide and the shape whose text contains the query. */
private data class Hit(val slide: Int, val shape: Int)

@Composable
private fun Deck(deck: Deck, media: MediaSource, state: ViewerState, onTap: () -> Unit, modifier: Modifier) {
    val outline = state.toggles[SlidesViewer.OUTLINE] == true
    val pager = rememberPagerState { deck.slides.size }
    val list = rememberLazyListState()
    val current = if (outline) list.firstVisibleItemIndex else pager.currentPage

    LaunchedEffect(current, deck) { state.pageLabel = "${current + 1} / ${deck.slides.size}" }
    DisposableEffect(Unit) { onDispose { state.pageLabel = null } }
    LaunchedEffect(state.jumpToPage) {
        val page = state.jumpToPage ?: return@LaunchedEffect
        if (outline) list.scrollToItem(page) else pager.scrollToPage(page)
        state.jumpToPage = null
    }
    // Switching views keeps the same slide, or opens the one tapped in the outline.
    var opening by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(outline) {
        if (outline) list.scrollToItem(pager.currentPage) else pager.scrollToPage(opening ?: list.firstVisibleItemIndex)
        opening = null
    }

    var hits by remember(deck) { mutableStateOf<List<Hit>>(emptyList()) }
    LaunchedEffect(deck, state.query) {
        val query = state.query
        if (query.isBlank()) {
            hits = emptyList()
            state.matchCount = 0
            return@LaunchedEffect
        }
        delay(250)
        hits = buildList {
            deck.slides.forEachIndexed { s, slide ->
                slide.texts().forEachIndexed { i, text ->
                    var from = text.indexOf(query, ignoreCase = true)
                    while (from >= 0) {
                        add(Hit(s, i))
                        from = text.indexOf(query, from + query.length, ignoreCase = true)
                    }
                }
            }
        }
        state.matchCount = hits.size
        if (state.matchIndex >= hits.size) state.matchIndex = 0
    }
    val hit = hits.getOrNull(state.matchIndex)
    LaunchedEffect(hit, outline) {
        hit ?: return@LaunchedEffect
        if (outline) list.animateScrollToItem(hit.slide) else pager.animateScrollToPage(hit.slide)
    }

    val backdrop = if (LocalFolioDark.current) Color(0xFF0F0F0E) else Color(0xFFF3F1EC)
    Column(modifier.background(backdrop)) {
        SimplifiedNote(
            "Simplified preview",
            "Folio shows a simplified preview of slides. Animations, some shapes, charts and special fonts may look " +
                "different. To see the exact slides, use Open with another app.",
        )
        if (outline) {
            Outline(deck, media, list, hit, onOpen = { slide ->
                opening = slide
                state.toggles[SlidesViewer.OUTLINE] = false
            }, Modifier.weight(1f))
        } else {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                pageSpacing = Spacing.lg,
                modifier = Modifier.weight(1f).navigationBarsPadding().pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) },
            ) { index ->
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Fit the slide inside the page whichever side limits it (portrait phone vs landscape).
                    val ratio = deck.width.toFloat() / deck.height
                    val byWidth = maxWidth / ratio <= maxHeight
                    SlideView(
                        deck,
                        deck.slides[index],
                        media,
                        highlight = hit?.takeIf { it.slide == index }?.shape,
                        modifier = (if (byWidth) Modifier.fillMaxWidth() else Modifier.height(maxHeight))
                            .aspectRatio(ratio, matchHeightConstraintsFirst = !byWidth)
                            .semantics { contentDescription = "Slide ${index + 1}: " + deck.slides[index].texts().joinToString(". ").take(500) },
                    )
                }
            }
        }
    }
}

/** Every slide's text in reading order, with small thumbnails of its pictures. Tap a slide to open it. */
@Composable
private fun Outline(deck: Deck, media: MediaSource, list: androidx.compose.foundation.lazy.LazyListState, hit: Hit?, onOpen: (Int) -> Unit, modifier: Modifier) {
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier,
    ) {
        itemsIndexed(deck.slides) { index, slide ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    if (hit?.slide == index) 2.dp else 1.dp,
                    if (hit?.slide == index) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth().widthIn(max = Spacing.contentMaxWidth).clickable { onOpen(index) },
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Slide ${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    slide.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    slide.shapes.forEach { shape ->
                        when (shape) {
                            is TextBox -> if (!shape.title) shape.paragraphs.filter { it.text.isNotBlank() }.forEach { p ->
                                Text(
                                    (p.bullet?.let { "$it  " } ?: "") + p.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = (p.level * 16).dp),
                                )
                            }
                            is ObjectBox -> (listOf(shape.label) + shape.lines).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            is Picture -> Unit
                        }
                    }
                    val pictures = slide.shapes.filterIsInstance<Picture>().take(4)
                    if (pictures.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
                            pictures.forEach {
                                Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    SlidePicture(it.media, media, Modifier.fillMaxSize(), ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
