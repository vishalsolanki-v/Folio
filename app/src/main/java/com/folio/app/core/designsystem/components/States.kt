package com.folio.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.CardShape
import com.folio.app.core.designsystem.FolioPreviews
import com.folio.app.core.designsystem.PreviewSurface
import com.folio.app.core.designsystem.Spacing

/** Calm empty state: monochrome illustration, title, one sentence, one action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.illustration_open_notebook),
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outline),
            modifier = Modifier.size(width = 160.dp, height = 120.dp),
        )
        Spacer(Modifier.height(Spacing.xl))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(Spacing.xl))
            PillButton(actionLabel, onAction)
        }
    }
}

/** Friendly full-screen failure: icon, one sentence, actions (e.g. "Open with another app"). */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(Spacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { actions() }
    }
}

/** Pulsing placeholder blocks shown while a document loads (> 150 ms). */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, lines: Int = 8) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier.fillMaxWidth().padding(Spacing.screen).alpha(pulse), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.fillMaxWidth(0.55f).height(24.dp).background(block, CardShape))
        repeat(lines) { i ->
            Box(Modifier.fillMaxWidth(if (i % 3 == 2) 0.7f else 1f).height(14.dp).background(block, CardShape))
        }
    }
}

@FolioPreviews
@Composable
private fun EmptyStatePreview() = PreviewSurface {
    EmptyState(
        title = "Open anything",
        body = "Tap any file in WhatsApp, Gmail or Files and choose Folio. Or open one from here.",
        actionLabel = "Open file",
    )
}

@FolioPreviews
@Composable
private fun ErrorStatePreview() = PreviewSurface {
    ErrorState("This file looks damaged, so Folio can't show it.") {
        PillButton("Open with another app", {}, style = PillStyle.Tonal)
    }
}

@FolioPreviews
@Composable
private fun SkeletonPreview() = PreviewSurface { LoadingSkeleton() }
