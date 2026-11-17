package com.example.group4app.integration

import android.content.Context
import android.content.SharedPreferences
import com.legenddrry.feature.tools.contracts.SettingsRepository
import com.legenddrry.feature.tools.contracts.ThemeMode
import com.legenddrry.feature.tools.contracts.ToolPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists the tools-module [ToolPreferences] in app-private SharedPreferences
 * ("legenddrry_settings", MODE_PRIVATE). Non-secret values only.
 *
 * The [preferences] StateFlow is initialised synchronously from disk at construction; missing,
 * mistyped or out-of-range stored values fall back to the [ToolPreferences] defaults per field.
 * Setters validate exactly like the in-memory repository (IllegalArgumentException for
 * non-positive durations), publish the new value immediately and persist it.
 */
class PreferencesSettingsRepository internal constructor(
    private val store: SettingsKeyValueStore,
) : SettingsRepository {

    constructor(context: Context) : this(
        SharedPreferencesKeyValueStore(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        )
    )

    private val mutex = Mutex()
    private val mutablePreferences = MutableStateFlow(load(store))
    override val preferences: StateFlow<ToolPreferences> = mutablePreferences.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) = mutate { it.copy(themeMode = mode) }

    override suspend fun setAutoLockMinutes(minutes: Int) {
        require(minutes > 0) { "Choose a positive auto-lock duration." }
        mutate { it.copy(autoLockMinutes = minutes) }
    }

    override suspend fun setClipboardClearSeconds(seconds: Int) {
        require(seconds > 0) { "Choose a positive clipboard clearing duration." }
        mutate { it.copy(clipboardClearSeconds = seconds) }
    }

    override suspend fun setShowPasswords(show: Boolean) = mutate { it.copy(showPasswords = show) }

    private suspend fun mutate(transform: (ToolPreferences) -> ToolPreferences) = mutex.withLock {
        val updated = transform(mutablePreferences.value)
        mutablePreferences.value = updated
        store.write(
            mapOf(
                KEY_THEME_MODE to updated.themeMode.name,
                KEY_AUTO_LOCK_MINUTES to updated.autoLockMinutes,
                KEY_CLIPBOARD_CLEAR_SECONDS to updated.clipboardClearSeconds,
                KEY_SHOW_PASSWORDS to updated.showPasswords,
            )
        )
    }

    internal companion object {
        const val PREFS_NAME = "legenddrry_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AUTO_LOCK_MINUTES = "auto_lock_minutes"
        const val KEY_CLIPBOARD_CLEAR_SECONDS = "clipboard_clear_seconds"
        const val KEY_SHOW_PASSWORDS = "show_passwords"

        fun load(store: SettingsKeyValueStore): ToolPreferences {
            val defaults = ToolPreferences()
            val theme = store.getString(KEY_THEME_MODE)
                ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode
            val autoLock = store.getInt(KEY_AUTO_LOCK_MINUTES)?.takeIf { it > 0 }
                ?: defaults.autoLockMinutes
            val clipboard = store.getInt(KEY_CLIPBOARD_CLEAR_SECONDS)?.takeIf { it > 0 }
                ?: defaults.clipboardClearSeconds
            val show = store.getBoolean(KEY_SHOW_PASSWORDS) ?: defaults.showPasswords
            return ToolPreferences(theme, autoLock, clipboard, show)
        }
    }
}

/** Minimal key-value persistence seam so the repository is testable on the JVM. */
internal interface SettingsKeyValueStore {
    /** Returns null when absent or stored with a different type. */
    fun getString(key: String): String?
    fun getInt(key: String): Int?
    fun getBoolean(key: String): Boolean?

    /** Writes all values together. Supported value types: String, Int, Boolean. */
    fun write(values: Map<String, Any>)
}

internal class SharedPreferencesKeyValueStore(
    private val prefs: SharedPreferences,
) : SettingsKeyValueStore {
    override fun getString(key: String): String? = typed { prefs.getString(key, null) }
    override fun getInt(key: String): Int? =
        typed { if (prefs.contains(key)) prefs.getInt(key, 0) else null }
    override fun getBoolean(key: String): Boolean? =
        typed { if (prefs.contains(key)) prefs.getBoolean(key, false) else null }

    override fun write(values: Map<String, Any>) {
        val editor = prefs.edit()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Boolean -> editor.putBoolean(key, value)
                else -> throw IllegalArgumentException("Unsupported preference type for $key")
            }
        }
        // apply(): in-memory SharedPreferences updated synchronously, disk write is async and
        // flushed by the framework before the process is considered stopped.
        editor.apply()
    }

    private inline fun <T> typed(read: () -> T?): T? =
        try { read() } catch (_: ClassCastException) { null }
}

internal class InMemoryKeyValueStore(initial: Map<String, Any> = emptyMap()) : SettingsKeyValueStore {
    private val values = LinkedHashMap<String, Any>(initial)

    @Synchronized override fun getString(key: String): String? = values[key] as? String
    @Synchronized override fun getInt(key: String): Int? = values[key] as? Int
    @Synchronized override fun getBoolean(key: String): Boolean? = values[key] as? Boolean
    @Synchronized override fun write(values: Map<String, Any>) { this.values.putAll(values) }
    @Synchronized fun snapshot(): Map<String, Any> = values.toMap()
}
