package com.folio.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.BadgeTint
import com.folio.app.core.designsystem.CardShape
import com.folio.app.core.designsystem.CodeTextStyle
import com.folio.app.core.designsystem.FolioTheme
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.ThemeMode
import com.folio.app.core.designsystem.colors
import com.folio.app.core.designsystem.components.ConfirmDialog
import com.folio.app.core.designsystem.components.EmptyState
import com.folio.app.core.designsystem.components.ErrorState
import com.folio.app.core.designsystem.components.ExtBadge
import com.folio.app.core.designsystem.components.FileRow
import com.folio.app.core.designsystem.components.FilterChipsRow
import com.folio.app.core.designsystem.components.FindBar
import com.folio.app.core.designsystem.components.FloatingPill
import com.folio.app.core.designsystem.components.FolderGlyph
import com.folio.app.core.designsystem.components.FolioBottomSheet
import com.folio.app.core.designsystem.components.FolioTopBar
import com.folio.app.core.designsystem.components.IconAction
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle
import com.folio.app.core.designsystem.components.SearchField
import com.folio.app.core.designsystem.components.SheetRow

/** Debug-only: every design-system piece on one scrolling page, switchable between themes. */
class DesignCatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent.getStringExtra("theme")?.let { name -> ThemeMode.entries.firstOrNull { it.name.equals(name, true) } }
        setContent {
            var mode by rememberSaveable { mutableStateOf(start ?: ThemeMode.System) }
            FolioTheme(mode) {
                Surface(Modifier.fillMaxSize()) { Catalog(mode) { mode = it } }
            }
        }
    }
}

@Composable
private fun Catalog(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var chip by rememberSaveable { mutableIntStateOf(0) }
    var find by rememberSaveable { mutableStateOf("total") }
    var sheet by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())) {
        FolioTopBar("Design catalog", onBack = {}) {
            IconAction(R.drawable.ic_search, "Search", {})
            IconAction(R.drawable.ic_more_vert, "More", {})
        }
        FilterChipsRow(ThemeMode.entries.map { it.name }, mode.ordinal, { onMode(ThemeMode.entries[it]) })

        Section("Type")
        Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Folio", style = MaterialTheme.typography.headlineMedium)
            Text("Sheet title", style = MaterialTheme.typography.titleLarge)
            Text("Quarterly report.pdf", style = MaterialTheme.typography.titleMedium)
            Text("Folio shows Word files in a clean reading layout, so long documents stay easy to read on a phone.", style = MaterialTheme.typography.bodyLarge)
            Text("2.4 MB · 10 min ago · WhatsApp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12 / 48", style = MaterialTheme.typography.labelSmall)
            Text("val total = items.sumOf { it.price }", style = CodeTextStyle)
        }

        Section("Surfaces & paper")
        FlowRow(Modifier.padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val c = MaterialTheme.colorScheme
            listOf(
                "surface" to c.surface, "low" to c.surfaceContainerLow, "cont" to c.surfaceContainer, "high" to c.surfaceContainerHigh,
                "ink" to c.onSurface, "meta" to c.onSurfaceVariant, "accent" to c.tertiary, "line" to c.outlineVariant,
            ).forEach { (name, color) -> Swatch(name, color) }
            Paper.entries.forEach { p -> p.colors(LocalFolioDark.current).let { Swatch("paper ${p.name}", it.background, it.ink) } }
        }

        Section("Badges")
        FlowRow(Modifier.padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("PDF" to BadgeTint.Pdf, "DOCX" to BadgeTint.Word, "XLSX" to BadgeTint.Sheet, "PPTX" to BadgeTint.Slides, "MD" to BadgeTint.Text,
                "JSON" to BadgeTint.Code, "JPG" to BadgeTint.Image, "MP4" to BadgeTint.Media, "ZIP" to BadgeTint.Archive, "BIN" to BadgeTint.Unknown)
                .forEach { (label, tint) -> ExtBadge(label, tint, "$label file") }
        }

        Section("Buttons")
        FlowRow(Modifier.padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PillButton("Open file", {}, icon = R.drawable.ic_add)
            PillButton("Save copy", {}, style = PillStyle.Tonal)
            PillButton("Cancel", {}, style = PillStyle.Outline)
            PillButton("Disabled", {}, enabled = false)
            FloatingPill("4 / 28", {})
            FloatingPill("Edit", {}, icon = R.drawable.ic_edit)
        }

        Section("Search & filters")
        SearchField(search, { search = it }, "Search recent files", Modifier.padding(horizontal = Spacing.screen))
        Box(Modifier.height(Spacing.md))
        FilterChipsRow(listOf("All", "Pinned", "Documents", "Sheets", "Text & code", "Media", "Archives"), chip, { chip = it })

        Section("Rows")
        FileRow(
            "Quarterly-financial-report-final-v3-signed-by-everyone.pdf", "2.4 MB · 10 min ago · WhatsApp",
            leading = { ExtBadge("PDF", BadgeTint.Pdf, "PDF file") }, onClick = {},
            trailing = { IconAction(R.drawable.ic_more_vert, "More", {}) },
        )
        FileRow("Budget 2027.xlsx", "86 KB · Yesterday · Gmail", leading = { ExtBadge("XLSX", BadgeTint.Sheet, "Excel file") }, onClick = {},
            trailing = { IconAction(R.drawable.ic_more_vert, "More", {}) })
        FileRow("WhatsApp Documents", "", leading = { FolderGlyph() }, onClick = {})

        Section("Find bar")
        FindBar(find, { find = it }, 2, 14, {}, {}, {}, autoFocus = false)

        Section("Sheet & dialog")
        Row(Modifier.padding(horizontal = Spacing.screen), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PillButton("Show sheet", { sheet = true }, style = PillStyle.Tonal)
            PillButton("Show dialog", { dialog = true }, style = PillStyle.Tonal)
        }

        Section("States")
        Bordered { EmptyState("Open anything", "Tap any file in WhatsApp, Gmail or Files and choose Folio. Or open one from here.", actionLabel = "Open file") }
        Bordered { ErrorState("This file looks damaged, so Folio can't show it.") { PillButton("Open with another app", {}, style = PillStyle.Tonal) } }
        Bordered { LoadingSkeleton() }
        Box(Modifier.height(Spacing.xxxl))
    }

    if (sheet) {
        FolioBottomSheet(onDismiss = { sheet = false }, title = "Invoice.pdf") {
            SheetRow(R.drawable.ic_share, "Share", {})
            SheetRow(R.drawable.ic_open_in_new, "Open with another app", {})
            SheetRow(R.drawable.ic_print, "Print or save as PDF", {})
            SheetRow(R.drawable.ic_push_pin, "Pin", {})
            SheetRow(R.drawable.ic_info, "File info", {})
        }
    }
    if (dialog) {
        ConfirmDialog(
            "Discard changes?", "Your edits to notes.md haven't been saved.",
            "Save", { dialog = false }, "Keep editing", { dialog = false }, "Discard", { dialog = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = Spacing.xl), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
    )
}

@Composable
private fun Swatch(name: String, color: Color, ink: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).background(color, CardShape).border(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant, CardShape),
            contentAlignment = Alignment.Center,
        ) { if (ink != null) Text("Aa", color = ink, style = MaterialTheme.typography.titleMedium) }
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bordered(content: @Composable () -> Unit) {
    Box(
        Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm).fillMaxWidth().height(320.dp)
            .border(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant, CardShape),
    ) { content() }
}
