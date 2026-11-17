// Instrumented tests for EntriesRoute against the VaultTestTags contract.
package com.legenddrry.feature.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.legenddrry.feature.vault.strength.StrengthLevel
import com.legenddrry.feature.vault.testing.AndroidFakeVaultRepository
import com.legenddrry.feature.vault.testing.MASKED_PASSWORD
import com.legenddrry.feature.vault.testing.await
import com.legenddrry.feature.vault.testing.awaitClipboard
import com.legenddrry.feature.vault.testing.awaitTag
import com.legenddrry.feature.vault.testing.awaitTagGone
import com.legenddrry.feature.vault.testing.awaitText
import com.legenddrry.feature.vault.testing.clearClipboard
import com.legenddrry.feature.vault.testing.click
import com.legenddrry.feature.vault.testing.exists
import com.legenddrry.feature.vault.testing.node
import com.legenddrry.feature.vault.testing.testEntry
import com.legenddrry.feature.vault.testing.textOf
import com.legenddrry.feature.vault.testing.textOrNull
import com.legenddrry.feature.vault.ui.common.VaultTestTags as T
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntriesRouteTest {

    @get:Rule
    val rule = createComposeRule()

    private val github = testEntry("e1", "GitHub", username = "octo", password = "Gh-Secret-123!", category = "Work")
    private val gmail = testEntry("e2", "Gmail", username = "me@gmail.com", password = "Mail-Pass-456?", category = "Email")
    private val outlook = testEntry("e3", "Outlook", username = "me@outlook.com", password = "Outl00k-Pass#9", category = "Email")
    private val seeded = listOf(github, gmail, outlook)

    private fun launch(repo: AndroidFakeVaultRepository): AndroidFakeVaultRepository {
        rule.setContent { MaterialTheme { EntriesRoute(repo) } }
        return repo
    }

    private fun row(id: String) = T.ENTRY_ROW_PREFIX + id

    private fun openDetail(id: String) {
        rule.click(row(id))
        rule.awaitTag(T.DETAIL_SCREEN)
    }

    // ---------- list states ----------

    @Test
    fun emptyRepository_showsEmptyState() {
        launch(AndroidFakeVaultRepository())
        rule.awaitTag(T.ENTRIES_SCREEN)
        rule.awaitTag(T.EMPTY_STATE)
        assertFalse(rule.exists(T.NO_RESULTS_STATE))
        assertTrue("Add FAB should be available on empty vault", rule.exists(T.ADD_FAB))
    }

    @Test
    fun seededEntries_renderRows_andNoEmptyState() {
        launch(AndroidFakeVaultRepository(seeded))
        seeded.forEach { rule.awaitTag(row(it.id)) }
        assertFalse(rule.exists(T.EMPTY_STATE))
        assertFalse(rule.exists(T.NO_RESULTS_STATE))
        rule.awaitText("GitHub")
        rule.awaitText("Gmail")
    }

    @Test
    fun loadingState_shownUntilFirstEmission() {
        val repo = AndroidFakeVaultRepository(seeded).apply { holdEntries = true }
        launch(repo)
        rule.awaitTag(T.LOADING_STATE)
        assertFalse(rule.exists(T.EMPTY_STATE))
    }

    @Test
    fun failingEntries_showErrorState_andRetryRecovers() {
        val repo = AndroidFakeVaultRepository(seeded).apply { failEntries = true }
        launch(repo)
        rule.awaitTag(T.ERROR_STATE)
        rule.awaitTag(T.RETRY_BUTTON)
        assertFalse(rule.exists(row(github.id)))

        repo.failEntries = false
        val before = repo.entriesSubscriptions.get()
        rule.click(T.RETRY_BUTTON)
        rule.await("retry to re-subscribe to repository.entries") { repo.entriesSubscriptions.get() > before }
        rule.awaitTag(row(github.id))
        rule.awaitTagGone(T.ERROR_STATE)
    }

    // ---------- add ----------

    @Test
    fun addFlow_savesEntryToRepository_andShowsRow() {
        val repo = launch(AndroidFakeVaultRepository())
        rule.click(T.ADD_FAB)
        rule.awaitTag(T.EDITOR)
        rule.node(T.EDITOR_TITLE).performTextInput("Netflix")
        rule.node(T.EDITOR_USERNAME).performTextInput("binge@example.com")
        rule.node(T.EDITOR_PASSWORD).performTextInput("Str3am-All-Night!")
        rule.click(T.EDITOR_SAVE)

        rule.await("repository.add to be called") { repo.addCalls.size == 1 }
        val added = repo.current.single()
        assertEquals("Netflix", added.title)
        assertEquals("binge@example.com", added.username)
        assertEquals("Str3am-All-Night!", added.password)
        assertTrue(added.id.isNotBlank())

        rule.awaitTagGone(T.EDITOR)
        rule.awaitTag(row(added.id))
        assertFalse(rule.exists(T.EMPTY_STATE))
        rule.awaitText(T.MSG_ENTRY_SAVED)
    }

    @Test
    fun addWithBlankTitle_showsTitleError_andDoesNotSave() {
        val repo = launch(AndroidFakeVaultRepository())
        rule.click(T.ADD_FAB)
        rule.awaitTag(T.EDITOR)
        rule.node(T.EDITOR_USERNAME).performTextInput("someone")
        rule.node(T.EDITOR_PASSWORD).performTextInput("Valid-Pass-123!")
        rule.click(T.EDITOR_SAVE)

        rule.awaitTag(T.EDITOR_ERROR_TITLE)
        assertTrue("editor must stay open on invalid save", rule.exists(T.EDITOR))
        rule.waitForIdle()
        assertTrue(repo.addCalls.isEmpty())
        assertTrue(repo.current.isEmpty())
    }

    @Test
    fun addWithWhitespaceTitle_showsTitleError() {
        val repo = launch(AndroidFakeVaultRepository())
        rule.click(T.ADD_FAB)
        rule.node(T.EDITOR_TITLE).performTextInput("   ")
        rule.node(T.EDITOR_PASSWORD).performTextInput("Valid-Pass-123!")
        rule.click(T.EDITOR_SAVE)
        rule.awaitTag(T.EDITOR_ERROR_TITLE)
        assertTrue(repo.addCalls.isEmpty())
    }

    @Test
    fun addWithBlankPassword_showsPasswordError_andDoesNotSave() {
        val repo = launch(AndroidFakeVaultRepository())
        rule.click(T.ADD_FAB)
        rule.awaitTag(T.EDITOR)
        rule.node(T.EDITOR_TITLE).performTextInput("Bank")
        rule.click(T.EDITOR_SAVE)

        rule.awaitTag(T.EDITOR_ERROR_PASSWORD)
        assertFalse("title is valid, no title error expected", rule.exists(T.EDITOR_ERROR_TITLE))
        assertTrue(rule.exists(T.EDITOR))
        assertTrue(repo.addCalls.isEmpty())
        assertTrue(repo.current.isEmpty())
    }

    @Test
    fun invalidWebsite_showsInlineError_andDoesNotSave() {
        val repo = launch(AndroidFakeVaultRepository())
        rule.click(T.ADD_FAB)
        rule.node(T.EDITOR_TITLE).performTextInput("Site")
        rule.node(T.EDITOR_PASSWORD).performTextInput("secret")
        rule.node(T.EDITOR_WEBSITE).performTextInput("bad website")
        rule.click(T.EDITOR_SAVE)
        rule.awaitTag(T.EDITOR_ERROR_WEBSITE)
        assertTrue(rule.exists(T.EDITOR))
        assertTrue(repo.addCalls.isEmpty())
    }

    @Test
    fun overlongNotes_showsInlineError_andDoesNotSave() {
        val repo = launch(AndroidFakeVaultRepository())
        rule.click(T.ADD_FAB)
        rule.node(T.EDITOR_TITLE).performTextInput("Site")
        rule.node(T.EDITOR_PASSWORD).performTextInput("secret")
        rule.node(T.EDITOR_NOTES).performTextInput("n".repeat(1001))
        rule.click(T.EDITOR_SAVE)
        rule.awaitTag(T.EDITOR_ERROR_NOTES)
        assertTrue(rule.exists(T.EDITOR))
        assertTrue(repo.addCalls.isEmpty())
    }

    @Test
    fun editorCancel_closesWithoutSaving() {
        val repo = launch(AndroidFakeVaultRepository())
        rule.click(T.ADD_FAB)
        rule.node(T.EDITOR_TITLE).performTextInput("Draft")
        rule.click(T.EDITOR_CANCEL)
        rule.awaitTagGone(T.EDITOR)
        assertTrue(repo.addCalls.isEmpty())
    }

    // ---------- edit ----------

    @Test
    fun editFlow_updatesRepository_andDetailShowsNewTitle() {
        val repo = launch(AndroidFakeVaultRepository(seeded))
        openDetail(github.id)
        assertTrue(rule.textOf(T.DETAIL_TITLE).contains("GitHub"))

        rule.click(T.DETAIL_EDIT)
        rule.awaitTag(T.EDITOR)
        rule.node(T.EDITOR_TITLE).performTextReplacement("GitHub Enterprise")
        rule.click(T.EDITOR_SAVE)

        rule.await("repository.update to be called") { repo.updateCalls.size == 1 }
        val updated = repo.byId(github.id)
        assertNotNull(updated)
        assertEquals("GitHub Enterprise", updated!!.title)
        assertEquals(github.password, updated.password)
        assertEquals(github.username, updated.username)
        assertEquals(seeded.size, repo.current.size)
        assertTrue(repo.addCalls.isEmpty())

        rule.awaitTagGone(T.EDITOR)
        rule.await("detail title to show the new title") {
            rule.textOrNull(T.DETAIL_TITLE)?.contains("GitHub Enterprise") == true
        }
    }

    // ---------- delete ----------

    @Test
    fun deleteFlow_cancelKeeps_confirmRemoves() {
        val repo = launch(AndroidFakeVaultRepository(seeded))
        openDetail(gmail.id)

        rule.click(T.DETAIL_DELETE)
        rule.awaitTag(T.DELETE_DIALOG)
        rule.click(T.DELETE_CANCEL)
        rule.awaitTagGone(T.DELETE_DIALOG)
        assertTrue(repo.deleteCalls.isEmpty())
        assertNotNull(repo.byId(gmail.id))

        // Detail should still be open after cancel; reopen defensively if the UI closed it.
        if (!rule.exists(T.DETAIL_DELETE)) openDetail(gmail.id)
        rule.click(T.DETAIL_DELETE)
        rule.awaitTag(T.DELETE_DIALOG)
        rule.click(T.DELETE_CONFIRM)

        rule.await("repository.delete(${gmail.id})") { repo.deleteCalls == listOf(gmail.id) }
        assertNull(repo.byId(gmail.id))
        assertEquals(seeded.size - 1, repo.current.size)
        rule.awaitTagGone(T.DELETE_DIALOG)
        rule.awaitTagGone(row(gmail.id))
        rule.awaitTag(row(github.id))
        rule.awaitText(T.MSG_ENTRY_DELETED)
    }

    // ---------- search & filter ----------

    @Test
    fun search_filtersRows_andNonsenseShowsNoResults() {
        launch(AndroidFakeVaultRepository(seeded))
        seeded.forEach { rule.awaitTag(row(it.id)) }

        rule.node(T.SEARCH_FIELD).performTextInput("git")
        rule.awaitTagGone(row(gmail.id))
        rule.awaitTagGone(row(outlook.id))
        rule.awaitTag(row(github.id))
        assertFalse(rule.exists(T.NO_RESULTS_STATE))

        rule.node(T.SEARCH_FIELD).performTextReplacement("zzqx-nothing-matches-this")
        rule.awaitTag(T.NO_RESULTS_STATE)
        seeded.forEach { rule.awaitTagGone(row(it.id)) }
        assertFalse("entries exist, so EMPTY_STATE must not be shown", rule.exists(T.EMPTY_STATE))

        rule.node(T.SEARCH_FIELD).performTextReplacement("")
        seeded.forEach { rule.awaitTag(row(it.id)) }
        rule.awaitTagGone(T.NO_RESULTS_STATE)
    }

    @Test
    fun search_matchesUsername() {
        launch(AndroidFakeVaultRepository(seeded))
        rule.awaitTag(row(github.id))
        rule.node(T.SEARCH_FIELD).performTextInput("outlook.com")
        rule.awaitTagGone(row(github.id))
        rule.awaitTagGone(row(gmail.id))
        rule.awaitTag(row(outlook.id))
    }

    @Test
    fun categoryChip_filtersToCategory_andAllRestores() {
        launch(AndroidFakeVaultRepository(seeded))
        seeded.forEach { rule.awaitTag(row(it.id)) }

        rule.click(T.CATEGORY_CHIP_PREFIX + "Email")
        rule.awaitTagGone(row(github.id))
        rule.awaitTag(row(gmail.id))
        rule.awaitTag(row(outlook.id))

        rule.click(T.CATEGORY_CHIP_ALL)
        seeded.forEach { rule.awaitTag(row(it.id)) }
    }

    @Test
    fun categoryChipWithNoEntries_showsNoResults() {
        launch(AndroidFakeVaultRepository(seeded))
        rule.awaitTag(row(github.id))
        rule.click(T.CATEGORY_CHIP_PREFIX + "Social")
        rule.awaitTag(T.NO_RESULTS_STATE)
        assertFalse(rule.exists(T.EMPTY_STATE))
    }

    // ---------- detail ----------

    @Test
    fun detail_showsTitleUsernameAndStrength() {
        launch(AndroidFakeVaultRepository(seeded))
        openDetail(github.id)
        assertTrue(rule.textOf(T.DETAIL_TITLE).contains("GitHub"))
        assertTrue(rule.textOf(T.DETAIL_USERNAME).contains("octo"))
        rule.awaitTag(T.STRENGTH_LABEL)
        val label = rule.textOf(T.STRENGTH_LABEL)
        assertTrue(
            "STRENGTH_LABEL '$label' should be a StrengthLevel label",
            StrengthLevel.entries.any { it.label == label },
        )
    }

    @Test
    fun detail_passwordVisibilityToggle() {
        launch(AndroidFakeVaultRepository(seeded))
        openDetail(github.id)

        assertEquals(MASKED_PASSWORD, rule.textOf(T.DETAIL_PASSWORD))
        rule.click(T.DETAIL_TOGGLE_VISIBILITY)
        rule.await("password to be revealed") { rule.textOrNull(T.DETAIL_PASSWORD) == github.password }
        rule.click(T.DETAIL_TOGGLE_VISIBILITY)
        rule.await("password to be masked again") { rule.textOrNull(T.DETAIL_PASSWORD) == MASKED_PASSWORD }
    }

    @Test
    fun detail_close_hidesDetail() {
        launch(AndroidFakeVaultRepository(seeded))
        openDetail(github.id)
        rule.click(T.DETAIL_CLOSE)
        rule.awaitTagGone(T.DETAIL_SCREEN)
        rule.awaitTag(row(github.id))
    }

    @Test
    fun detail_copyPassword_putsPasswordOnClipboard_andShowsSnackbar() {
        clearClipboard()
        launch(AndroidFakeVaultRepository(seeded))
        openDetail(github.id)
        rule.click(T.DETAIL_COPY_PASSWORD)
        rule.awaitClipboard(github.password)
        rule.awaitText(T.MSG_PASSWORD_COPIED)
    }

    @Test
    fun clipboardPolicyDenialShowsFailureAndKeepsDetailOpen() {
        val repo = AndroidFakeVaultRepository(seeded)
        rule.setContent {
            val host = LocalContext.current
            val denied = remember(host) {
                object : ContextWrapper(host) {
                    override fun getSystemService(name: String): Any? {
                        if (name == Context.CLIPBOARD_SERVICE) throw SecurityException("Clipboard blocked by policy")
                        return super.getSystemService(name)
                    }
                }
            }
            CompositionLocalProvider(LocalContext provides denied) {
                MaterialTheme { EntriesRoute(repo) }
            }
        }
        openDetail(github.id)
        rule.click(T.DETAIL_COPY_PASSWORD)
        rule.awaitText("Couldn't copy to clipboard. Please try again.")
        assertTrue(rule.exists(T.DETAIL_SCREEN))
        assertEquals(seeded, repo.current)
    }

    @Test
    fun detail_copyUsername_putsUsernameOnClipboard_andShowsSnackbar() {
        clearClipboard()
        launch(AndroidFakeVaultRepository(seeded))
        openDetail(gmail.id)
        rule.click(T.DETAIL_COPY_USERNAME)
        rule.awaitClipboard(gmail.username)
        rule.awaitText(T.MSG_USERNAME_COPIED)
    }

    // ---------- duplicate password ----------

    @Test
    fun duplicatePasswords_showBadges_andDetailWarning() {
        val shared = "Same-Pass-For-Both1!"
        val a = testEntry("d1", "Alpha", password = shared, category = "Social")
        val b = testEntry("d2", "Bravo", password = shared, category = "Shopping")
        val c = testEntry("d3", "Charlie", password = "Unique-Pass-Only-Me9#")
        launch(AndroidFakeVaultRepository(listOf(a, b, c)))

        rule.awaitTag(row(c.id))
        rule.awaitTag(T.ENTRY_DUPLICATE_BADGE_PREFIX + a.id)
        rule.awaitTag(T.ENTRY_DUPLICATE_BADGE_PREFIX + b.id)
        assertFalse("unique password must not get a badge", rule.exists(T.ENTRY_DUPLICATE_BADGE_PREFIX + c.id))

        openDetail(a.id)
        rule.awaitTag(T.DUPLICATE_WARNING)
    }

    @Test
    fun uniquePassword_noDuplicateWarningInDetail() {
        launch(AndroidFakeVaultRepository(seeded))
        rule.awaitTag(row(github.id))
        seeded.forEach { assertFalse(rule.exists(T.ENTRY_DUPLICATE_BADGE_PREFIX + it.id)) }
        openDetail(github.id)
        rule.waitForIdle()
        assertFalse(rule.exists(T.DUPLICATE_WARNING))
    }
}
