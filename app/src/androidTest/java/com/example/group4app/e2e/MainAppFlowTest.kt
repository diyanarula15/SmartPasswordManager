// End-to-end tests of the integrated app: auth gate, vault, generator and global navigation.
// Every test starts from wiped storage + a fresh AppContainer (see AppStateReset), so the
// order does not matter; NAME_ASCENDING only keeps reports stable.
package com.example.group4app.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.group4app.ui.shell.AppTestTags
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.vault.generator.PasswordGenerator
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MainAppFlowTest : E2eTestBase() {

    private companion object {
        // Non-sensitive test values.
        const val DEMO_TITLE = "Demo Account"
        const val DEMO_USERNAME = "demo@example.com"
        const val DEMO_USERNAME_EDITED = "demo.edited@example.com"
        const val DEMO_PASSWORD = "Demo-Entry-Pass-42!"
        const val GENERATED_TITLE = "Generated Login"
    }

    // ------------------------------------------------------------------ 1. setup

    @Test
    fun test01_setup_rejectsShortAndMismatched_thenLandsInVault() {
        assertTrue(
            "This test needs a fresh install; storage reset failed: ${AppStateReset.lastFailure}",
            freshInstall,
        )
        assertEquals("fresh install must need setup", AuthState.NEEDS_SETUP, awaitAuthSettled())
        compose.awaitTag(AppTestTags.SETUP_SCREEN, AUTH_WAIT_MS)
        assertFalse("vault must not be visible before setup", compose.exists(AppTestTags.MAIN_SHELL))

        // Mismatched confirmation (both long enough).
        compose.typeInto(AppTestTags.SETUP_PASSWORD, TEST_MASTER_PASSWORD)
        compose.typeInto(AppTestTags.SETUP_CONFIRM, TEST_MASTER_PASSWORD + "x")
        compose.click(AppTestTags.SETUP_SUBMIT)
        compose.awaitTag(AppTestTags.SETUP_ERROR)
        compose.assertStays("mismatched confirmation must not create the vault") { authState == AuthState.NEEDS_SETUP }
        assertFalse(compose.exists(AppTestTags.MAIN_SHELL))
        assertTrue(compose.exists(AppTestTags.SETUP_SCREEN))

        // Too short (matching) password.
        val tooShort = "Ab1!".padEnd(AuthManager.MIN_MASTER_PASSWORD_LENGTH - 1, 'z')
        compose.typeInto(AppTestTags.SETUP_PASSWORD, tooShort)
        compose.typeInto(AppTestTags.SETUP_CONFIRM, tooShort)
        compose.click(AppTestTags.SETUP_SUBMIT)
        compose.awaitTag(AppTestTags.SETUP_ERROR)
        compose.assertStays("too-short password must not create the vault") { authState == AuthState.NEEDS_SETUP }
        assertFalse(compose.exists(AppTestTags.MAIN_SHELL))

        // Valid.
        completeSetup()
        awaitVaultShown()
        compose.awaitTagGone(AppTestTags.SETUP_SCREEN)
        assertEquals(AuthState.UNLOCKED, authState)
        assertTrue("an empty vault starts empty", vaultEntries().isEmpty())
    }

    // ------------------------------------------------------------------ 2. lock / unlock

    @Test
    fun test02_lock_wrongPasswordRejected_backDoesNotReveal_correctPasswordUnlocks() {
        ensureUnlocked()
        openMoreTab()
        compose.click(AppTestTags.MORE_LOCK)
        compose.awaitTag(AppTestTags.UNLOCK_SCREEN, AUTH_WAIT_MS)
        assertEquals(AuthState.LOCKED, authState)
        compose.awaitTagGone(AppTestTags.MAIN_SHELL)
        assertFalse("vault list must be hidden while locked", compose.exists(VaultTestTags.ENTRIES_SCREEN))

        // Wrong password.
        compose.typeInto(AppTestTags.UNLOCK_PASSWORD, "Wrong-Password-000")
        compose.click(AppTestTags.UNLOCK_SUBMIT)
        compose.awaitTag(AppTestTags.UNLOCK_ERROR, AUTH_WAIT_MS)
        compose.assertStays("wrong password must keep the vault locked") { authState == AuthState.LOCKED }
        assertFalse(compose.exists(AppTestTags.MAIN_SHELL))
        assertTrue(compose.exists(AppTestTags.UNLOCK_SCREEN))

        // System Back on the unlock screen: either the app closes or it stays on the unlock screen.
        pressSystemBack()
        compose.assertStays("Back must neither unlock nor reveal the vault") {
            authState == AuthState.LOCKED && !compose.exists(AppTestTags.MAIN_SHELL)
        }
        if (activityLeftForeground) relaunchApp() // Back closed / backgrounded the app: reopen it.
        compose.awaitTag(AppTestTags.UNLOCK_SCREEN, AUTH_WAIT_MS)
        assertEquals("Back must not unlock", AuthState.LOCKED, authState)
        assertFalse("Back must not reveal the vault", compose.exists(AppTestTags.MAIN_SHELL))
        assertFalse(compose.exists(VaultTestTags.ENTRIES_SCREEN))

        // Correct password.
        unlockWithMasterPassword()
        awaitVaultShown()
        compose.awaitTagGone(AppTestTags.UNLOCK_SCREEN)
    }

    // ------------------------------------------------------------------ 3. vault CRUD

    @Test
    fun test03_vault_addSearchDetailEditRevealCopy() {
        ensureUnlocked()
        addEntryViaUi(DEMO_TITLE, DEMO_USERNAME, DEMO_PASSWORD)
        val saved = vaultEntries().single { it.title == DEMO_TITLE }
        assertEquals(DEMO_USERNAME, saved.username)
        assertTrue("stored password must equal the typed one", saved.password == DEMO_PASSWORD)

        // Search: no match, then match by title and by username.
        compose.typeInto(VaultTestTags.SEARCH_FIELD, "zzqx-no-such-entry")
        compose.awaitTag(VaultTestTags.NO_RESULTS_STATE)
        compose.awaitTextGone(DEMO_TITLE)
        compose.typeInto(VaultTestTags.SEARCH_FIELD, "demo acc")
        compose.awaitText(DEMO_TITLE)
        compose.typeInto(VaultTestTags.SEARCH_FIELD, "demo@example")
        compose.awaitText(DEMO_TITLE)
        compose.awaitTagGone(VaultTestTags.NO_RESULTS_STATE)

        // Detail.
        compose.clickText(DEMO_TITLE)
        compose.awaitTag(VaultTestTags.DETAIL_SCREEN)
        assertTrue(compose.textOf(VaultTestTags.DETAIL_TITLE).contains(DEMO_TITLE))
        assertTrue(compose.textOf(VaultTestTags.DETAIL_USERNAME).contains(DEMO_USERNAME))

        // Edit username.
        compose.click(VaultTestTags.DETAIL_EDIT)
        compose.awaitTag(VaultTestTags.EDITOR)
        compose.typeInto(VaultTestTags.EDITOR_USERNAME, DEMO_USERNAME_EDITED)
        compose.click(VaultTestTags.EDITOR_SAVE)
        compose.awaitTagGone(VaultTestTags.EDITOR)
        compose.await("detail to show the edited username") {
            compose.textOrNull(VaultTestTags.DETAIL_USERNAME)?.contains(DEMO_USERNAME_EDITED) == true
        }
        compose.await("repository to store the edited username") {
            vaultEntries().singleOrNull { it.title == DEMO_TITLE }?.username == DEMO_USERNAME_EDITED
        }
        assertEquals("edit must not duplicate the entry", 1, vaultEntries().count { it.title == DEMO_TITLE })

        // Show / hide password.
        compose.await("password to start masked") { compose.textOrNull(VaultTestTags.DETAIL_PASSWORD) == MASKED_PASSWORD }
        compose.click(VaultTestTags.DETAIL_TOGGLE_VISIBILITY)
        compose.await("password to be revealed") { compose.textOrNull(VaultTestTags.DETAIL_PASSWORD) == DEMO_PASSWORD }
        compose.click(VaultTestTags.DETAIL_TOGGLE_VISIBILITY)
        compose.await("password to be masked again") { compose.textOrNull(VaultTestTags.DETAIL_PASSWORD) == MASKED_PASSWORD }

        // Copy (clipboard seam is internal to feature-vault; assert the snackbar instead).
        compose.click(VaultTestTags.DETAIL_COPY_PASSWORD)
        compose.awaitText(VaultTestTags.MSG_PASSWORD_COPIED)

        compose.click(VaultTestTags.DETAIL_CLOSE)
        compose.awaitTagGone(VaultTestTags.DETAIL_SCREEN)
    }

    // ------------------------------------------------------------------ 4. generator

    @Test
    fun test04_generator_length20_allClasses_copy_saveSharedWithVault() {
        ensureUnlocked()
        openGenerateTab()
        compose.await("a generated password") { !compose.textOrNull(VaultTestTags.GENERATOR_PASSWORD).isNullOrEmpty() }

        compose.node(VaultTestTags.GENERATOR_LENGTH_SLIDER)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        compose.await("length label to show 20") {
            compose.textOrNull(VaultTestTags.GENERATOR_LENGTH_LABEL)?.contains("20") == true
        }
        compose.await("generated password to be 20 characters") {
            compose.textOrNull(VaultTestTags.GENERATOR_PASSWORD)?.length == 20
        }
        val generated = compose.textOf(VaultTestTags.GENERATOR_PASSWORD)
        assertEquals(20, generated.length)
        assertTrue("generated password lacks an uppercase letter", generated.any { it in PasswordGenerator.UPPERCASE })
        assertTrue("generated password lacks a lowercase letter", generated.any { it in PasswordGenerator.LOWERCASE })
        assertTrue("generated password lacks a digit", generated.any { it in PasswordGenerator.DIGITS })
        assertTrue("generated password lacks a symbol", generated.any { it in PasswordGenerator.SYMBOLS })

        compose.click(VaultTestTags.GENERATOR_COPY)
        compose.awaitText(VaultTestTags.MSG_PASSWORD_COPIED)

        val shown = compose.textOf(VaultTestTags.GENERATOR_PASSWORD)
        compose.click(VaultTestTags.GENERATOR_SAVE)
        compose.awaitTag(VaultTestTags.EDITOR)
        compose.typeInto(VaultTestTags.EDITOR_TITLE, GENERATED_TITLE)
        compose.typeInto(VaultTestTags.EDITOR_NOTES, "Generated account")
        compose.click(VaultTestTags.EDITOR_SAVE)
        compose.awaitTagGone(VaultTestTags.EDITOR)
        compose.await("generated entry to be saved in the shared repository") {
            vaultEntries().any { it.title == GENERATED_TITLE && it.password == shown }
        }

        openVaultTab()
        compose.awaitText(GENERATED_TITLE)
    }

    // ------------------------------------------------------------------ 9. navigation

    @Test
    fun test09a_navigation_allTabsRepeatedly_singleNavigationBar() {
        ensureUnlocked()
        repeat(3) { round ->
            openVaultTab()
            openGenerateTab()
            openApprovalsTab()
            openAliasesTab()
            openMoreTab()
            assertTrue("main shell must stay visible (round $round)", compose.exists(AppTestTags.MAIN_SHELL))
            listOf(
                AppTestTags.NAV_VAULT, AppTestTags.NAV_GENERATE, AppTestTags.NAV_APPROVALS,
                AppTestTags.NAV_ALIASES, AppTestTags.NAV_MORE,
            ).forEach { tag ->
                assertEquals("exactly one '$tag' (one global NavigationBar)", 1, compose.nodesWithTag(tag).size)
            }
        }
        openVaultTab()
    }

    @Test
    fun test09b_navigation_backFromNonVaultTabReturnsToVault() {
        ensureUnlocked()
        val tabs = listOf<Pair<String, () -> Unit>>(
            "Generate" to ::openGenerateTab,
            "Approvals" to ::openApprovalsTab,
            "Aliases" to ::openAliasesTab,
            "More" to ::openMoreTab,
        )
        for ((name, open) in tabs) {
            open()
            pressSystemBack()
            compose.await("Back from $name to show the Vault tab", WAIT_MS) {
                activityLeftForeground || compose.exists(VaultTestTags.ENTRIES_SCREEN)
            }
            assertFalse("Back from $name closed the app instead of returning to Vault", activityLeftForeground)
            compose.awaitTag(VaultTestTags.ENTRIES_SCREEN)
            assertTrue(compose.exists(AppTestTags.MAIN_SHELL))
            assertEquals(AuthState.UNLOCKED, authState)
        }
    }

    @Test
    fun test09c_navigation_moreHubAboutAndSubscreenBack() {
        ensureUnlocked()
        openMoreTab()
        compose.click(AppTestTags.MORE_ABOUT)
        compose.awaitTag(AppTestTags.ABOUT_SCREEN)
        backToMore()
        compose.awaitTagGone(AppTestTags.ABOUT_SCREEN)

        compose.click(AppTestTags.MORE_SECURITY)
        compose.awaitTag(ToolTags.SECURITY_TOTAL)
        backToMore()

        compose.click(AppTestTags.MORE_SETTINGS)
        compose.awaitTag(ToolTags.THEME_DARK)
        backToMore()
        compose.awaitTagGone(ToolTags.THEME_DARK)
    }
}
