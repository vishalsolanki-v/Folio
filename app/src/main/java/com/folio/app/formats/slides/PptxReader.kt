package com.folio.app.formats.slides

import com.folio.app.core.files.LimitedStream
import com.folio.app.core.files.XNode
import com.folio.app.core.files.Xml
import java.io.Closeable
import java.io.IOException
import java.util.zip.ZipFile

/** Position and size in EMU (914,400 per inch, 12,700 per point). */
data class Frame(val x: Long, val y: Long, val w: Long, val h: Long)

enum class Align { Left, Center, Right, Justify }
enum class Anchor { Top, Middle, Bottom }

class Run(val text: String, val sizePt: Float, val bold: Boolean, val italic: Boolean, val underline: Boolean, val color: Int?)
class Para(val runs: List<Run>, val align: Align, val bullet: String?, val level: Int) {
    val text: String get() = runs.joinToString("") { it.text }
}

sealed interface SlideShape {
    /** Null when neither the slide nor its layout/master says where the shape goes ("unpositioned"). */
    val frame: Frame?
}

class TextBox(
    override val frame: Frame?,
    val paragraphs: List<Para>,
    val title: Boolean,
    val fill: Int?,
    val geometry: String?,
    val anchor: Anchor,
    /** left, top, right, bottom insets in EMU. */
    val insets: List<Long>,
) : SlideShape

class Picture(override val frame: Frame?, val media: String) : SlideShape

/** Tables, charts, diagrams: a labelled box; their text still reaches the outline and search. */
class ObjectBox(override val frame: Frame?, val label: String, val lines: List<String>) : SlideShape

class Slide(val number: Int, val background: Int?, val shapes: List<SlideShape>) {
    val title: String? get() = shapes.filterIsInstance<TextBox>().firstOrNull { it.title }?.paragraphs?.joinToString(" ") { it.text }?.trim()?.ifEmpty { null }

    /** All text on the slide, shape by shape (for the outline and search). */
    fun texts(): List<String> = shapes.map { s ->
        when (s) {
            is TextBox -> s.paragraphs.joinToString("\n") { it.text }
            is ObjectBox -> s.lines.joinToString("\n")
            is Picture -> ""
        }
    }
}

class Deck(val width: Long, val height: Long, val slides: List<Slide>)

/**
 * Reads PPTX with a small XML tree per part (plan M12): slide order, positions (incl. placeholders inherited from the
 * layout and master, and group transforms), text with sizes/bold/italic/colour/alignment/bullets inherited through
 * list styles and theme colours, pictures, and labelled boxes for tables/charts/diagrams.
 */
class PptxReader(private val zip: ZipFile) : Closeable {
    companion object {
        const val MAX_SLIDES = 2_000
        private const val MAX_MEDIA_BYTES = 50L * 1024 * 1024
        private const val DEFAULT_TITLE_PT = 32f
        private const val DEFAULT_BODY_PT = 18f
        private val DEFAULT_INSETS = listOf(91_440L, 45_720L, 91_440L, 45_720L)
        private const val BULLETS = "•◦▪▫■□○●–—-»›→➢➤✓✔*"
        private val NAMED = mapOf("black" to 0xFF000000.toInt(), "white" to 0xFFFFFFFF.toInt())
    }

    private val parts = HashMap<String, XNode?>()

    private fun part(path: String): XNode? = parts.getOrPut(path) {
        zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).use { Xml.parse(it) } }
    }

    /** Relationship id -> resolved part path, and type, for [path]'s .rels. */
    private fun rels(path: String): Map<String, Pair<String, String>> {
        val dir = path.substringBeforeLast('/', "")
        val relsPath = (if (dir.isEmpty()) "" else "$dir/") + "_rels/" + path.substringAfterLast('/') + ".rels"
        val root = part(relsPath) ?: return emptyMap()
        return root.all("Relationship").mapNotNull { r ->
            val id = r.attr("Id") ?: return@mapNotNull null
            val target = r.attr("Target") ?: return@mapNotNull null
            if (r.attr("TargetMode") == "External") return@mapNotNull null
            id to (resolve(dir, target) to r.attr("Type").orEmpty())
        }.toMap()
    }

    private fun relOfType(path: String, suffix: String) = rels(path).values.firstOrNull { it.second.endsWith(suffix) }?.first

    fun read(): Deck {
        val pres = part("ppt/presentation.xml") ?: throw IOException("Not a presentation")
        val size = pres.child("sldSz")
        val width = size?.attr("cx")?.toLongOrNull()?.takeIf { it > 0 } ?: 9_144_000L
        val height = size?.attr("cy")?.toLongOrNull()?.takeIf { it > 0 } ?: 6_858_000L
        val presRels = rels("ppt/presentation.xml")
        val paths = pres.child("sldIdLst")?.all("sldId").orEmpty().mapNotNull { presRels[it.nsAttr("id")]?.first }.take(MAX_SLIDES)
        val slides = paths.mapIndexed { i, path -> readSlide(i + 1, path) }
        return Deck(width, height, slides)
    }

    fun media(path: String): ByteArray {
        val entry = zip.getEntry(path) ?: throw IOException("Missing $path")
        return LimitedStream(zip.getInputStream(entry), MAX_MEDIA_BYTES).use { it.readBytes() }
    }

    override fun close() = zip.close()

    // --- one slide ---

    private class Context(
        val slidePath: String,
        val slideRels: Map<String, Pair<String, String>>,
        val layout: XNode?,
        val master: XNode?,
        val colors: Map<String, Int>,
    )

    private fun readSlide(number: Int, path: String): Slide {
        val slide = part(path) ?: return Slide(number, null, emptyList())
        val layoutPath = relOfType(path, "/slideLayout")
        val layout = layoutPath?.let(::part)
        val masterPath = layoutPath?.let { relOfType(it, "/slideMaster") }
        val master = masterPath?.let(::part)
        val colors = masterPath?.let { themeColors(it, master) }.orEmpty()
        val ctx = Context(path, rels(path), layout, master, colors)
        val background = listOfNotNull(slide, layout, master).firstNotNullOfOrNull { background(it, ctx) }
        val shapes = ArrayList<SlideShape>()
        slide.path("cSld", "spTree")?.let { tree -> collect(tree, Transform.IDENTITY, ctx, shapes) }
        return Slide(number, background, shapes)
    }

    private fun background(root: XNode, ctx: Context): Int? {
        val bg = root.path("cSld", "bg") ?: return null
        bg.child("bgPr")?.let { return color(it.child("solidFill"), ctx) }
        return bg.child("bgRef")?.let { color(it, ctx) }
    }

    private fun collect(tree: XNode, transform: Transform, ctx: Context, out: MutableList<SlideShape>) {
        for (node in tree.children) {
            when (node.name) {
                "sp" -> textBox(node, transform, ctx)?.let(out::add)
                "pic" -> picture(node, transform, ctx)?.let(out::add)
                "graphicFrame" -> out += objectBox(node, transform)
                "grpSp" -> collect(node, transform.then(Transform.ofGroup(node.path("grpSpPr", "xfrm"))), ctx, out)
                // Newer content wrapped for older readers: use the fallback (usually a picture), else the choice.
                "AlternateContent" -> (node.child("Fallback") ?: node.child("Choice"))?.let { collect(it, transform, ctx, out) }
            }
        }
    }

    private fun picture(node: XNode, transform: Transform, ctx: Context): Picture? {
        val embed = node.path("blipFill", "blip")?.nsAttr("embed") ?: return null
        val media = ctx.slideRels[embed]?.first ?: return null
        val frame = frameOf(node.path("spPr", "xfrm")) ?: placeholder(node, ctx)?.let { frameOf(it.path("spPr", "xfrm")) }
        return Picture(frame?.let(transform::apply), media)
    }

    private fun objectBox(node: XNode, transform: Transform): ObjectBox {
        val frame = frameOf(node.child("xfrm"))?.let(transform::apply)
        val data = node.path("graphic", "graphicData")
        val uri = data?.attr("uri").orEmpty()
        val table = data?.child("tbl")
        return when {
            table != null -> ObjectBox(frame, "Table", table.all("tr").map { row ->
                row.all("tc").joinToString(" | ") { cell -> cell.descendants("t").joinToString("") { it.text } }
            })
            "chart" in uri -> ObjectBox(frame, "Chart", emptyList())
            "diagram" in uri -> ObjectBox(frame, "Diagram", node.descendants("t").map { it.text }.toList())
            else -> ObjectBox(frame, "Object", emptyList())
        }
    }

    private fun textBox(node: XNode, transform: Transform, ctx: Context): TextBox? {
        val body = node.child("txBody")
        val fill = node.child("spPr")?.let { spPr -> if (spPr.child("noFill") != null) null else color(spPr.child("solidFill"), ctx) }
        val geometry = node.path("spPr", "prstGeom")?.attr("prst")
        if (body == null && fill == null) return null

        val ph = node.path("nvSpPr", "nvPr", "ph")
        val type = ph?.let { it.attr("type") ?: "body" }
        val title = type == "title" || type == "ctrTitle"
        val layoutShape = ph?.let { findPlaceholder(ctx.layout, it) }
        val masterShape = ph?.let { findPlaceholder(ctx.master, it) }
        val frame = listOfNotNull(node, layoutShape, masterShape).firstNotNullOfOrNull { frameOf(it.path("spPr", "xfrm")) }

        // Paragraph/run defaults, nearest first: this shape, its layout and master placeholders, the master's text styles.
        val masterStyle = ctx.master?.path("txStyles", when {
            title -> "titleStyle"
            ph != null && type != "dt" && type != "ftr" && type != "sldNum" -> "bodyStyle"
            else -> "otherStyle"
        })
        val styles = listOfNotNull(body?.child("lstStyle"), layoutShape?.path("txBody", "lstStyle"), masterShape?.path("txBody", "lstStyle"), masterStyle)
        val bodyPrs = listOfNotNull(body?.child("bodyPr"), layoutShape?.path("txBody", "bodyPr"), masterShape?.path("txBody", "bodyPr"))
        val fontScale = body?.path("bodyPr", "normAutofit")?.attr("fontScale")?.toFloatOrNull()?.div(100_000f) ?: 1f
        val anchor = when (bodyPrs.firstNotNullOfOrNull { it.attr("anchor") }) {
            "ctr" -> Anchor.Middle
            "b" -> Anchor.Bottom
            else -> Anchor.Top
        }
        val insets = listOf("lIns", "tIns", "rIns", "bIns").mapIndexed { i, a ->
            bodyPrs.firstNotNullOfOrNull { it.attr(a)?.toLongOrNull() } ?: DEFAULT_INSETS[i]
        }

        val counters = IntArray(10)
        val paragraphs = body?.all("p").orEmpty().map { p ->
            val pPr = p.child("pPr")
            val level = pPr?.attr("lvl")?.toIntOrNull()?.coerceIn(0, 8) ?: 0
            fun <T> inherited(get: (XNode) -> T?): T? =
                pPr?.let(get) ?: styles.firstNotNullOfOrNull { s -> s.child("lvl${level + 1}pPr")?.let(get) }
            val align = when (inherited { it.attr("algn") }) {
                "ctr" -> Align.Center
                "r" -> Align.Right
                "just", "dist" -> Align.Justify
                else -> Align.Left
            }
            val bulletNode = inherited { n -> n.child("buNone") ?: n.child("buChar") ?: n.child("buAutoNum") }
            val runs = p.children.mapNotNull { child ->
                when (child.name) {
                    "r", "fld" -> run(child.path("t")?.text.orEmpty(), child.child("rPr"), level, title, fontScale, styles, ctx)
                    "br" -> run("\n", child.child("rPr"), level, title, fontScale, styles, ctx)
                    else -> null
                }
            }
            val hasText = runs.any { it.text.isNotBlank() }
            val bullet = when {
                !hasText || bulletNode == null -> null
                // Symbol-font bullets (Wingdings "§", "Ø", ...) would show as letters; keep only real bullet glyphs.
                bulletNode.name == "buChar" -> bulletNode.attr("char")?.takeIf { it.length == 1 && it[0] in BULLETS } ?: "•"
                bulletNode.name == "buAutoNum" -> "${++counters[level]}."
                else -> null
            }
            if (bulletNode?.name != "buAutoNum") counters[level] = 0
            val endSize = (p.child("endParaRPr")?.attr("sz")?.toFloatOrNull()?.div(100f) ?: defaultSize(level, title, styles)) * fontScale
            Para(runs.ifEmpty { listOf(Run("", endSize, false, false, false, null)) }, align, bullet, level)
        }
        if (paragraphs.none { it.text.isNotBlank() } && fill == null) return null
        return TextBox(frame?.let(transform::apply), paragraphs, title, fill, geometry, anchor, insets)
    }

    private fun run(text: String, rPr: XNode?, level: Int, title: Boolean, fontScale: Float, styles: List<XNode>, ctx: Context): Run {
        fun <T> inherited(get: (XNode) -> T?): T? =
            rPr?.let(get) ?: styles.firstNotNullOfOrNull { s -> s.path("lvl${level + 1}pPr", "defRPr")?.let(get) }
        val size = (rPr?.attr("sz")?.toFloatOrNull()?.div(100f) ?: defaultSize(level, title, styles)) * fontScale
        val bold = inherited { it.attr("b") }.let { it == "1" || it == "true" }
        val italic = inherited { it.attr("i") }.let { it == "1" || it == "true" }
        val underline = inherited { it.attr("u") }.let { it != null && it != "none" }
        val color = inherited { n -> n.child("solidFill")?.let { color(it, ctx) } }
        return Run(text, size, bold, italic, underline, color)
    }

    private fun defaultSize(level: Int, title: Boolean, styles: List<XNode>): Float =
        styles.firstNotNullOfOrNull { it.path("lvl${level + 1}pPr", "defRPr")?.attr("sz")?.toFloatOrNull()?.div(100f) }
            ?: if (title) DEFAULT_TITLE_PT else DEFAULT_BODY_PT

    /** The layout/master shape for a slide placeholder: same idx first, then same type (title types match each other). */
    private fun findPlaceholder(root: XNode?, ph: XNode): XNode? {
        val tree = root?.path("cSld", "spTree") ?: return null
        val candidates = tree.descendants("sp").mapNotNull { sp -> sp.path("nvSpPr", "nvPr", "ph")?.let { sp to it } }.toList()
        val idx = ph.attr("idx")
        val type = normalType(ph.attr("type"))
        return candidates.firstOrNull { (_, p) -> idx != null && p.attr("idx") == idx && normalType(p.attr("type")) == type }?.first
            ?: candidates.firstOrNull { (_, p) -> normalType(p.attr("type")) == type }?.first
            ?: candidates.firstOrNull { (_, p) -> idx != null && p.attr("idx") == idx }?.first
    }

    private fun normalType(type: String?) = when (type) {
        null, "obj", "body" -> "body"
        "ctrTitle", "title" -> "title"
        else -> type
    }

    private fun placeholder(node: XNode, ctx: Context): XNode? {
        val ph = node.path("nvPicPr", "nvPr", "ph") ?: return null
        return findPlaceholder(ctx.layout, ph) ?: findPlaceholder(ctx.master, ph)
    }

    private fun frameOf(xfrm: XNode?): Frame? {
        val off = xfrm?.child("off") ?: return null
        val ext = xfrm.child("ext") ?: return null
        return Frame(
            off.attr("x")?.toLongOrNull() ?: 0, off.attr("y")?.toLongOrNull() ?: 0,
            ext.attr("cx")?.toLongOrNull() ?: 0, ext.attr("cy")?.toLongOrNull() ?: 0,
        )
    }

    // --- colours ---

    /** Theme colours by scheme name, plus the master's mapping (bg1 -> lt1, tx1 -> dk1, ...). */
    private fun themeColors(masterPath: String, master: XNode?): Map<String, Int> {
        val theme = relOfType(masterPath, "/theme")?.let(::part) ?: return emptyMap()
        val scheme = theme.descendants("clrScheme").firstOrNull() ?: return emptyMap()
        val base = scheme.children.mapNotNull { c ->
            val value = c.child("srgbClr")?.attr("val") ?: c.child("sysClr")?.attr("lastClr")
            value?.let { hex(it) }?.let { c.name to it }
        }.toMap()
        val map = master?.child("clrMap")?.attrs?.associate { it.first to it.second }
            ?: mapOf("bg1" to "lt1", "tx1" to "dk1", "bg2" to "lt2", "tx2" to "dk2")
        return base + map.mapNotNull { (alias, target) -> base[target]?.let { alias to it } }
    }

    /** Colour of a fill-like node: srgbClr, schemeClr, sysClr or a basic preset. Modifiers (lumMod, tint) are ignored. */
    private fun color(node: XNode?, ctx: Context): Int? {
        node ?: return null
        node.child("srgbClr")?.attr("val")?.let { return hex(it) }
        node.child("schemeClr")?.attr("val")?.let { return ctx.colors[it] }
        node.child("sysClr")?.attr("lastClr")?.let { return hex(it) }
        node.child("prstClr")?.attr("val")?.let { return NAMED[it] }
        return null
    }

    private fun hex(value: String): Int? = value.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
}

/** x' = sx * x + dx (EMU), for group shapes' child coordinate spaces. */
internal data class Transform(val sx: Double, val sy: Double, val dx: Double, val dy: Double) {
    fun apply(f: Frame) = Frame((sx * f.x + dx).toLong(), (sy * f.y + dy).toLong(), (sx * f.w).toLong(), (sy * f.h).toLong())

    /** This transform applied after [inner]. */
    fun then(inner: Transform) = Transform(sx * inner.sx, sy * inner.sy, sx * inner.dx + dx, sy * inner.dy + dy)

    companion object {
        val IDENTITY = Transform(1.0, 1.0, 0.0, 0.0)

        fun ofGroup(xfrm: XNode?): Transform {
            xfrm ?: return IDENTITY
            fun l(child: String, a: String) = xfrm.child(child)?.attr(a)?.toDoubleOrNull()
            val offX = l("off", "x") ?: return IDENTITY
            val offY = l("off", "y") ?: return IDENTITY
            val sx = (l("ext", "cx") ?: 1.0) / (l("chExt", "cx")?.takeIf { it != 0.0 } ?: l("ext", "cx") ?: 1.0)
            val sy = (l("ext", "cy") ?: 1.0) / (l("chExt", "cy")?.takeIf { it != 0.0 } ?: l("ext", "cy") ?: 1.0)
            return Transform(sx, sy, offX - sx * (l("chOff", "x") ?: offX), offY - sy * (l("chOff", "y") ?: offY))
        }
    }
}

/** "ppt/slides" + "../media/image1.png" -> "ppt/media/image1.png"; "/ppt/x" is absolute. */
internal fun resolve(dir: String, target: String): String {
    val parts = if (target.startsWith("/")) ArrayList() else dir.split('/').filter { it.isNotEmpty() }.toMutableList()
    for (seg in target.removePrefix("/").split('/')) {
        when (seg) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += seg
        }
    }
    return parts.joinToString("/")
}
