package com.example.group4app.integration

import com.example.group4app.integration.PreferencesSettingsRepository.Companion.KEY_AUTO_LOCK_MINUTES
import com.example.group4app.integration.PreferencesSettingsRepository.Companion.KEY_CLIPBOARD_CLEAR_SECONDS
import com.example.group4app.integration.PreferencesSettingsRepository.Companion.KEY_SHOW_PASSWORDS
import com.example.group4app.integration.PreferencesSettingsRepository.Companion.KEY_THEME_MODE
import com.legenddrry.feature.tools.contracts.ThemeMode
import com.legenddrry.feature.tools.contracts.ToolPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PreferencesSettingsRepositoryTest {

    @Test
    fun emptyStoreYieldsDefaults() {
        val repo = PreferencesSettingsRepository(InMemoryKeyValueStore())
        assertEquals(ToolPreferences(), repo.preferences.value)
        assertEquals(ThemeMode.SYSTEM, repo.preferences.value.themeMode)
        assertEquals(5, repo.preferences.value.autoLockMinutes)
        assertEquals(30, repo.preferences.value.clipboardClearSeconds)
        assertEquals(false, repo.preferences.value.showPasswords)
    }

    @Test
    fun eachSetterUpdatesFlowImmediatelyAndPersistsAcrossInstances() = runTest {
        val store = InMemoryKeyValueStore()
        val repo = PreferencesSettingsRepository(store)

        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.preferences.value.themeMode)
        repo.setAutoLockMinutes(15)
        assertEquals(15, repo.preferences.value.autoLockMinutes)
        repo.setClipboardClearSeconds(90)
        assertEquals(90, repo.preferences.value.clipboardClearSeconds)
        repo.setShowPasswords(true)
        assertEquals(true, repo.preferences.value.showPasswords)

        val reopened = PreferencesSettingsRepository(store)
        assertEquals(ToolPreferences(ThemeMode.DARK, 15, 90, true), reopened.preferences.value)
        assertEquals("DARK", store.snapshot()[KEY_THEME_MODE])
    }

    @Test
    fun eachSetterPersistsIndividually() = runTest {
        val store = InMemoryKeyValueStore()
        PreferencesSettingsRepository(store).setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, PreferencesSettingsRepository(store).preferences.value.themeMode)
        PreferencesSettingsRepository(store).setAutoLockMinutes(1)
        assertEquals(1, PreferencesSettingsRepository(store).preferences.value.autoLockMinutes)
        PreferencesSettingsRepository(store).setClipboardClearSeconds(10)
        assertEquals(10, PreferencesSettingsRepository(store).preferences.value.clipboardClearSeconds)
        PreferencesSettingsRepository(store).setShowPasswords(true)
        val last = PreferencesSettingsRepository(store).preferences.value
        assertEquals(ToolPreferences(ThemeMode.LIGHT, 1, 10, true), last)
    }

    @Test
    fun invalidDurationsRejectedAndNothingChanges() = runTest {
        val store = InMemoryKeyValueStore()
        val repo = PreferencesSettingsRepository(store)
        for (bad in listOf(0, -1, Int.MIN_VALUE)) {
            expectIllegalArgument { repo.setAutoLockMinutes(bad) }
            expectIllegalArgument { repo.setClipboardClearSeconds(bad) }
        }
        assertEquals(ToolPreferences(), repo.preferences.value)
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun invalidOrMistypedStoredValuesFallBackToDefaultsPerField() {
        val store = InMemoryKeyValueStore(
            mapOf(
                KEY_THEME_MODE to "PURPLE",
                KEY_AUTO_LOCK_MINUTES to 0,
                KEY_CLIPBOARD_CLEAR_SECONDS to "thirty",
                KEY_SHOW_PASSWORDS to true,
            )
        )
        val repo = PreferencesSettingsRepository(store)
        assertEquals(ToolPreferences(showPasswords = true), repo.preferences.value)
    }

    private suspend fun expectIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
