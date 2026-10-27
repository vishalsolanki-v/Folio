package com.folio.app.feature.viewer

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.folio.app.R
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.FilterChipsRow
import com.folio.app.core.designsystem.components.FolioBottomSheet
import com.folio.app.core.designsystem.components.SheetRow
import com.folio.app.core.model.FormatInfo
import com.folio.app.core.model.OpenedFile

/** The ⋯ sheet (plan §2.3). Rows that don't apply to this file/format are simply absent. */
@Composable
internal fun MoreSheet(
    file: OpenedFile,
    paper: Paper?,
    pinned: Boolean?,
    canPrint: Boolean,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    onPrint: () -> Unit,
    onPaper: (Paper) -> Unit,
    onTogglePin: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    FolioBottomSheet(onDismiss = onDismiss, title = file.name) {
        SheetRow(R.drawable.ic_share, "Share", { onDismiss(); onShare() })
        SheetRow(R.drawable.ic_open_in_new, "Open with another app", { onDismiss(); onOpenWith() })
        if (canPrint) SheetRow(R.drawable.ic_print, "Print or save as PDF", { onDismiss(); onPrint() })
        if (paper != null) {
            Text(
                "Paper",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xl, top = Spacing.md, bottom = Spacing.sm),
            )
            FilterChipsRow(Paper.entries.map { it.name }, paper.ordinal, { onPaper(Paper.entries[it]) }, Modifier.padding(start = Spacing.xs, bottom = Spacing.sm))
        }
        if (pinned != null) {
            SheetRow(if (pinned) R.drawable.ic_push_pin_filled else R.drawable.ic_push_pin, if (pinned) "Unpin" else "Pin", { onTogglePin() })
        }
        SheetRow(R.drawable.ic_info, "File info", { onDismiss(); onInfo() })
    }
}

@Composable
internal fun FileInfoSheet(file: OpenedFile, lastModified: suspend () -> Long?, extras: Map<String, String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val modified by produceState<Long?>(null, file.uri) { value = lastModified() }
    val format = FormatInfo.describe(file.mime, file.extension)
    FolioBottomSheet(onDismiss = onDismiss, title = "File info") {
        Column(Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            InfoRow("Name", file.name)
            InfoRow("Type", format.friendlyName)
            InfoRow("Size", if (file.sizeBytes >= 0) Formatter.formatFileSize(context, file.sizeBytes) else "Unknown")
            modified?.let {
                InfoRow("Modified", DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME))
            }
            file.sourceApp?.let { InfoRow("From", it) }
            extras.forEach { (label, value) -> InfoRow(label, value) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.3f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.7f))
    }
}

/** Plan v1.0: type a page number. [pageLabel] is "4 / 28". */
@Composable
internal fun PageJumpDialog(pageLabel: String, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val total = pageLabel.substringAfter('/').trim().toIntOrNull() ?: 1
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..total }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter(Char::isDigit).take(6) },
                label = { Text("Page (1–$total)") },
                singleLine = true,
                isError = text.isNotEmpty() && page == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { page?.let { onJump(it - 1) }; onDismiss() }, enabled = page != null) { Text("Go") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
}
