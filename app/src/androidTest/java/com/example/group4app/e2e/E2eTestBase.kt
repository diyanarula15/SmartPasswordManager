// Shared lifecycle + app-level flows for the end-to-end tests.
package com.example.group4app.e2e

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.group4app.MainActivity
import com.example.group4app.di.AppContainer
import com.example.group4app.ui.shell.AppTestTags
import com.legenddrry.core.VaultEntry
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule

abstract class E2eTestBase {

    @get:Rule
    val compose = createEmptyComposeRule()

    protected var scenario: ActivityScenario<MainActivity>? = null

    /** True when this test runs on wiped storage with a freshly built AppContainer. */
    protected var freshInstall = false

    protected val container: AppContainer get() = app.container
    protected val authState: AuthState get() = container.auth.state.value

    @Before
    fun resetAndLaunch() {
        // No activity is running here: safe to wipe storage and rebuild the container.
        freshInstall = AppStateReset.resetToFreshInstall()
        launchApp()
    }

    @After
    fun closeApp() {
        runCatching { scenario?.close() }
        scenario = null
    }

    protected fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    protected fun relaunchApp() {
        runCatching { scenario?.close() }
        launchApp()
    }

    /** True once Back (or anything else) closed MainActivity or sent it to the background. */
    protected val activityLeftForeground: Boolean
        get() = scenario?.state?.isAtLeast(Lifecycle.State.RESUMED) != true

    /**
     * System Back. Uses a real BACK key event when our window has focus; otherwise (e.g. a
     * system/ANR dialog from another app on a shared emulator holds focus) dispatches Back
     * through the activity's OnBackPressedDispatcher, which is what a system Back reaches.
     * Never throws when Back closes the app.
     */
    protected fun pressSystemBack() {
        var hasFocus = false
        runCatching { scenario?.onActivity { hasFocus = it.hasWindowFocus() } }
        if (hasFocus) {
            Espresso.pressBackUnconditionally()
        } else {
            runCatching { scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    // ---------------------------------------------------------------- auth

    protected fun awaitAuthSettled(): AuthState {
        compose.await("auth state to leave LOADING", AUTH_WAIT_MS) { authState != AuthState.LOADING }
        return authState
    }

    /** Brings the app to the unlocked vault from whatever persisted state it is in. */
    protected fun ensureUnlocked() {
        when (awaitAuthSettled()) {
            AuthState.NEEDS_SETUP -> completeSetup()
            AuthState.LOCKED -> unlockWithMasterPassword()
            AuthState.UNLOCKED -> Unit
            AuthState.LOADING -> fail("auth state stuck in LOADING")
        }
        awaitVaultShown()
    }

    protected fun completeSetup() {
        compose.awaitTag(AppTestTags.SETUP_SCREEN, AUTH_WAIT_MS)
        compose.typeInto(AppTestTags.SETUP_PASSWORD, TEST_MASTER_PASSWORD)
        compose.typeInto(AppTestTags.SETUP_CONFIRM, TEST_MASTER_PASSWORD)
        compose.click(AppTestTags.SETUP_SUBMIT)
        compose.await("setup with a valid matching password to unlock the vault", AUTH_WAIT_MS) {
            authState == AuthState.UNLOCKED
        }
    }

    protected fun unlockWithMasterPassword() {
        compose.awaitTag(AppTestTags.UNLOCK_SCREEN, AUTH_WAIT_MS)
        compose.typeInto(AppTestTags.UNLOCK_PASSWORD, TEST_MASTER_PASSWORD)
        compose.click(AppTestTags.UNLOCK_SUBMIT)
        try {
            compose.await("unlock with the e2e master password", AUTH_WAIT_MS) { authState == AuthState.UNLOCKED }
        } catch (e: AssertionError) {
            throw AssertionError(
                "Could not unlock with the e2e test master password (state=$authState). If storage was not " +
                    "reset (freshInstall=$freshInstall), a previous run or manual use probably set a different " +
                    "master password: run `adb shell pm clear com.example.group4app` and retry.",
                e,
            )
        }
    }

    protected fun awaitVaultShown() {
        compose.awaitTag(AppTestTags.MAIN_SHELL, AUTH_WAIT_MS)
        compose.awaitTag(VaultTestTags.ENTRIES_SCREEN, AUTH_WAIT_MS)
    }

    // ---------------------------------------------------------------- navigation

    protected fun openVaultTab() {
        compose.click(AppTestTags.NAV_VAULT)
        compose.awaitTag(VaultTestTags.ENTRIES_SCREEN)
    }

    protected fun openGenerateTab() {
        compose.click(AppTestTags.NAV_GENERATE)
        compose.awaitTag(VaultTestTags.GENERATOR_SCREEN)
    }

    protected fun openApprovalsTab() {
        compose.click(AppTestTags.NAV_APPROVALS)
        compose.awaitTag(ToolTags.CREATE_DEMO_REQUEST)
    }

    protected fun openAliasesTab() {
        compose.click(AppTestTags.NAV_ALIASES)
        compose.awaitTag(ToolTags.ALIASES_CREATE)
    }

    protected fun openMoreTab() {
        compose.click(AppTestTags.NAV_MORE)
        compose.awaitTag(AppTestTags.MORE_SCREEN)
    }

    protected fun openSecurityDashboard() {
        openMoreTab()
        compose.click(AppTestTags.MORE_SECURITY)
        compose.await("security dashboard to show a total", WAIT_MS) { compose.intOf(ToolTags.SECURITY_TOTAL) != null }
    }

    protected fun openSettings() {
        openMoreTab()
        compose.click(AppTestTags.MORE_SETTINGS)
        compose.awaitTag(ToolTags.THEME_DARK)
    }

    protected fun backToMore() {
        compose.click(AppTestTags.SUBSCREEN_BACK)
        compose.awaitTag(AppTestTags.MORE_SCREEN)
    }

    // ---------------------------------------------------------------- vault

    protected fun vaultEntries(): List<VaultEntry> =
        runBlocking { withTimeout(5_000) { container.vaultRepository.entries.first() } }

    /** Adds an entry through the real UI (Vault tab → FAB → editor) and waits for its row. */
    protected fun addEntryViaUi(title: String, username: String, password: String) {
        openVaultTab()
        compose.click(VaultTestTags.ADD_FAB)
        compose.awaitTag(VaultTestTags.EDITOR)
        compose.typeInto(VaultTestTags.EDITOR_TITLE, title)
        compose.typeInto(VaultTestTags.EDITOR_USERNAME, username)
        compose.typeInto(VaultTestTags.EDITOR_PASSWORD, password)
        compose.typeInto(VaultTestTags.EDITOR_NOTES, "Saved from test")
        compose.click(VaultTestTags.EDITOR_SAVE)
        compose.awaitTagGone(VaultTestTags.EDITOR)
        compose.await("repository to contain '$title'") { vaultEntries().any { it.title == title } }
        compose.awaitText(title)
    }
}

/** Selectors from feature-tools present in both its committed and newest versions. */
internal object ToolTags {
    const val CREATE_DEMO_REQUEST = "create_demo_request"
    const val APPROVAL_FEEDBACK = "approval_feedback"
    fun approve(id: String) = "approve_$id"
    fun deny(id: String) = "deny_$id"
    fun requestDetails(id: String) = "request_details_$id"

    const val ALIASES_CREATE = "aliases-create"
    const val ALIASES_SERVICE = "aliases-service"
    const val ALIASES_CONFIRM_CREATE = "aliases-confirm-create"
    const val ALIASES_CONFIRM_DELETE = "aliases-confirm-delete"
    const val ALIASES_SEARCH = "aliases-search"
    const val ALIASES_FEEDBACK = "aliases-feedback"
    fun aliasCopy(id: String) = "alias-copy-$id"
    fun aliasToggle(id: String) = "alias-toggle-$id"
    fun aliasDelete(id: String) = "alias-delete-$id"

    const val SECURITY_TOTAL = "security_total"
    const val SECURITY_WEAK = "security_weak"
    const val SECURITY_REUSED = "security_reused"

    const val THEME_SYSTEM = "theme_system"
    const val THEME_LIGHT = "theme_light"
    const val THEME_DARK = "theme_dark"
    const val AUTO_LOCK_PREFERENCE = "auto_lock_preference"
    const val CLIPBOARD_PREFERENCE = "clipboard_preference"
    const val SHOW_PASSWORDS_PREFERENCE = "show_passwords_preference"
}
