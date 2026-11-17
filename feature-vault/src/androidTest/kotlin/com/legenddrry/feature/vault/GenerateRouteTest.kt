// Instrumented tests for GenerateRoute against the VaultTestTags contract.
package com.legenddrry.feature.vault

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.DIGITS
import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.LOWERCASE
import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.SYMBOLS
import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.UPPERCASE
import com.legenddrry.feature.vault.strength.StrengthLevel
import com.legenddrry.feature.vault.testing.AndroidFakeVaultRepository
import com.legenddrry.feature.vault.testing.await
import com.legenddrry.feature.vault.testing.awaitClipboard
import com.legenddrry.feature.vault.testing.awaitTag
import com.legenddrry.feature.vault.testing.awaitTagGone
import com.legenddrry.feature.vault.testing.clearClipboard
import com.legenddrry.feature.vault.testing.click
import com.legenddrry.feature.vault.testing.exists
import com.legenddrry.feature.vault.testing.isOn
import com.legenddrry.feature.vault.testing.node
import com.legenddrry.feature.vault.testing.textOf
import com.legenddrry.feature.vault.testing.textOrNull
import com.legenddrry.feature.vault.ui.common.VaultTestTags as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerateRouteTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var repo: AndroidFakeVaultRepository

    private fun launch(r: AndroidFakeVaultRepository = AndroidFakeVaultRepository()) {
        repo = r
        rule.setContent { MaterialTheme { GenerateRoute(r) } }
        rule.awaitTag(T.GENERATOR_SCREEN)
        rule.await("a generated password to be shown") { !rule.textOrNull(T.GENERATOR_PASSWORD).isNullOrEmpty() }
    }

    private fun password(): String = rule.textOf(T.GENERATOR_PASSWORD)

    private fun regenerate(): String {
        val before = password()
        rule.click(T.GENERATOR_REGENERATE)
        rule.await("password to change after regenerate") {
            val now = rule.textOrNull(T.GENERATOR_PASSWORD)
            !now.isNullOrEmpty() && now != before
        }
        return password()
    }

    private fun setLength(value: Float) {
        rule.node(T.GENERATOR_LENGTH_SLIDER).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        rule.waitForIdle()
    }

    private fun assertOnlyFrom(pw: String, allowed: String) {
        val bad = pw.filterNot { it in allowed }
        assertTrue("password '$pw' has chars outside the enabled classes: '$bad'", bad.isEmpty())
    }

    private fun turnOff(tag: String) {
        if (rule.isOn(tag)) rule.click(tag)
        rule.await("switch '$tag' to be off") { !rule.isOn(tag) }
    }

    @Test
    fun defaultPassword_is16Chars_withAllClasses() {
        launch()
        val pw = password()
        assertEquals("default length", 16, pw.length)
        assertTrue("upper in '$pw'", pw.any { it in UPPERCASE })
        assertTrue("lower in '$pw'", pw.any { it in LOWERCASE })
        assertTrue("digit in '$pw'", pw.any { it in DIGITS })
        assertTrue("symbol in '$pw'", pw.any { it in SYMBOLS })
        assertTrue(rule.textOf(T.GENERATOR_LENGTH_LABEL).contains("Length: 16"))
        listOf(T.GENERATOR_TOGGLE_UPPER, T.GENERATOR_TOGGLE_LOWER, T.GENERATOR_TOGGLE_DIGITS, T.GENERATOR_TOGGLE_SYMBOLS)
            .forEach { assertTrue("$it should default to on", rule.isOn(it)) }
    }

    @Test
    fun everyGeneratedPassword_containsAllEnabledClasses() {
        launch()
        repeat(5) {
            val pw = regenerate()
            assertEquals(16, pw.length)
            assertTrue("'$pw' all classes", pw.any { it in UPPERCASE } && pw.any { it in LOWERCASE } &&
                pw.any { it in DIGITS } && pw.any { it in SYMBOLS })
        }
    }

    @Test
    fun regenerate_changesPassword() {
        launch()
        val first = password()
        val second = regenerate()
        assertTrue(first != second)
    }

    @Test
    fun strengthLabel_isShown() {
        launch()
        rule.awaitTag(T.STRENGTH_LABEL)
        val label = rule.textOf(T.STRENGTH_LABEL)
        assertTrue("'$label' is a StrengthLevel label", StrengthLevel.entries.any { it.label == label })
    }

    @Test
    fun disablingSymbolsDigitsUpper_leavesOnlyLowercase() {
        launch()
        turnOff(T.GENERATOR_TOGGLE_SYMBOLS)
        repeat(3) { assertOnlyFrom(regenerate(), UPPERCASE + LOWERCASE + DIGITS) }

        turnOff(T.GENERATOR_TOGGLE_DIGITS)
        repeat(3) { assertOnlyFrom(regenerate(), UPPERCASE + LOWERCASE) }

        turnOff(T.GENERATOR_TOGGLE_UPPER)
        repeat(3) {
            val pw = regenerate()
            assertOnlyFrom(pw, LOWERCASE)
            assertEquals(16, pw.length)
        }
        assertTrue(rule.isOn(T.GENERATOR_TOGGLE_LOWER))
    }

    @Test
    fun disablingSymbolsOnly_passwordHasNoSymbols_butOtherClasses() {
        launch()
        turnOff(T.GENERATOR_TOGGLE_SYMBOLS)
        val pw = regenerate()
        assertOnlyFrom(pw, UPPERCASE + LOWERCASE + DIGITS)
        assertTrue(pw.any { it in UPPERCASE } && pw.any { it in LOWERCASE } && pw.any { it in DIGITS })
    }

    @Test
    fun turningAllOff_keepsLastCategoryOn() {
        launch()
        turnOff(T.GENERATOR_TOGGLE_UPPER)
        turnOff(T.GENERATOR_TOGGLE_LOWER)
        turnOff(T.GENERATOR_TOGGLE_DIGITS)
        // Attempt to switch off the last remaining category: it must stay on.
        rule.click(T.GENERATOR_TOGGLE_SYMBOLS)
        rule.waitForIdle()
        assertTrue("last enabled category must stay on", rule.isOn(T.GENERATOR_TOGGLE_SYMBOLS))

        val pw = regenerate()
        assertTrue(pw.isNotEmpty())
        assertOnlyFrom(pw, SYMBOLS)
        assertEquals(16, pw.length)
    }

    @Test
    fun slider_max32_updatesLabelAndPasswordLength() {
        launch()
        setLength(32f)
        rule.await("length label to show 32") { rule.textOrNull(T.GENERATOR_LENGTH_LABEL)?.contains("Length: 32") == true }
        rule.await("password length 32 (after option change)") { rule.textOrNull(T.GENERATOR_PASSWORD)?.length == 32 }
        assertEquals(32, regenerate().length)
    }

    @Test
    fun slider_min12_updatesLabelAndPasswordLength() {
        launch()
        setLength(12f)
        rule.await("length label to show 12") { rule.textOrNull(T.GENERATOR_LENGTH_LABEL)?.contains("Length: 12") == true }
        rule.await("password length 12 (after option change)") { rule.textOrNull(T.GENERATOR_PASSWORD)?.length == 12 }
        assertEquals(12, regenerate().length)
    }

    @Test
    fun copy_putsGeneratedPasswordOnClipboard() {
        clearClipboard()
        launch()
        val pw = password()
        rule.click(T.GENERATOR_COPY)
        rule.awaitClipboard(pw)
    }

    @Test
    fun saveToVault_opensPrefilledEditor_andAddsEntryWithGeneratedPassword() {
        launch()
        val pw = password()
        rule.click(T.GENERATOR_SAVE)
        rule.awaitTag(T.EDITOR)
        rule.awaitTag(T.EDITOR_PASSWORD)
        rule.node(T.EDITOR_TITLE).performTextInput("Generated Login")
        rule.click(T.EDITOR_SAVE)

        rule.await("repository.add to be called") { repo.addCalls.size == 1 }
        val added = repo.current.single()
        assertEquals("Generated Login", added.title)
        assertEquals("saved password must equal the displayed generated one", pw, added.password)
        rule.awaitTagGone(T.EDITOR)
        assertTrue(rule.exists(T.GENERATOR_SCREEN))
    }

    @Test
    fun saveToVault_blankTitle_showsErrorAndDoesNotSave() {
        launch()
        rule.click(T.GENERATOR_SAVE)
        rule.awaitTag(T.EDITOR)
        rule.click(T.EDITOR_SAVE)
        rule.awaitTag(T.EDITOR_ERROR_TITLE)
        assertTrue(repo.addCalls.isEmpty())
    }
}
