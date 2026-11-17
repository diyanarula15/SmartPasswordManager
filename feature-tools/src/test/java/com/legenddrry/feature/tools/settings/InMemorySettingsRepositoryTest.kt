package com.legenddrry.feature.tools.settings

import com.legenddrry.feature.tools.contracts.ThemeMode
import com.legenddrry.feature.tools.contracts.ToolPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySettingsRepositoryTest {
    @Test fun defaultsAreSafeAndDocumented() {
        val preferences = InMemorySettingsRepository().preferences.value
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode)
        assertEquals(5, preferences.autoLockMinutes)
        assertEquals(30, preferences.clipboardClearSeconds)
        assertFalse(preferences.showPasswords)
    }

    @Test fun customInitialValuesArePreserved() {
        val initial = ToolPreferences(ThemeMode.DARK, 15, 60, true)
        assertEquals(initial, InMemorySettingsRepository(initial).preferences.value)
    }

    @Test fun allThemeModesCanBeSelected() = runBlocking {
        val repository = InMemorySettingsRepository()
        ThemeMode.entries.forEach {
            repository.setThemeMode(it)
            assertEquals(it, repository.preferences.value.themeMode)
        }
    }

    @Test fun allPreferencesUpdateWithoutOverwritingOtherFields() = runBlocking {
        val repository = InMemorySettingsRepository()
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setAutoLockMinutes(15)
        repository.setClipboardClearSeconds(60)
        repository.setShowPasswords(true)
        assertEquals(ToolPreferences(ThemeMode.LIGHT, 15, 60, true), repository.preferences.value)
        repository.setShowPasswords(false)
        assertFalse(repository.preferences.value.showPasswords)
    }

    @Test fun concurrentIndependentUpdatesAreAtomic() = runBlocking {
        repeat(50) {
            val repository = InMemorySettingsRepository()
            coroutineScope {
                launch(Dispatchers.Default) { repository.setThemeMode(ThemeMode.DARK) }
                launch(Dispatchers.Default) { repository.setAutoLockMinutes(30) }
                launch(Dispatchers.Default) { repository.setClipboardClearSeconds(120) }
                launch(Dispatchers.Default) { repository.setShowPasswords(true) }
            }
            assertEquals(ToolPreferences(ThemeMode.DARK, 30, 120, true), repository.preferences.value)
        }
    }

    @Test fun invalidDurationsFailWithoutChangingPreferences() = runBlocking {
        val repository = InMemorySettingsRepository()
        listOf(0, -1).forEach { invalid ->
            var lockRejected = false
            try { repository.setAutoLockMinutes(invalid) } catch (_: IllegalArgumentException) { lockRejected = true }
            assertTrue(lockRejected)
            var clipboardRejected = false
            try { repository.setClipboardClearSeconds(invalid) } catch (_: IllegalArgumentException) { clipboardRejected = true }
            assertTrue(clipboardRejected)
        }
        assertEquals(ToolPreferences(), repository.preferences.value)
    }

    @Test fun observersReceiveThemeChangesImmediately() = runBlocking {
        val repository = InMemorySettingsRepository()
        val observed = mutableListOf<ToolPreferences>()
        val observer = launch { repository.preferences.take(2).toList(observed) }
        yield()
        repository.setThemeMode(ThemeMode.DARK)
        observer.join()
        assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.DARK), observed.map { it.themeMode })
    }
}
