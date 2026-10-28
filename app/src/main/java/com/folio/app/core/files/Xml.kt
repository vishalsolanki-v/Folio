package com.folio.app.core.files

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** A minimal XML element: local name (prefix dropped), attributes as written, children, and its own text. */
class XNode(val name: String, val attrs: List<Pair<String, String>>) {
    val children = ArrayList<XNode>(4)
    var text: String = ""
        internal set

    /** Attribute by exact name (e.g. "sz", "r:id"). */
    fun attr(name: String): String? = attrs.firstOrNull { it.first == name }?.second

    /** A namespaced attribute by local name, whatever its prefix (e.g. "embed" finds r:embed). */
    fun nsAttr(local: String): String? = attrs.firstOrNull { ':' in it.first && it.first.substringAfter(':') == local }?.second

    fun child(name: String): XNode? = children.firstOrNull { it.name == name }

    fun all(name: String): List<XNode> = children.filter { it.name == name }

    /** Follows child names, e.g. path("spPr", "xfrm", "off"). */
    fun path(vararg names: String): XNode? = names.fold(this as XNode?) { node, n -> node?.child(n) }

    /** Every descendant (depth-first, document order) with this name. */
    fun descendants(name: String): Sequence<XNode> = sequence {
        for (c in children) {
            if (c.name == name) yield(c)
            yieldAll(c.descendants(name))
        }
    }
}

object Xml {
    /**
     * Parses a whole part into [XNode]s. No DOCTYPE processing (no entity expansion), and at most [limit] bytes
     * are read (zip-bomb guard, plan §4.10).
     */
    fun parse(input: InputStream, limit: Long = 50L * 1024 * 1024): XNode {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        // Off by default; some parsers (kxml2 on the JVM) reject setting it at all.
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        parser.setInput(LimitedStream(input, limit), null)
        val stack = ArrayList<XNode>()
        var root: XNode? = null
        var text: StringBuilder? = null
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    stack.lastOrNull()?.let { parent -> text?.let { if (parent.text.isEmpty()) parent.text = it.toString() } }
                    text = null
                    val node = XNode(parser.name.substringAfter(':'), List(parser.attributeCount) { parser.getAttributeName(it) to parser.getAttributeValue(it) })
                    stack.lastOrNull()?.children?.add(node) ?: run { root = node }
                    stack.add(node)
                }
                XmlPullParser.TEXT -> (text ?: StringBuilder().also { text = it }).append(parser.text)
                XmlPullParser.END_TAG -> {
                    val node = stack.removeAt(stack.lastIndex)
                    if (node.children.isEmpty()) text?.let { node.text = it.toString() }
                    text = null
                }
                XmlPullParser.END_DOCUMENT -> return root ?: throw IOException("Empty XML")
            }
        }
    }
}

/** Throws once more than [limit] bytes have been read, so a hostile (zip-bomb) part can't be read forever. */
class LimitedStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var count = 0L
    override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
    private fun bump(n: Long) {
        count += n
        if (count > limit) throw IOException("Part too large")
    }
}
