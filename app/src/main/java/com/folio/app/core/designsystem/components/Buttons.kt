package com.folio.app.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.FolioPreviews
import com.folio.app.core.designsystem.PillShape
import com.folio.app.core.designsystem.PreviewSurface
import com.folio.app.core.designsystem.Spacing

enum class PillStyle { Filled, Tonal, Outline }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Filled,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val padding = PaddingValues(horizontal = if (icon == null) 20.dp else 16.dp, vertical = 10.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    val m = modifier.heightIn(min = 40.dp)
    when (style) {
        PillStyle.Filled -> Button(onClick, m, enabled, shape = PillShape, contentPadding = padding) { content() }
        PillStyle.Tonal -> FilledTonalButton(
            onClick, m, enabled, shape = PillShape, contentPadding = padding,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) { content() }
        PillStyle.Outline -> OutlinedButton(
            onClick, m, enabled, shape = PillShape, contentPadding = padding,
            border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) { content() }
    }
}

/** Small translucent pill floating over content, e.g. "4 / 28" or "Edit". */
@Composable
fun FloatingPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (icon != null) Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = if (icon == null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
        }
    }
}

/** Top-bar / row icon button with a Folio icon drawable. */
@Composable
fun IconAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(
            painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

@FolioPreviews
@Composable
private fun ButtonsPreview() = PreviewSurface {
    Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        PillButton("Open file", {}, icon = R.drawable.ic_add)
        PillButton("Save copy", {}, style = PillStyle.Tonal)
        PillButton("Cancel", {}, style = PillStyle.Outline)
        FloatingPill("4 / 28", {})
        FloatingPill("Edit", {}, icon = R.drawable.ic_edit)
        IconAction(R.drawable.ic_search, "Search", {})
    }
}
