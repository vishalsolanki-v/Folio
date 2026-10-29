package com.folio.app.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.folio.app.R
import com.folio.app.core.designsystem.BadgeShape
import com.folio.app.core.designsystem.BadgeTint
import com.folio.app.core.designsystem.FolioPreviews
import com.folio.app.core.designsystem.LocalFolioDark
import com.folio.app.core.designsystem.PreviewSurface
import com.folio.app.core.designsystem.Spacing

/** Monospace extension badge, e.g. "PDF". [spokenName] is what TalkBack reads ("PDF file"). */
@Composable
fun ExtBadge(label: String, tint: BadgeTint, spokenName: String, modifier: Modifier = Modifier) {
    val color = tint.color(LocalFolioDark.current)
    Box(
        modifier
            .widthIn(min = badgeWidth())
            .background(color.copy(alpha = 0.14f), BadgeShape)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            // One spoken label ("PDF document"), not the badge letters as well.
            .clearAndSetSemantics { contentDescription = spokenName },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(4).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Badge column width; grows with the font size so every row's name still starts at the same place. */
@Composable
private fun badgeWidth() = with(LocalDensity.current) { (11.sp.toDp() * 4).coerceAtLeast(44.dp) } // 4 badge letters at their own size (non-linear font scaling grows small text most)

/** Leading folder icon sized like a badge, so file and folder rows line up. */
@Composable
fun FolderGlyph(modifier: Modifier = Modifier) {
    Box(modifier.widthIn(min = badgeWidth()), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_folder), contentDescription = "Folder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The one list row used for files and folders: leading badge, name (middle-ellipsized so the
 * extension stays visible), one-line subtitle, optional trailing slot (usually an overflow button).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    name: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.rowHeight)
            .then(clickModifier)
            .padding(start = Spacing.screen, end = if (trailing == null) Spacing.screen else Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        leading()
        Column(Modifier.weight(1f).padding(vertical = Spacing.md)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** A row inside a bottom sheet: icon, label, optional supporting line. */
@Composable
fun SheetRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
            }
        }
        trailing?.invoke()
    }
}

@FolioPreviews
@Composable
private fun RowsPreview() = PreviewSurface {
    Column {
        FileRow(
            name = "Quarterly-financial-report-final-v3-signed-by-everyone.pdf",
            subtitle = "2.4 MB · 10 min ago · WhatsApp",
            leading = { ExtBadge("PDF", BadgeTint.Pdf, "PDF file") },
            onClick = {},
            trailing = { IconAction(R.drawable.ic_more_vert, "More", {}) },
        )
        FileRow("notes.md", "4 KB · Yesterday", leading = { ExtBadge("MD", BadgeTint.Text, "Markdown file") }, onClick = {})
        FileRow("WhatsApp Documents", "", leading = { FolderGlyph() }, onClick = {})
        SheetRow(R.drawable.ic_share, "Share", {})
        SheetRow(R.drawable.ic_edit, "Editing not available", {}, supporting = "This file uses an unusual text encoding", enabled = false)
    }
}
