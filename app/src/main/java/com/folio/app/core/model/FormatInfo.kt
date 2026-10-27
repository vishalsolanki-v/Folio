package com.folio.app.core.model

import com.folio.app.core.designsystem.BadgeTint
import com.folio.app.core.model.FileFamily.ARCHIVE
import com.folio.app.core.model.FileFamily.AUDIO
import com.folio.app.core.model.FileFamily.CODE
import com.folio.app.core.model.FileFamily.HTML
import com.folio.app.core.model.FileFamily.IMAGE
import com.folio.app.core.model.FileFamily.MARKDOWN
import com.folio.app.core.model.FileFamily.PDF
import com.folio.app.core.model.FileFamily.SHEET
import com.folio.app.core.model.FileFamily.SLIDES
import com.folio.app.core.model.FileFamily.TEXT
import com.folio.app.core.model.FileFamily.UNKNOWN
import com.folio.app.core.model.FileFamily.VIDEO
import com.folio.app.core.model.FileFamily.WORD

/** How Folio describes one file type: MIME, viewer family, friendly name, badge. */
data class Format(val mime: String, val family: FileFamily, val friendlyName: String, val badge: String, val tint: BadgeTint)

/** The single extension/MIME table (plan §4.4). Formats planned for v1.1 map to UNKNOWN but keep a good name. */
object FormatInfo {
    /** MIME types that say nothing specific; a known extension beats them. */
    val GENERIC_MIMES = setOf("text/plain", "application/octet-stream", "*/*", "application/x-unknown")

    private val byExt = HashMap<String, Format>()
    private val byMime = HashMap<String, Format>()

    private fun add(mime: String, family: FileFamily, name: String, tint: BadgeTint, vararg exts: String) {
        if (exts.isEmpty()) byMime.putIfAbsent(mime, Format(mime, family, name, "FILE", tint))
        exts.forEach { ext ->
            val format = Format(mime, family, name, ext.uppercase().take(4), tint)
            byExt[ext] = format
            byMime.putIfAbsent(mime, format)
        }
    }

    private fun code(name: String, mime: String, vararg exts: String) = add(mime, CODE, name, BadgeTint.Code, *exts)

    init {
        add("application/pdf", PDF, "PDF document", BadgeTint.Pdf, "pdf")
        add("application/vnd.openxmlformats-officedocument.wordprocessingml.document", WORD, "Word document", BadgeTint.Word, "docx")
        add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", SHEET, "Excel spreadsheet", BadgeTint.Sheet, "xlsx")
        add("text/csv", SHEET, "CSV file", BadgeTint.Sheet, "csv")
        add("text/tab-separated-values", SHEET, "TSV file", BadgeTint.Sheet, "tsv")
        add("application/vnd.openxmlformats-officedocument.presentationml.presentation", SLIDES, "PowerPoint presentation", BadgeTint.Slides, "pptx")

        add("text/plain", TEXT, "Text file", BadgeTint.Text, "txt", "text", "nfo", "rst", "adoc", "tex", "srt", "vtt")
        add("text/x-log", TEXT, "Log file", BadgeTint.Text, "log")
        add("text/markdown", MARKDOWN, "Markdown file", BadgeTint.Text, "md", "markdown")
        add("text/html", HTML, "Web page", BadgeTint.Code, "html", "htm")
        add("application/xhtml+xml", HTML, "Web page", BadgeTint.Code, "xhtml")

        code("JSON file", "application/json", "json", "jsonc", "geojson")
        code("XML file", "application/xml", "xml", "plist")
        code("YAML file", "application/yaml", "yaml", "yml")
        code("TOML file", "application/toml", "toml")
        code("Settings file", "text/plain", "ini", "cfg", "conf", "properties", "env")
        code("Kotlin file", "text/x-kotlin", "kt", "kts")
        code("Java file", "text/x-java", "java")
        code("Python file", "text/x-python", "py")
        code("JavaScript file", "text/javascript", "js", "mjs", "cjs", "jsx")
        code("TypeScript file", "text/x-typescript", "ts", "tsx")
        code("C file", "text/x-c", "c", "h")
        code("C++ file", "text/x-c++", "cpp", "cc", "cxx", "hpp", "hh")
        code("C# file", "text/x-csharp", "cs")
        code("Go file", "text/x-go", "go")
        code("Rust file", "text/x-rust", "rs")
        code("Swift file", "text/x-swift", "swift")
        code("Dart file", "text/x-dart", "dart")
        code("Ruby file", "text/x-ruby", "rb")
        code("PHP file", "text/x-php", "php")
        code("Shell script", "application/x-sh", "sh", "bash", "zsh", "fish")
        code("PowerShell script", "text/plain", "ps1")
        code("Batch file", "text/plain", "bat", "cmd")
        code("SQL file", "application/sql", "sql")
        code("CSS file", "text/css", "css", "scss", "sass", "less")
        code("Code file", "text/plain", "vue", "svelte", "lua", "pl", "r", "scala", "groovy", "gradle", "m", "mm", "hs",
            "ex", "exs", "erl", "clj", "fs", "vb", "asm", "proto", "graphql", "gql", "tf", "nix", "diff", "patch", "dockerfile")

        add("image/jpeg", IMAGE, "JPEG image", BadgeTint.Image, "jpg", "jpeg")
        add("image/png", IMAGE, "PNG image", BadgeTint.Image, "png")
        add("image/webp", IMAGE, "WebP image", BadgeTint.Image, "webp")
        add("image/gif", IMAGE, "GIF image", BadgeTint.Image, "gif")
        add("image/bmp", IMAGE, "Bitmap image", BadgeTint.Image, "bmp")
        add("image/heic", IMAGE, "HEIC photo", BadgeTint.Image, "heic", "heif")
        add("image/avif", IMAGE, "AVIF image", BadgeTint.Image, "avif")
        add("image/svg+xml", IMAGE, "SVG image", BadgeTint.Image, "svg")
        add("image/x-icon", IMAGE, "Icon", BadgeTint.Image, "ico")

        add("audio/mpeg", AUDIO, "MP3 audio", BadgeTint.Media, "mp3")
        add("audio/mp4", AUDIO, "M4A audio", BadgeTint.Media, "m4a")
        add("audio/aac", AUDIO, "AAC audio", BadgeTint.Media, "aac")
        add("audio/wav", AUDIO, "WAV audio", BadgeTint.Media, "wav")
        add("audio/flac", AUDIO, "FLAC audio", BadgeTint.Media, "flac")
        add("audio/ogg", AUDIO, "Ogg audio", BadgeTint.Media, "ogg", "oga", "opus")
        add("audio/amr", AUDIO, "Voice recording", BadgeTint.Media, "amr")
        add("video/mp4", VIDEO, "MP4 video", BadgeTint.Media, "mp4", "m4v")
        add("video/x-matroska", VIDEO, "MKV video", BadgeTint.Media, "mkv")
        add("video/webm", VIDEO, "WebM video", BadgeTint.Media, "webm")
        add("video/3gpp", VIDEO, "3GP video", BadgeTint.Media, "3gp")
        add("video/quicktime", VIDEO, "QuickTime video", BadgeTint.Media, "mov")

        add("application/zip", ARCHIVE, "ZIP archive", BadgeTint.Archive, "zip")

        // v1.1 formats: recognised and named now, shown by the Unknown viewer until their viewers land.
        add("application/epub+zip", UNKNOWN, "EPUB book", BadgeTint.Word, "epub")
        add("application/vnd.rar", UNKNOWN, "RAR archive", BadgeTint.Archive, "rar")
        add("application/x-7z-compressed", UNKNOWN, "7-Zip archive", BadgeTint.Archive, "7z")
        add("application/x-tar", UNKNOWN, "TAR archive", BadgeTint.Archive, "tar")
        add("application/gzip", UNKNOWN, "GZip archive", BadgeTint.Archive, "gz", "tgz")
        add("application/msword", UNKNOWN, "Older Word document", BadgeTint.Word, "doc")
        add("application/vnd.ms-excel", UNKNOWN, "Older Excel spreadsheet", BadgeTint.Sheet, "xls")
        add("application/vnd.ms-powerpoint", UNKNOWN, "Older PowerPoint file", BadgeTint.Slides, "ppt")
        add("application/vnd.ms-outlook", UNKNOWN, "Outlook message", BadgeTint.Unknown, "msg")
        add("application/vnd.oasis.opendocument.text", UNKNOWN, "OpenDocument text", BadgeTint.Word, "odt")
        add("application/vnd.oasis.opendocument.spreadsheet", UNKNOWN, "OpenDocument spreadsheet", BadgeTint.Sheet, "ods")
        add("application/vnd.oasis.opendocument.presentation", UNKNOWN, "OpenDocument presentation", BadgeTint.Slides, "odp")
        add("application/rtf", UNKNOWN, "Rich text document", BadgeTint.Word, "rtf")
        add("message/rfc822", UNKNOWN, "Email message", BadgeTint.Unknown, "eml")
        add("text/vcard", TEXT, "Contact card", BadgeTint.Text, "vcf")
        add("text/calendar", TEXT, "Calendar event", BadgeTint.Text, "ics")
        add("application/vnd.android.package-archive", UNKNOWN, "Android app", BadgeTint.Unknown, "apk")
        add("font/ttf", UNKNOWN, "Font", BadgeTint.Unknown, "ttf")
        add("font/otf", UNKNOWN, "Font", BadgeTint.Unknown, "otf")
        add("image/tiff", UNKNOWN, "TIFF image", BadgeTint.Image, "tif", "tiff")
        add("application/x-ole-storage", UNKNOWN, "Older Office file", BadgeTint.Unknown)

        // MIME aliases other apps send.
        byMime["application/x-zip-compressed"] = byMime.getValue("application/zip")
        byMime["application/x-rar-compressed"] = byMime.getValue("application/vnd.rar")
        byMime["application/x-yaml"] = byMime.getValue("application/yaml")
        byMime["text/x-markdown"] = byMime.getValue("text/markdown")
        byMime["text/comma-separated-values"] = byMime.getValue("text/csv")
        byMime["text/x-vcard"] = byMime.getValue("text/vcard")
        byMime["audio/x-wav"] = byMime.getValue("audio/wav")
        byMime["audio/x-flac"] = byMime.getValue("audio/flac")
        byMime["application/javascript"] = byMime.getValue("text/javascript")
        byMime["application/x-javascript"] = byMime.getValue("text/javascript")
        byMime["text/xml"] = byMime.getValue("application/xml")
        byMime["text/json"] = byMime.getValue("application/json")
    }

    fun forExtension(extension: String): Format? = byExt[extension.lowercase()]

    fun forMime(mime: String): Format? = byMime[mime.lowercase().substringBefore(';').trim()]

    /** Extensions whose content is text: weak binary signatures are ignored for these (plan §4.4). */
    fun isTextExtension(extension: String): Boolean =
        forExtension(extension)?.family in setOf(TEXT, MARKDOWN, CODE, HTML) || extension.lowercase() in setOf("csv", "tsv", "svg")

    /** Best description for a resolved file: the extension wins when it agrees with the MIME family. */
    fun describe(mime: String, extension: String): Format {
        val byExtension = forExtension(extension)
        val byType = forMime(mime)
        return when {
            byExtension != null && (byType == null || mime in GENERIC_MIMES || byType.mime == byExtension.mime || byType.family == byExtension.family) -> byExtension
            byType != null -> byType
            mime.startsWith("text/") -> forMime("text/plain")!!
            mime.startsWith("image/") -> Format(mime, IMAGE, "Image", extension.uppercase().take(4).ifEmpty { "IMG" }, BadgeTint.Image)
            mime.startsWith("audio/") -> Format(mime, AUDIO, "Audio", extension.uppercase().take(4).ifEmpty { "AUD" }, BadgeTint.Media)
            mime.startsWith("video/") -> Format(mime, VIDEO, "Video", extension.uppercase().take(4).ifEmpty { "VID" }, BadgeTint.Media)
            else -> Format(mime, UNKNOWN, "Unknown file", extension.uppercase().take(4).ifEmpty { "FILE" }, BadgeTint.Unknown)
        }
    }
}
