package com.folio.app.core.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.folio.app.R
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.FolioBottomSheet

/** A quiet "Simplified layout/preview" note with a one-tap explanation (plan M10, M12). */
@Composable
fun SimplifiedNote(label: String, explanation: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        TextButton(onClick = { open = true }, modifier = Modifier.padding(end = Spacing.sm)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                painterResource(R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs).size(16.dp),
            )
        }
    }
    if (open) {
        FolioBottomSheet(onDismiss = { open = false }, title = label) {
            Text(explanation, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))
        }
    }
}
