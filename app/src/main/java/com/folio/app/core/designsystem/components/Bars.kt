package com.folio.app.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.FolioPreviews
import com.folio.app.core.designsystem.PillShape
import com.folio.app.core.designsystem.PreviewSurface
import com.folio.app.core.designsystem.Spacing

/** Flat top bar: back, title (optionally tappable), up to 3 actions. Plan rule: max 4 items in total. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = if (onTitleClick != null) Modifier.clickable(role = Role.Button, onClick = onTitleClick) else Modifier,
            )
        },
        navigationIcon = { if (onBack != null) IconAction(R.drawable.ic_arrow_back, "Back", onBack) },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** Pill search field. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = Spacing.lg, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = Spacing.md), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                )
            }
            if (value.isNotEmpty()) IconAction(R.drawable.ic_close, "Clear search", { onValueChange("") })
        }
    }
}

/** Single-select chip row; selected chip is filled with ink (monochrome, Notion-like). */
@Composable
fun FilterChipsRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.screen),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        itemsIndexed(labels) { index, label ->
            val isSelected = index == selected
            Surface(
                onClick = { onSelect(index) },
                shape = PillShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (isSelected) null else BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

/** Replaces the viewer top bar while searching: field, "3 / 14", previous, next, close. */
@Composable
fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
    Row(
        modifier.fillMaxWidth().height(64.dp).padding(start = Spacing.lg, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Find in file", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Find in file" },
            )
        }
        if (query.isNotEmpty()) {
            Text(
                if (matchCount == 0) "0 / 0" else "${matchIndex + 1} / $matchCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
        IconAction(R.drawable.ic_expand_less, "Previous match", onPrevious, enabled = matchCount > 0)
        IconAction(R.drawable.ic_expand_more, "Next match", onNext, enabled = matchCount > 0)
        IconAction(R.drawable.ic_close, "Close search", onClose)
    }
}

@FolioPreviews
@Composable
private fun BarsPreview() = PreviewSurface {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FolioTopBar("Invoice-October-2026-final.pdf", onBack = {}, onTitleClick = {}) {
            IconAction(R.drawable.ic_search, "Search", {})
            IconAction(R.drawable.ic_more_vert, "More", {})
        }
        SearchField("", {}, "Search recent files", Modifier.padding(horizontal = Spacing.screen))
        FilterChipsRow(listOf("All", "Pinned", "Documents", "Sheets", "Text & code", "Media", "Archives"), 0, {})
        FindBar("total", {}, 2, 14, {}, {}, {})
    }
}
