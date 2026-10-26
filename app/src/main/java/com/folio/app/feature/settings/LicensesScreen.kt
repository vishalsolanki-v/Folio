package com.folio.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.folio.app.R
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.components.FolioTopBar
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/** Maven dependencies come from AboutLibraries; bundled assets that aren't dependencies are listed in the header. */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val libraries by produceLibraries(R.raw.aboutlibraries)
    Scaffold(topBar = { FolioTopBar("Open-source licenses", onBack = onBack) }) { padding ->
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize().padding(padding),
            header = {
                item {
                    Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md)) {
                        Bundled("Inter and JetBrains Mono fonts", "SIL Open Font License 1.1")
                        Bundled("Material Symbols icons", "Apache License 2.0")
                        Bundled("mammoth (modified SimpleSax class)", "BSD 2-Clause License, © 2015 Michael Williamson")
                        Bundled("Sora Editor", "GNU LGPL 2.1, used unmodified. Source: github.com/Rosemoe/sora-editor")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
        )
    }
}

@Composable
private fun Bundled(name: String, license: String) {
    Column(Modifier.padding(vertical = Spacing.sm)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
