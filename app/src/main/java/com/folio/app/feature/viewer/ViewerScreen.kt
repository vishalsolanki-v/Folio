package com.folio.app.feature.viewer

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folio.app.appContainer
import com.folio.app.core.designsystem.components.EmptyState
import com.folio.app.core.designsystem.components.ErrorState
import com.folio.app.core.designsystem.components.FolioTopBar
import com.folio.app.core.designsystem.components.LoadingSkeleton
import com.folio.app.core.designsystem.components.PillButton
import com.folio.app.core.designsystem.components.PillStyle
import com.folio.app.core.model.FormatInfo

/** M5 placeholder: resolves and records the file. M6 replaces the body with the real viewer shell. */
@Composable
fun ViewerScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: ViewerViewModel = viewModel { ViewerViewModel(container, createSavedStateHandle()) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ready = state as? ViewerUiState.Ready

    Scaffold(topBar = { FolioTopBar(ready?.file?.name.orEmpty(), onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ViewerUiState.Loading -> LoadingSkeleton()
                ViewerUiState.NoLongerAvailable -> ErrorState("This file is no longer available.") {
                    PillButton("Go back", onBack, style = PillStyle.Tonal)
                }
                is ViewerUiState.Ready -> {
                    val format = FormatInfo.describe(s.file.mime, s.file.extension)
                    val size = if (s.file.sizeBytes >= 0) Formatter.formatShortFileSize(context, s.file.sizeBytes) else "Unknown size"
                    EmptyState(format.friendlyName, "$size · Previews arrive in the next update.")
                }
            }
        }
    }
}
