package com.folio.app.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.folio.app.core.designsystem.Paper
import com.folio.app.core.designsystem.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.store() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        tmp.root.resolve("settings.preferences_pb")
    }

    @Test fun defaultsWhenEmpty() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(Settings(), SettingsRepository(store()).settings.first())
    }

    @Test fun savesAndReadsBack() = runTest(UnconfinedTestDispatcher()) {
        val repo = SettingsRepository(store())
        repo.setTheme(ThemeMode.Black)
        repo.setPaper(Paper.Sepia)
        repo.setKeepCopies(false)
        repo.setEditorFontSp(18)
        repo.setWordWrap(false)
        repo.setLineNumbers(false)
        assertEquals(Settings(ThemeMode.Black, Paper.Sepia, false, 18, false, false), repo.settings.first())
    }

    @Test fun fontSizeIsClamped() = runTest(UnconfinedTestDispatcher()) {
        val repo = SettingsRepository(store())
        repo.setEditorFontSp(99)
        assertEquals(20, repo.settings.first().editorFontSp)
    }

    @Test fun unknownStoredEnumFallsBackToDefault() = runTest(UnconfinedTestDispatcher()) {
        val store = store()
        store.edit { it[stringPreferencesKey("theme")] = "Purple" }
        assertEquals(ThemeMode.System, SettingsRepository(store).settings.first().theme)
    }
}
