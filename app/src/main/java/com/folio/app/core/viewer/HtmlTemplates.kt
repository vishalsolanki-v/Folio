package com.folio.app.core.viewer

import androidx.compose.ui.graphics.Color
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.colors

/** HTML wrappers for SafeWebView content (plan M10). */
object HtmlTemplates {
    private val SCRIPT = Regex("(?is)<script.*?(</script\\s*>|$)")
    private val VIEWPORT = Regex("(?i)<meta[^>]+name\\s*=\\s*[\"']?viewport")
    private val HEAD_OPEN = Regex("(?i)<head(\\s[^>]*)?>")
    private val HTML_OPEN = Regex("(?i)<html(\\s[^>]*)?>")

    /**
     * A clean document page for converted Word (DOCX) files. Light and Auto paper show it as written (white canvas,
     * dark ink); Sepia and Dark recolour the page for reading.
     */
    fun wrap(bodyHtml: String, paper: Paper, appIsDark: Boolean): String = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html { background: #FFFFFF; color: #1A1A1A; }
body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 16px; line-height: 1.6; max-width: 820px; margin: 0 auto; padding: 24px 20px 64px; overflow-wrap: break-word; }
h1, h2, h3, h4, h5, h6 { line-height: 1.3; font-weight: 600; margin: 1.4em 0 0.5em; color: #111111; }
h1 { font-size: 28px; } h1.title { font-size: 32px; margin-top: 0.4em; } h2 { font-size: 23px; } h3 { font-size: 19px; } h4, h5, h6 { font-size: 16px; }
p { margin: 0 0 0.9em; }
a { color: #0969DA; text-decoration: underline; }
img { max-width: 100%; height: auto; }
table { border-collapse: collapse; display: block; overflow-x: auto; max-width: 100%; margin: 1.2em 0; border: 1px solid #D0D7DE; }
td, th { border: 1px solid #D0D7DE; padding: 8px 12px; vertical-align: top; }
th { background: #F6F8FA; font-weight: 600; }
td p, th p { margin: 0; }
blockquote { margin: 1em 0; padding-left: 14px; border-left: 3px solid #D0D7DE; color: #57609A; }
pre, code { font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace; background: #F6F8FA; border-radius: 4px; padding: 2px 5px; font-size: 85%; }
hr { border: 0; border-top: 1px solid #D0D7DE; margin: 2em 0; }
${readingColours(paper, appIsDark)}
</style></head><body>$bodyHtml</body></html>"""

    /** Sepia/Dark colours for the Word page (it's Folio's own markup, so it can be recoloured exactly). */
    private fun readingColours(paper: Paper, appIsDark: Boolean): String {
        if (paper == Paper.Auto || paper == Paper.Light) return ""
        val (bg, ink) = paper.colors(appIsDark)
        return """html { background: ${css(bg)}; color: ${css(ink)}; }
h1, h2, h3, h4, h5, h6, blockquote { color: ${css(ink)}; }
a { color: ${css(ink)}; }
th, pre, code { background: ${css(ink, 0.06f)}; }
table, td, th, hr, blockquote { border-color: ${css(ink, 0.18f)}; }"""
    }

    /**
     * An HTML file as written, minus scripts. [fitToScreen] gives pages without a mobile layout tag one, so their
     * text wraps to the phone instead of showing a zoomed-out desktop page. Sepia/Dark paper tint the page with CSS
     * filters (its own colours are unknown): Dark inverts the page and turns pictures back.
     */
    fun page(html: String, paper: Paper, fitToScreen: Boolean): String {
        val head = buildString {
            if (fitToScreen && !VIEWPORT.containsMatchIn(html)) {
                append("""<meta name="viewport" content="width=device-width, initial-scale=1"><style>img, video { max-width: 100%; height: auto; } pre { white-space: pre-wrap; } body { overflow-wrap: break-word; }</style>""")
            }
            // On <body>, not <html>: the page background is painted from the root element without its filter.
            when (paper) {
                Paper.Sepia -> append(
                    "<style>html { background: #F4ECD8; } body { filter: sepia(0.25); }</style>",
                )
                Paper.Dark -> append(
                    "<style>html { background: #1A1A1A; } body { background: #FFFFFF; min-height: 100vh; filter: invert(0.9) hue-rotate(180deg); } " +
                        "img, video, picture, canvas, svg image { filter: invert(1) hue-rotate(180deg); }</style>",
                )
                Paper.Auto, Paper.Light -> Unit
            }
        }
        val clean = stripScripts(html)
        if (head.isEmpty()) return clean
        // Inside <head> (or right after <html>), never before <!DOCTYPE>: that would switch the page to quirks mode.
        val anchor = HEAD_OPEN.find(clean) ?: HTML_OPEN.find(clean) ?: return head + clean
        return clean.substring(0, anchor.range.last + 1) + head + clean.substring(anchor.range.last + 1)
    }

    fun stripScripts(html: String) = html.replace(SCRIPT, "")

    private fun css(color: Color, alpha: Float = color.alpha) =
        "rgba(${(color.red * 255).toInt()}, ${(color.green * 255).toInt()}, ${(color.blue * 255).toInt()}, $alpha)"
}
