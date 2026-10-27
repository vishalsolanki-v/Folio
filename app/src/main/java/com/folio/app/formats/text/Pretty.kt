package com.folio.app.formats.text

import android.util.Xml
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.StringWriter

/** "Pretty view" for JSON and XML: re-indents for reading only, never saved (plan M7). */
object Pretty {
    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun supports(extension: String, mime: String): Boolean = isJson(extension, mime) || isXml(extension, mime)

    /** Null when the text doesn't parse; the viewer then keeps the original. */
    fun format(text: String, extension: String, mime: String): String? = runCatching {
        when {
            isJson(extension, mime) -> json.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(text))
            isXml(extension, mime) -> xml(text)
            else -> null
        }
    }.getOrNull()

    private fun isJson(ext: String, mime: String) = ext in setOf("json", "geojson") || mime == "application/json"
    private fun isXml(ext: String, mime: String) = ext in setOf("xml", "plist") || mime == "application/xml"

    private fun xml(text: String): String {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(StringReader(text))
        }
        val out = StringWriter()
        val writer = Xml.newSerializer().apply {
            setOutput(out)
            setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        }
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    writer.startTag(null, parser.name)
                    for (i in 0 until parser.attributeCount) writer.attribute(null, parser.getAttributeName(i), parser.getAttributeValue(i))
                }
                XmlPullParser.END_TAG -> writer.endTag(null, parser.name)
                XmlPullParser.TEXT, XmlPullParser.ENTITY_REF -> parser.text.trim().takeIf { it.isNotEmpty() }?.let(writer::text)
                XmlPullParser.CDSECT -> writer.cdsect(parser.text)
                XmlPullParser.COMMENT -> writer.comment(parser.text)
                XmlPullParser.PROCESSING_INSTRUCTION -> writer.processingInstruction(parser.text)
                else -> Unit // DOCDECL and ignorable whitespace are dropped
            }
        }
        writer.endDocument()
        return out.toString()
    }
}
