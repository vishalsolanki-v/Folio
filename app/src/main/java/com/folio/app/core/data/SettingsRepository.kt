package com.folio.app.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User preferences (plan §2.5, §4.7). */
data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val paper: Paper = Paper.Auto,
    val keepCopies: Boolean = true,
    val editorFontSp: Int = 14,
    val wordWrap: Boolean = true,
    val lineNumbers: Boolean = true,
) {
    companion object {
        val EDITOR_FONT_RANGE = 12..20
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val paper = stringPreferencesKey("paper")
        val keepCopies = booleanPreferencesKey("keepCopies")
        val editorFontSp = intPreferencesKey("editorFontSp")
        val wordWrap = booleanPreferencesKey("wordWrap")
        val lineNumbers = booleanPreferencesKey("lineNumbers")
    }

    val settings: Flow<Settings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it } // corrupt file: fall back to defaults
        .map { p ->
            val defaults = Settings()
            Settings(
                theme = p[Keys.theme].toEnum(defaults.theme),
                paper = p[Keys.paper].toEnum(defaults.paper),
                keepCopies = p[Keys.keepCopies] ?: defaults.keepCopies,
                editorFontSp = (p[Keys.editorFontSp] ?: defaults.editorFontSp).coerceIn(Settings.EDITOR_FONT_RANGE),
                wordWrap = p[Keys.wordWrap] ?: defaults.wordWrap,
                lineNumbers = p[Keys.lineNumbers] ?: defaults.lineNumbers,
            )
        }

    suspend fun setTheme(value: ThemeMode) = store.edit { it[Keys.theme] = value.name }
    suspend fun setPaper(value: Paper) = store.edit { it[Keys.paper] = value.name }
    suspend fun setKeepCopies(value: Boolean) = store.edit { it[Keys.keepCopies] = value }
    suspend fun setEditorFontSp(value: Int) = store.edit { it[Keys.editorFontSp] = value.coerceIn(Settings.EDITOR_FONT_RANGE) }
    suspend fun setWordWrap(value: Boolean) = store.edit { it[Keys.wordWrap] = value }
    suspend fun setLineNumbers(value: Boolean) = store.edit { it[Keys.lineNumbers] = value }
}

private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
