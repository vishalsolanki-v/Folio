package com.folio.app.formats.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import com.folio.app.R
import com.folio.app.core.designsystem.PaperColors
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.viewer.ViewerState
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/** Display settings for [SoraText], from the user's editor settings and the paper. */
data class SoraLook(
    val paper: PaperColors,
    val darkPaper: Boolean,
    val accent: Color,
    val fontSp: Int,
    val wordWrap: Boolean,
    val lineNumbers: Boolean,
)

/**
 * Sora Editor in Compose, used read-only by the text viewer and editable by the editor.
 * [text] is set once per value (a new value recreates the editor). Find-in-file is wired to [search] when given.
 */
@Composable
fun SoraText(
    text: String,
    scope: String?,
    readOnly: Boolean,
    look: SoraLook,
    modifier: Modifier = Modifier,
    lineSeparator: String = "\n",
    search: ViewerState? = null,
    onTap: (() -> Unit)? = null,
    onChange: (() -> Unit)? = null,
    onReady: (CodeEditor) -> Unit = {},
) {
    val context = LocalContext.current
    val loaded by produceState(false) { TextMate.ensureLoaded(context); value = true }
    if (!loaded) {
        LoadingSkeleton(modifier)
        return
    }
    val tap by rememberUpdatedState(onTap)
    val change by rememberUpdatedState(onChange)
    var editor by remember { mutableStateOf<CodeEditor?>(null) }

    key(text, scope) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                CodeEditor(ctx).apply {
                    val dp = ctx.resources.displayMetrics.density
                    typefaceText = ResourcesCompat.getFont(ctx, R.font.jetbrains_mono)
                    typefaceLineNumber = typefaceText
                    isEditable = !readOnly
                    setDividerWidth(0f)
                    setLineNumberMarginLeft(16 * dp)
                    setDividerMargin(10 * dp, 12 * dp)
                    setLineSpacing(2 * dp, 1.1f)
                    isHighlightCurrentLine = !readOnly
                    setCursorAnimationEnabled(false)
                    searcher.isCyclicJumping = true
                    applyLook(look) // the TextMate colour scheme must be in place before the TextMate language
                    setEditorLanguage(scope?.let { TextMateLanguage.create(it, false) } ?: EmptyLanguage())
                    setText(text)
                    setLineSeparator(
                        when (lineSeparator) {
                            "\r\n" -> LineSeparator.CRLF
                            "\r" -> LineSeparator.CR
                            else -> LineSeparator.LF
                        },
                    )
                    subscribeAlways(ClickEvent::class.java) { tap?.invoke() }
                    subscribeAlways(ContentChangeEvent::class.java) {
                        if (it.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) change?.invoke()
                    }
                    editor = this
                    onReady(this)
                }
            },
            update = { it.applyLook(look) },
            onRelease = { it.release(); if (editor === it) editor = null },
        )
    }

    if (search != null) FindSync(editor, search)
}

private fun CodeEditor.applyLook(look: SoraLook) {
    ThemeRegistry.getInstance().setTheme(if (look.darkPaper) TextMate.DARK_THEME else TextMate.LIGHT_THEME)
    val ink = look.paper.ink
    // Assign first: attaching a TextMate scheme re-applies the theme, which would wipe earlier overrides.
    colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    colorScheme.apply {
        setColor(EditorColorScheme.WHOLE_BACKGROUND, look.paper.background.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, look.paper.background.toArgb())
        setColor(EditorColorScheme.LINE_NUMBER, ink.copy(alpha = 0.35f).toArgb())
        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, ink.copy(alpha = 0.7f).toArgb())
        setColor(EditorColorScheme.LINE_DIVIDER, Color.Transparent.toArgb())
        setColor(EditorColorScheme.CURRENT_LINE, ink.copy(alpha = 0.04f).toArgb())
        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, look.accent.copy(alpha = 0.28f).toArgb())
        setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, look.accent.copy(alpha = 0.22f).toArgb())
        setColor(EditorColorScheme.SELECTION_INSERT, look.accent.toArgb())
        setColor(EditorColorScheme.SELECTION_HANDLE, look.accent.toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB, ink.copy(alpha = 0.2f).toArgb())
        setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, ink.copy(alpha = 0.35f).toArgb())
        setColor(EditorColorScheme.BLOCK_LINE, ink.copy(alpha = 0.08f).toArgb())
        setColor(EditorColorScheme.BLOCK_LINE_CURRENT, ink.copy(alpha = 0.16f).toArgb())
    }
    invalidate()
    setTextSize(look.fontSp.toFloat())
    isWordwrap = look.wordWrap
    isLineNumberEnabled = look.lineNumbers
}

/** Keeps Sora's searcher in step with the find bar: query → matches, ↑/↓ → selected match. */
@Composable
private fun FindSync(editor: CodeEditor?, state: ViewerState) {
    DisposableEffect(editor) {
        val receipt = editor?.subscribeAlways(PublishSearchResultEvent::class.java) {
            val searcher = editor.searcher
            state.matchCount = if (searcher.hasQuery()) searcher.matchedPositionCount else 0
            if (state.matchCount > 0 && searcher.hasQuery() && searcher.currentMatchedPositionIndex < 0) searcher.gotoNext()
            state.matchIndex = 0
        }
        onDispose { receipt?.unsubscribe() }
    }
    LaunchedEffect(editor, state.query) {
        val searcher = editor?.searcher ?: return@LaunchedEffect
        if (state.query.isEmpty()) {
            if (searcher.hasQuery()) searcher.stopSearch()
            state.matchCount = 0
        } else {
            searcher.search(state.query, EditorSearcher.SearchOptions(true, false))
        }
    }
    LaunchedEffect(editor, state.matchIndex) {
        val searcher = editor?.searcher ?: return@LaunchedEffect
        val count = state.matchCount
        if (!searcher.hasQuery() || count == 0) return@LaunchedEffect
        val target = state.matchIndex
        val current = searcher.currentMatchedPositionIndex
        if (current == target) return@LaunchedEffect
        if (current >= 0 && target == (current - 1 + count) % count) {
            searcher.gotoPrevious()
        } else {
            repeat(count) { if (searcher.currentMatchedPositionIndex != target) searcher.gotoNext() }
        }
    }
}
