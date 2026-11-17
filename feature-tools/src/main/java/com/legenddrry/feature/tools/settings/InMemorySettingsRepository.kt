package com.legenddrry.feature.tools.settings

import com.legenddrry.feature.tools.contracts.SettingsRepository
import com.legenddrry.feature.tools.contracts.ThemeMode
import com.legenddrry.feature.tools.contracts.ToolPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Session-scoped demo storage. A host may replace this with a DataStore adapter. */
class InMemorySettingsRepository(initial: ToolPreferences = ToolPreferences()) : SettingsRepository {
    private val mutablePreferences = MutableStateFlow(initial)
    override val preferences = mutablePreferences.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        mutablePreferences.update { it.copy(themeMode = mode) }
    }

    override suspend fun setAutoLockMinutes(minutes: Int) {
        require(minutes > 0) { "Choose a positive auto-lock duration." }
        mutablePreferences.update { it.copy(autoLockMinutes = minutes) }
    }

    override suspend fun setClipboardClearSeconds(seconds: Int) {
        require(seconds > 0) { "Choose a positive clipboard clearing duration." }
        mutablePreferences.update { it.copy(clipboardClearSeconds = seconds) }
    }

    override suspend fun setShowPasswords(show: Boolean) {
        mutablePreferences.update { it.copy(showPasswords = show) }
    }
}
