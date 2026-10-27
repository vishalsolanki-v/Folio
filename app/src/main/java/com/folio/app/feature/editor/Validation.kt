package com.folio.app.feature.editor

import android.util.Xml
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader

/** Checks JSON and XML before saving (plan §2.4). Null = fine or not checked (YAML, TOML in v1.0). */
object Validation {
    fun check(text: String, extension: String, mime: String): String? = when {
        extension in setOf("json", "geojson") || mime == "application/json" -> json(text)
        extension in setOf("xml", "plist", "svg") || mime == "application/xml" -> xml(text)
        else -> null
    }

    private fun json(text: String): String? = try {
        Json.parseToJsonElement(text)
        null
    } catch (e: SerializationException) {
        val offset = Regex("offset (\\d+)").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        val line = offset?.let { o -> text.take(o).count { it == '\n' } + 1 }
        val reason = e.message.orEmpty().lineSequence().firstOrNull().orEmpty().substringAfter(": ", "").substringBefore(" at path:").ifBlank { "it isn't valid JSON" }
        if (line != null) "This JSON has an error on line $line: $reason" else "This JSON has an error: $reason"
    }

    private fun xml(text: String): String? = try {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(StringReader(text))
        }
        while (parser.next() != XmlPullParser.END_DOCUMENT) Unit
        null
    } catch (e: XmlPullParserException) {
        "This XML has an error on line ${e.lineNumber}: ${e.message.orEmpty().substringBefore(" (position").substringBefore("@")}"
    }
}
