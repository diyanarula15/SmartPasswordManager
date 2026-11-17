package com.example.group4app.integration

import com.legenddrry.feature.tools.contracts.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SettingsWriteFailureTest {
    @Test fun failedDurableWriteDoesNotPublishFalseSuccess() = runBlocking {
        val backing=InMemoryKeyValueStore()
        val store=object:SettingsKeyValueStore by backing {
            override fun write(values:Map<String,Any>) { throw IOException("Synthetic disk failure") }
        }
        val repo=PreferencesSettingsRepository(store)
        val before=repo.preferences.value
        try { repo.setThemeMode(ThemeMode.DARK);fail("Write must report failure") } catch(_:IOException) { }
        assertEquals(before,repo.preferences.value)
        assertTrue(backing.snapshot().isEmpty())
    }
}
