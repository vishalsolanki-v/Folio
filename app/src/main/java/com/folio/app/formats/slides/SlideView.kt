package com.folio.app.formats.slides

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.folio.app.appContainer
import com.folio.app.core.designsystem.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val EMU_PER_PT = 12_700f

/** Loads slide media bytes (pictures) by part path. */
fun interface MediaSource {
    suspend fun bytes(path: String): ByteArray?
}

/**
 * One slide drawn to scale (plan M12): shapes placed by their EMU frames, text sized from points (ignoring the
 * system font scale, like a picture of the slide), pictures from the file, boxes for tables/charts. Shapes with no
 * known position are stacked from the top.
 */
@Composable
fun SlideView(deck: Deck, slide: Slide, media: MediaSource, highlight: Int?, modifier: Modifier = Modifier) {
    val background = slide.background?.let(::Color) ?: Color.White
    val defaultInk = if (background.luminance() < 0.4f) Color(0xFFF2F2F2) else Color(0xFF1A1A1A)
    val accent = MaterialTheme.colorScheme.tertiary
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        val scale = constraints.maxWidth / deck.width.toFloat() // px per EMU
        val density = LocalDensity.current
        slide.shapes.forEachIndexed { i, shape ->
            val f = shape.frame ?: return@forEachIndexed
            Box(
                Modifier
                    .offset { IntOffset((f.x * scale).roundToInt(), (f.y * scale).roundToInt()) }
                    .requiredSize(with(density) { (f.w * scale).toDp() }, with(density) { (f.h * scale).toDp() })
                    .then(if (i == highlight) Modifier.border(2.dp, accent) else Modifier),
            ) {
                ShapeContent(shape, scale, media, defaultInk)
            }
        }
        val loose = slide.shapes.withIndex().filter { it.value.frame == null }
        if (loose.isNotEmpty()) {
            Column(Modifier.fillMaxSize().padding(horizontal = maxWidth * 0.06f, vertical = maxHeight * 0.06f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                loose.forEach { (i, shape) ->
                    Box(Modifier.fillMaxWidth().then(if (i == highlight) Modifier.border(2.dp, accent) else Modifier)) {
                        ShapeContent(shape, scale, media, defaultInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShapeContent(shape: SlideShape, scale: Float, media: MediaSource, defaultInk: Color) {
    when (shape) {
        is TextBox -> TextShape(shape, scale, defaultInk)
        is Picture -> SlidePicture(shape.media, media, Modifier.fillMaxSize())
        is ObjectBox -> Box(
            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(defaultInk.copy(alpha = 0.06f)).padding(Spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            // Sized in points like the slide's own text, so it scales with the slide.
            val density = LocalDensity.current
            fun pt(points: Float) = with(density) { (points * EMU_PER_PT * scale).toSp() }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(shape.label, fontSize = pt(14f), lineHeight = pt(17f), color = defaultInk.copy(alpha = 0.7f))
                shape.lines.take(8).forEach { Text(it, fontSize = pt(12f), lineHeight = pt(15f), color = defaultInk.copy(alpha = 0.8f), maxLines = 1) }
            }
        }
    }
}

@Composable
private fun TextShape(box: TextBox, scale: Float, defaultInk: Color) {
    val density = LocalDensity.current
    val family = MaterialTheme.typography.bodyLarge.fontFamily
    fun pt(points: Float) = with(density) { (points * EMU_PER_PT * scale).toSp() }
    val shape: Shape = when (box.geometry) {
        "ellipse" -> RoundedCornerShape(percent = 50)
        "roundRect" -> RoundedCornerShape(percent = 12)
        else -> RectangleShape
    }
    val (l, t, r, b) = box.insets.map { with(density) { (it * scale).toDp() } }
    Box(
        Modifier.fillMaxSize().then(box.fill?.let { Modifier.clip(shape).background(Color(it)) } ?: Modifier).padding(l, t, r, b),
    ) {
        // Text can run past its box, as in PowerPoint; the box only sets width and anchor.
        Column(
            Modifier.fillMaxWidth().wrapContentHeight(
                align = when (box.anchor) {
                    Anchor.Top -> Alignment.Top
                    Anchor.Middle -> Alignment.CenterVertically
                    Anchor.Bottom -> Alignment.Bottom
                },
                unbounded = true,
            ),
        ) {
            box.paragraphs.forEach { p ->
                val size = p.runs.maxOfOrNull { it.sizePt } ?: 18f
                Text(
                    paragraphText(p, ::pt, defaultInk),
                    fontFamily = family,
                    fontSize = pt(size),
                    lineHeight = pt(size * 1.2f),
                    color = defaultInk,
                    textAlign = when (p.align) {
                        Align.Left -> TextAlign.Start
                        Align.Center -> TextAlign.Center
                        Align.Right -> TextAlign.End
                        Align.Justify -> TextAlign.Justify
                    },
                    modifier = Modifier.fillMaxWidth().padding(start = with(density) { (p.level * 0.4f * 914_400 * scale).toDp() }),
                )
            }
        }
    }
}

private fun paragraphText(p: Para, pt: (Float) -> androidx.compose.ui.unit.TextUnit, defaultInk: Color): AnnotatedString = buildAnnotatedString {
    p.bullet?.let { append("$it  ") }
    p.runs.forEach { run ->
        withStyle(
            SpanStyle(
                color = run.color?.let(::Color) ?: defaultInk,
                fontSize = pt(run.sizePt),
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                textDecoration = if (run.underline) TextDecoration.Underline else null,
            ),
        ) { append(run.text) }
    }
}

/** A picture from the presentation file, decoded by the app's Coil loader at the size it is shown. */
@Composable
fun SlidePicture(path: String, media: MediaSource, modifier: Modifier, contentScale: ContentScale = ContentScale.FillBounds) {
    val context = LocalContext.current
    val bytes by produceState<ByteArray?>(null, path) { value = withContext(Dispatchers.IO) { runCatching { media.bytes(path) }.getOrNull() } }
    bytes?.let {
        AsyncImage(
            model = ImageRequest.Builder(context).data(it).build(),
            contentDescription = null,
            imageLoader = appContainer().imageLoader,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
