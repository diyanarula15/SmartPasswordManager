package com.example.group4app.e2e

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.group4app.MainActivity
import com.example.group4app.ui.shell.AppTestTags
import com.legenddrry.core.VaultEntry
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.tools.contracts.ThemeMode
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** External runner invokes seed, force-stops/relaunches the process, then invokes verify. */
@RunWith(AndroidJUnit4::class)
class ProcessPersistenceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun phase(expected: String) = assumeTrue("External process-restart phase", InstrumentationRegistry.getArguments().getString("persistencePhase") == expected)

    @Test fun seedPersistentState() = runBlocking {
        phase("seed")
        assertTrue(AppStateReset.resetToFreshInstall())
        val c = app.container
        withTimeout(30_000) { c.auth.state.first { it != AuthState.LOADING } }
        assertEquals(AuthResult.SUCCESS,c.auth.setUp(TEST_MASTER_PASSWORD.toCharArray()))
        val keep = VaultEntry("restart-keep","Restart kept","https://keep.example","first@example.com","Keep-Synthetic-42!","Keep notes Ω","School",100)
        val edited = keep.copy(id="restart-edit",title="Restart edited",password="Original-Synthetic-42!")
        val deleted = keep.copy(id="restart-delete",title="Restart deleted")
        c.vaultRepository.add(keep);c.vaultRepository.add(edited);c.vaultRepository.add(deleted)
        c.vaultRepository.update(edited.copy(username="edited@example.com",password="Edited-Synthetic-99!",notes="Edited notes 秘密",category="Work",updatedAtEpochMs=200))
        c.vaultRepository.delete(deleted.id)
        c.settings.setThemeMode(ThemeMode.DARK);c.settings.setAutoLockMinutes(15)
        c.settings.setClipboardClearSeconds(60);c.settings.setShowPasswords(true)
        val alias=c.aliases.create("Restart alias");c.aliases.setActive(alias.id,false)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.awaitTag(VaultTestTags.ENTRIES_SCREEN)
            compose.awaitText("Restart kept");compose.awaitText("Restart edited")
            assertEquals(2,c.vaultRepository.entries.first().size)
        }
    }

    @Test fun verifyPersistentStateAfterRealProcessRestart() = runBlocking {
        phase("verify")
        val c=app.container
        withTimeout(30_000) { c.auth.state.first { it != AuthState.LOADING } }
        assertEquals("Fresh process must require local authentication",AuthState.LOCKED,c.auth.state.value)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.awaitTag(AppTestTags.UNLOCK_SCREEN)
            compose.typeInto(AppTestTags.UNLOCK_PASSWORD,TEST_MASTER_PASSWORD)
            compose.click(AppTestTags.UNLOCK_SUBMIT)
            compose.awaitTag(VaultTestTags.ENTRIES_SCREEN,AUTH_WAIT_MS)
            val entries=c.vaultRepository.entries.first()
            assertEquals(setOf("restart-keep","restart-edit"),entries.map { it.id }.toSet())
            val edited=entries.single { it.id=="restart-edit" }
            assertEquals("edited@example.com",edited.username)
            assertTrue(edited.password=="Edited-Synthetic-99!")
            assertEquals("Edited notes 秘密",edited.notes);assertEquals("Work",edited.category);assertEquals(200L,edited.updatedAtEpochMs)
            val prefs=c.settings.preferences.value
            assertEquals(ThemeMode.DARK,prefs.themeMode);assertEquals(15,prefs.autoLockMinutes)
            assertEquals(60,prefs.clipboardClearSeconds);assertTrue(prefs.showPasswords)
            assertEquals(1,c.aliases.aliases.value.size);assertFalse(c.aliases.aliases.value.single().isActive)
            compose.clickText("Restart edited")
            compose.awaitTag(VaultTestTags.DETAIL_SCREEN)
            compose.await("Show passwords preference applies after restart") { compose.textOrNull(VaultTestTags.DETAIL_PASSWORD)=="Edited-Synthetic-99!" }
            assertEquals(Lifecycle.State.RESUMED,scenario.state)
        }
    }
}
