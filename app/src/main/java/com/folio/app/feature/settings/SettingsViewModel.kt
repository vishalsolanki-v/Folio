package com.folio.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.folio.app.core.data.Library
import com.folio.app.core.data.Settings
import com.folio.app.core.data.SettingsRepository
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository, private val library: Library) : ViewModel() {
    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTheme(value: ThemeMode) = viewModelScope.launch { repo.setTheme(value) }
    fun setPaper(value: Paper) = viewModelScope.launch { repo.setPaper(value) }
    fun setEditorFontSp(value: Int) = viewModelScope.launch { repo.setEditorFontSp(value) }
    fun setWordWrap(value: Boolean) = viewModelScope.launch { repo.setWordWrap(value) }
    fun setLineNumbers(value: Boolean) = viewModelScope.launch { repo.setLineNumbers(value) }
    fun setKeepCopies(value: Boolean) = viewModelScope.launch { repo.setKeepCopies(value) }
    fun clearRecents() = viewModelScope.launch { library.clearRecents() }
}
