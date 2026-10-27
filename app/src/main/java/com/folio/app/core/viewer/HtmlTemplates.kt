package com.folio.app.core.viewer

import androidx.compose.ui.graphics.Color
import com.folio.app.core.designsystem.PaperColors

/** HTML wrappers for SafeWebView content (plan M10). */
object HtmlTemplates {
    private val SCRIPT = Regex("(?is)<script.*?(</script\\s*>|$)")

    /** A reading page around converted body HTML (DOCX): Inter, 760 px column, hairline tables, paper colours. */
    fun wrap(bodyHtml: String, paper: PaperColors): String = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>${fontFace()}
:root { --bg: ${css(paper.background)}; --ink: ${css(paper.ink)}; --line: ${css(paper.ink, 0.16f)}; --soft: ${css(paper.ink, 0.05f)}; }
html { background: var(--bg); color: var(--ink); }
body { font-family: Inter, sans-serif; font-size: 16px; line-height: 1.62; max-width: 760px; margin: 0 auto; padding: 20px 20px 48px; overflow-wrap: break-word; }
h1, h2, h3, h4, h5, h6 { line-height: 1.3; font-weight: 600; margin: 1.4em 0 0.5em; letter-spacing: -0.01em; }
h1 { font-size: 28px; } h1.title { font-size: 32px; margin-top: 0.4em; } h2 { font-size: 23px; } h3 { font-size: 19px; } h4, h5, h6 { font-size: 16px; }
p { margin: 0 0 0.9em; }
a { color: inherit; text-decoration: underline; }
img { max-width: 100%; height: auto; }
table { border-collapse: collapse; display: block; overflow-x: auto; max-width: 100%; margin: 1em 0; }
td, th { border: 1px solid var(--line); padding: 6px 10px; vertical-align: top; }
th { background: var(--soft); }
td p, th p { margin: 0; }
blockquote { margin: 1em 0; padding-left: 14px; border-left: 3px solid var(--line); }
pre, code { font-family: monospace; background: var(--soft); }
hr { border: 0; border-top: 1px solid var(--line); margin: 2em 0; }
</style></head><body>$bodyHtml</body></html>"""

    /**
     * An HTML file as written, minus scripts (defence in depth: JavaScript is off anyway), with the paper colours
     * as low-priority defaults so pages without their own colours follow Light/Sepia/Dark.
     */
    fun page(html: String, paper: PaperColors): String {
        val defaults = "<style>${fontFace()} html { background: ${css(paper.background)}; color: ${css(paper.ink)}; " +
            "font-family: Inter, sans-serif; overflow-wrap: break-word; } body { margin: 16px 20px 48px; line-height: 1.5; } img { max-width: 100%; height: auto; }</style>"
        return defaults + stripScripts(html)
    }

    fun stripScripts(html: String) = html.replace(SCRIPT, "")

    private fun fontFace() = "@font-face { font-family: Inter; src: url(inter.ttf); }"

    private fun css(color: Color, alpha: Float = color.alpha) =
        "rgba(${(color.red * 255).toInt()}, ${(color.green * 255).toInt()}, ${(color.blue * 255).toInt()}, $alpha)"
}
