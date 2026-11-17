package com.example.group4app.e2e

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.group4app.integration.PreferencesSettingsRepository
import com.example.group4app.ui.shell.AppTestTags
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.tools.contracts.ThemeMode
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Real MainActivity, encrypted vault and persistent settings; no device-wide configuration edits. */
@RunWith(AndroidJUnit4::class)
class LifecycleAndLayoutTest : E2eTestBase() {
    @Test fun recreationRetainsUnlockedSessionSavedEntryAndSettings() {
        ensureUnlocked()
        addEntryViaUi("Recreated account", "student@example.com", "Lifecycle-Entry-42!")
        val saved = vaultEntries().single()
        openSettings()
        compose.click(ToolTags.THEME_DARK)
        compose.click(ToolTags.AUTO_LOCK_PREFERENCE)
        compose.clickText("15 minutes")
        compose.click(ToolTags.CLIPBOARD_PREFERENCE)
        compose.clickText("60 seconds")
        compose.await("new settings to be published") {
            val preferences = container.settings.preferences.value
            preferences.themeMode == ThemeMode.DARK && preferences.autoLockMinutes == 15 &&
                preferences.clipboardClearSeconds == 60
        }
        val expected = container.settings.preferences.value

        scenario!!.recreate()
        compose.awaitTag(ToolTags.THEME_DARK, AUTH_WAIT_MS)
        assertEquals(AuthState.UNLOCKED, authState)
        assertTrue(compose.isSelected(ToolTags.THEME_DARK))
        assertEquals(expected, container.settings.preferences.value)
        // Construct another real adapter to prove this is persisted rather than only a UI StateFlow.
        assertEquals(expected, PreferencesSettingsRepository(app).preferences.value)
        assertTrue("Saved record fields must survive recreation", vaultEntries().singleOrNull() == saved)
        openVaultTab()
        compose.clickText(saved.title)
        compose.awaitTag(VaultTestTags.DETAIL_SCREEN)
        assertTrue(compose.textOf(VaultTestTags.DETAIL_USERNAME).contains("student@example.com"))
        compose.click(VaultTestTags.DETAIL_CLOSE)
    }

    @Test fun rotationAndRecreationPreserveUnsavedVaultAndGeneratedDrafts() {
        ensureUnlocked()
        val originalOrientation = requestedOrientation()
        try {
            openVaultTab()
            compose.click(VaultTestTags.ADD_FAB)
            compose.typeInto(VaultTestTags.EDITOR_TITLE, "Unsaved rotation draft")
            compose.typeInto(VaultTestTags.EDITOR_WEBSITE, "https://school.example")
            compose.typeInto(VaultTestTags.EDITOR_USERNAME, "rotation@example.com")
            compose.typeInto(VaultTestTags.EDITOR_PASSWORD, "Draft-Secret-42!")
            compose.typeInto(VaultTestTags.EDITOR_NOTES, "Not saved before rotation")
            assertTrue("typing must not write an incomplete record", vaultEntries().isEmpty())

            requestLandscape()
            compose.awaitTag(VaultTestTags.EDITOR)
            assertEquals(AuthState.UNLOCKED, authState)
            assertEquals("Unsaved rotation draft", compose.textOf(VaultTestTags.EDITOR_TITLE))
            assertEquals("https://school.example", compose.textOf(VaultTestTags.EDITOR_WEBSITE))
            assertEquals("rotation@example.com", compose.textOf(VaultTestTags.EDITOR_USERNAME))
            assertEquals("Not saved before rotation", compose.textOf(VaultTestTags.EDITOR_NOTES))
            compose.click(VaultTestTags.EDITOR_PASSWORD_TOGGLE)
            compose.await("unsaved password to survive rotation") {
                compose.textOrNull(VaultTestTags.EDITOR_PASSWORD) == "Draft-Secret-42!"
            }
            compose.click(VaultTestTags.EDITOR_SAVE)
            compose.awaitTagGone(VaultTestTags.EDITOR)
            compose.await("rotated draft to save exactly once") { vaultEntries().size == 1 }
            assertTrue("saved password must retain the typed value", vaultEntries().single().password == "Draft-Secret-42!")

            openGenerateTab()
            val generated = compose.textOf(VaultTestTags.GENERATOR_PASSWORD)
            compose.click(VaultTestTags.GENERATOR_SAVE)
            compose.typeInto(VaultTestTags.EDITOR_TITLE, "Unsaved generated draft")
            compose.typeInto(VaultTestTags.EDITOR_USERNAME, "generator@example.com")
            scenario!!.recreate()
            compose.awaitTag(VaultTestTags.EDITOR, AUTH_WAIT_MS)
            assertEquals("Unsaved generated draft", compose.textOf(VaultTestTags.EDITOR_TITLE))
            assertEquals("generator@example.com", compose.textOf(VaultTestTags.EDITOR_USERNAME))
            compose.typeInto(VaultTestTags.EDITOR_NOTES, "Generated draft note")
            compose.click(VaultTestTags.EDITOR_SAVE)
            compose.awaitTagGone(VaultTestTags.EDITOR)
            compose.await("generated draft to retain its original generated password") {
                vaultEntries().singleOrNull { it.title == "Unsaved generated draft" }?.password == generated
            }
            assertEquals(2, vaultEntries().size)

            // The in-memory draft survives configuration changes, but must not cross a lock.
            openVaultTab()
            compose.click(VaultTestTags.ADD_FAB)
            compose.typeInto(VaultTestTags.EDITOR_TITLE, "Discard this draft on lock")
            compose.typeInto(VaultTestTags.EDITOR_PASSWORD, "Discarded-Secret-42!")
            compose.runOnIdle { container.auth.lock() }
            compose.awaitTag(AppTestTags.UNLOCK_SCREEN, AUTH_WAIT_MS)
            unlockWithMasterPassword()
            awaitVaultShown()
            compose.click(VaultTestTags.ADD_FAB)
            assertTrue("A new unlocked session must not recover a locked-session draft",
                editableValue(VaultTestTags.EDITOR_TITLE).isEmpty())
            assertTrue("A locked-session draft password must be discarded",
                editableValue(VaultTestTags.EDITOR_PASSWORD).isEmpty())
            compose.click(VaultTestTags.EDITOR_CANCEL)
            assertEquals(2, vaultEntries().size)
        } finally {
            scenario?.onActivity { it.requestedOrientation = originalOrientation }
            compose.waitForIdle()
        }
    }

    @Test fun showPasswordsSettingChangesActualVaultDetailAndEditorDefaults() {
        ensureUnlocked()
        val title = "Visibility preference account"
        val password = "Visibility-Entry-42!"
        addEntryViaUi(title, "visibility@example.com", password)
        openSettings()
        assertFalse(container.settings.preferences.value.showPasswords)
        compose.click(ToolTags.SHOW_PASSWORDS_PREFERENCE)
        compose.await("show passwords preference to enable") { container.settings.preferences.value.showPasswords }
        openVaultTab()
        compose.clickText(title)
        compose.awaitTag(VaultTestTags.DETAIL_SCREEN)
        compose.await("real detail to receive enabled visibility preference") {
            compose.textOrNull(VaultTestTags.DETAIL_PASSWORD) == password
        }
        compose.click(VaultTestTags.DETAIL_EDIT)
        compose.awaitTag(VaultTestTags.EDITOR)
        compose.await("real editor to receive enabled visibility preference") {
            compose.textOrNull(VaultTestTags.EDITOR_PASSWORD) == password
        }
        compose.click(VaultTestTags.EDITOR_CANCEL)
        compose.click(VaultTestTags.DETAIL_CLOSE)

        openSettings()
        compose.click(ToolTags.SHOW_PASSWORDS_PREFERENCE)
        compose.await("show passwords preference to disable") { !container.settings.preferences.value.showPasswords }
        openVaultTab()
        compose.clickText(title)
        compose.awaitTag(VaultTestTags.DETAIL_SCREEN)
        compose.await("real detail to mask after preference is disabled") {
            compose.textOrNull(VaultTestTags.DETAIL_PASSWORD) == MASKED_PASSWORD
        }
        compose.click(VaultTestTags.DETAIL_EDIT)
        compose.awaitTag(VaultTestTags.EDITOR)
        assertTrue("real editor must mask by default after disabling visibility",
            compose.textOf(VaultTestTags.EDITOR_PASSWORD) != password)
        compose.click(VaultTestTags.EDITOR_PASSWORD_TOGGLE)
        compose.await("manual reveal to remain functional") { compose.textOrNull(VaultTestTags.EDITOR_PASSWORD) == password }
        compose.click(VaultTestTags.EDITOR_CANCEL)
        compose.click(VaultTestTags.DETAIL_CLOSE)
    }

    @Test fun narrowActivityKeepsSettingsActionsAndNavigationReachable() {
        ensureUnlocked()
        var originalWidth = ViewGroup.LayoutParams.MATCH_PARENT
        var originalHeight = ViewGroup.LayoutParams.MATCH_PARENT
        var narrowWidthPx = 0
        scenario!!.onActivity { activity ->
            originalWidth = activity.window.attributes.width
            originalHeight = activity.window.attributes.height
            narrowWidthPx = (320 * activity.resources.displayMetrics.density).roundToInt()
            activity.window.setLayout(narrowWidthPx, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        try {
            compose.waitForIdle()
            compose.await("activity content to use a narrow phone width") {
                compose.nodesWithTag(AppTestTags.MAIN_SHELL).firstOrNull()?.boundsInRoot?.width?.let {
                    it > 0 && it <= narrowWidthPx + 2
                } == true
            }
            openSettings()
            listOf(ToolTags.THEME_SYSTEM, ToolTags.THEME_LIGHT, ToolTags.THEME_DARK).forEach {
                compose.node(it).performScrollTo().assertIsDisplayed()
            }
            compose.click(ToolTags.THEME_LIGHT)
            compose.click(ToolTags.THEME_DARK)
            assertTrue(compose.isSelected(ToolTags.THEME_DARK))
            compose.click(ToolTags.AUTO_LOCK_PREFERENCE)
            compose.clickText("15 minutes")
            compose.click(ToolTags.CLIPBOARD_PREFERENCE)
            compose.clickText("60 seconds")
            compose.click(ToolTags.SHOW_PASSWORDS_PREFERENCE)
            compose.await("narrow controls to apply preferences") {
                val value = container.settings.preferences.value
                value.autoLockMinutes == 15 && value.clipboardClearSeconds == 60 && value.showPasswords
            }
            listOf("about_button", "privacy_button", "limitations_button").forEach {
                compose.click(it)
                compose.clickText("Close")
            }
            openSecurityDashboard()
            compose.node(ToolTags.SECURITY_TOTAL).performScrollTo().assertIsDisplayed()
            compose.node(ToolTags.SECURITY_WEAK).performScrollTo().assertIsDisplayed()
            compose.node(ToolTags.SECURITY_REUSED).performScrollTo().assertIsDisplayed()
            openVaultTab()
            compose.node(VaultTestTags.ADD_FAB).assertIsDisplayed()
            assertEquals(AuthState.UNLOCKED, authState)
        } finally {
            scenario?.onActivity { it.window.setLayout(originalWidth, originalHeight) }
            compose.waitForIdle()
        }
    }

    /** Empty EditableText is meaningful; generic textOf falls back to the field label. */
    private fun editableValue(tag: String): String {
        fun find(node: SemanticsNode): String? =
            node.config.getOrNull(SemanticsProperties.EditableText)?.text
                ?: node.children.firstNotNullOfOrNull { find(it) }
        return checkNotNull(find(compose.node(tag).fetchSemanticsNode())) {
            "The tagged editor field must expose EditableText semantics"
        }
    }

    private fun requestedOrientation(): Int {
        var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        scenario!!.onActivity { orientation = it.requestedOrientation }
        return orientation
    }

    private fun requestLandscape() {
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.await("activity to enter landscape configuration", AUTH_WAIT_MS) {
            var landscape = false
            scenario!!.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            landscape
        }
        compose.waitForIdle()
    }
}
