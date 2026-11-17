// Instrumented tests for the host-provided "show passwords" display option.
package com.legenddrry.feature.vault

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.legenddrry.feature.vault.testing.AndroidFakeVaultRepository
import com.legenddrry.feature.vault.testing.MASKED_PASSWORD
import com.legenddrry.feature.vault.testing.await
import com.legenddrry.feature.vault.testing.awaitTag
import com.legenddrry.feature.vault.testing.click
import com.legenddrry.feature.vault.testing.node
import com.legenddrry.feature.vault.testing.testEntry
import com.legenddrry.feature.vault.testing.textOf
import com.legenddrry.feature.vault.testing.textOrNull
import com.legenddrry.feature.vault.ui.common.VaultTestTags as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowPasswordsOptionTest {

    @get:Rule
    val rule = createComposeRule()

    private val github = testEntry("e1", "GitHub", username = "octo", password = "Gh-Secret-123!", category = "Work")

    private fun launch(options: VaultDisplayOptions? = null) {
        val repo = AndroidFakeVaultRepository(listOf(github))
        rule.setContent {
            MaterialTheme {
                if (options == null) {
                    EntriesRoute(repo)
                } else {
                    CompositionLocalProvider(LocalVaultDisplayOptions provides options) { EntriesRoute(repo) }
                }
            }
        }
    }

    private fun openDetail() {
        rule.click(T.ENTRY_ROW_PREFIX + github.id)
        rule.awaitTag(T.DETAIL_SCREEN)
    }

    @Test
    fun default_detailPasswordHidden() {
        launch()
        openDetail()
        assertEquals(MASKED_PASSWORD, rule.textOf(T.DETAIL_PASSWORD))
    }

    @Test
    fun providedFalse_detailPasswordHidden() {
        launch(VaultDisplayOptions(showPasswordsByDefault = false))
        openDetail()
        assertEquals(MASKED_PASSWORD, rule.textOf(T.DETAIL_PASSWORD))
    }

    @Test
    fun providedTrue_detailShowsRealPassword_andToggleStillMasks() {
        launch(VaultDisplayOptions(showPasswordsByDefault = true))
        openDetail()
        assertEquals(github.password, rule.textOf(T.DETAIL_PASSWORD))

        rule.click(T.DETAIL_TOGGLE_VISIBILITY)
        rule.await("password to be masked") { rule.textOrNull(T.DETAIL_PASSWORD) == MASKED_PASSWORD }
        rule.click(T.DETAIL_TOGGLE_VISIBILITY)
        rule.await("password to be revealed again") { rule.textOrNull(T.DETAIL_PASSWORD) == github.password }
    }

    @Test
    fun default_editorPasswordMasked() {
        launch()
        rule.click(T.ADD_FAB)
        rule.awaitTag(T.EDITOR)
        rule.node(T.EDITOR_PASSWORD).performTextInput("Plain-Text-42!")
        assertNotEquals("Plain-Text-42!", rule.textOf(T.EDITOR_PASSWORD))
    }

    @Test
    fun providedTrue_editorPasswordStartsVisible() {
        launch(VaultDisplayOptions(showPasswordsByDefault = true))
        rule.click(T.ADD_FAB)
        rule.awaitTag(T.EDITOR)
        rule.node(T.EDITOR_PASSWORD).performTextInput("Plain-Text-42!")
        rule.await("editor password to be shown in plain text") {
            rule.textOrNull(T.EDITOR_PASSWORD) == "Plain-Text-42!"
        }
    }
}
