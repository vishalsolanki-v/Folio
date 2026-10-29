package com.folio.app.feature.settings

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.folio.app.R
import com.folio.app.appContainer
import com.folio.app.core.data.Settings
import com.folio.app.core.designsystem.CardShape
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.Spacing
import com.folio.app.core.designsystem.ThemeMode
import com.folio.app.core.designsystem.components.ConfirmDialog
import com.folio.app.core.designsystem.components.FilterChipsRow
import com.folio.app.core.designsystem.components.FolioTopBar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit) {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel { SettingsViewModel(container.settings, container.library) }
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(topBar = { FolioTopBar("Settings", onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val s = settings ?: return@Box
            Column(Modifier.widthIn(max = Spacing.contentMaxWidth).verticalScroll(rememberScrollState())) {
                SettingsBody(s, vm, onOpenLicenses)
            }
        }
    }
}

@Composable
private fun SettingsBody(s: Settings, vm: SettingsViewModel, onOpenLicenses: () -> Unit) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Group("Appearance")
    Label("Theme")
    FilterChipsRow(listOf("System", "Light", "Dark", "Black"), s.theme.ordinal, { vm.setTheme(ThemeMode.entries[it]) })

    Group("Documents")
    Label("Default paper")
    Text(
        "Auto shows PDFs, Word files and web pages as written, and text in the app's theme.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.sm),
    )
    FilterChipsRow(listOf("Auto", "Light", "Sepia", "Dark"), s.paper.ordinal, { vm.setPaper(Paper.entries[it]) })

    Group("Editor")
    var font by remember(s.editorFontSp) { mutableFloatStateOf(s.editorFontSp.toFloat()) }
    Row(Modifier.padding(horizontal = Spacing.screen), verticalAlignment = Alignment.CenterVertically) {
        Text("Font size", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${font.roundToInt()} sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val range = Settings.EDITOR_FONT_RANGE
    Slider(
        value = font,
        onValueChange = { font = it },
        onValueChangeFinished = { vm.setEditorFontSp(font.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = range.last - range.first - 1,
        modifier = Modifier.padding(horizontal = Spacing.screen),
    )
    SwitchRow("Word wrap", null, s.wordWrap, vm::setWordWrap)
    SwitchRow("Line numbers", null, s.lineNumbers, vm::setLineNumbers)

    Group("Storage")
    SwitchRow(
        "Keep copies of shared files for Recents",
        "Files opened from WhatsApp or email disappear after you close them. Folio keeps a private copy on this phone so they stay in Recents. Up to 300 MB.",
        s.keepCopies,
        vm::setKeepCopies,
    )
    ClickRow("Clear recents and copies", onClick = { confirmClear = true })

    PrivacyCard()

    Group("About")
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
    ClickRow("Version", value = version)
    ClickRow("Open-source licenses", chevron = true, onClick = onOpenLicenses)
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        ClickRow("Design catalog (debug)", chevron = true, onClick = {
            context.startActivity(Intent().setClassName(context, "com.folio.app.debug.DesignCatalogActivity"))
        })
    }
    Spacer(Modifier.height(Spacing.xxxl))

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear recents and copies?",
            text = "Your Recents list and Folio's private copies will be removed. Your original files and added folders stay as they are.",
            confirmLabel = "Clear",
            onConfirm = { confirmClear = false; vm.clearRecents() },
            dismissLabel = "Cancel",
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Group(title: String) {
    HorizontalDivider(Modifier.padding(top = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg, bottom = Spacing.sm),
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.screen, bottom = Spacing.sm))
}

@Composable
private fun SwitchRow(title: String, supporting: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null, // the whole row toggles
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

@Composable
private fun ClickRow(title: String, value: String? = null, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chevron) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacyCard() {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xl),
    ) {
        Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(painterResource(R.drawable.ic_lock), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Folio has no internet access and sends nothing on its own. Files stay here unless you choose Share or Open with another app. Nothing is collected.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
