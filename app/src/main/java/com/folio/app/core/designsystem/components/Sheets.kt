package com.folio.app.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.FolioPreviews
import com.folio.app.core.designsystem.PreviewSurface
import com.folio.app.core.designsystem.SheetShape
import com.folio.app.core.designsystem.Spacing

/** Modal bottom sheet with Folio shape and colors, optional title, and content below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolioBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    ) {
        SheetContent(title, content)
    }
}

@Composable
private fun SheetContent(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.sm)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
        }
        content()
    }
}

/** Confirmation dialog with up to three buttons (dismiss, optional neutral, confirm). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    neutralLabel: String? = null,
    onNeutral: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            if (neutralLabel != null) TextButton(onNeutral) { Text(neutralLabel) }
            TextButton(onDismiss) { Text(dismissLabel) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
    )
}

@FolioPreviews
@Composable
private fun SheetContentPreview() = PreviewSurface {
    SheetContent("Invoice.pdf") {
        SheetRow(R.drawable.ic_share, "Share", {})
        SheetRow(R.drawable.ic_open_in_new, "Open with another app", {})
        SheetRow(R.drawable.ic_print, "Print or save as PDF", {})
        SheetRow(R.drawable.ic_info, "File info", {})
    }
}
