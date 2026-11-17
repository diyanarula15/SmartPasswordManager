// LOCKED API (owner: adapters worker): public constructor (context: Context). SKELETON STUB.
package com.example.group4app.integration

import android.content.Context
import com.legenddrry.feature.tools.contracts.SettingsRepository
import com.legenddrry.feature.tools.settings.InMemorySettingsRepository

/** Persists the tools-module ToolPreferences in app-private SharedPreferences (non-secret values only). */
class PreferencesSettingsRepository(context: Context) : SettingsRepository by InMemorySettingsRepository()
