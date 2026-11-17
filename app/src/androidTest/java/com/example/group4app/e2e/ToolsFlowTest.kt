// End-to-end tests of the tools features inside the integrated app shell:
// security dashboard, login approvals, email aliases and settings.
package com.example.group4app.e2e

import androidx.compose.ui.state.ToggleableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.group4app.ui.shell.AppTestTags
import com.legenddrry.core.VaultEntry
import com.legenddrry.feature.tools.contracts.ApprovalStatus
import com.legenddrry.feature.tools.contracts.ThemeMode
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ToolsFlowTest : E2eTestBase() {

    private companion object {
        // Non-sensitive test value, deliberately shared by two entries.
        const val REUSED_PASSWORD = "Shared-Reuse-Pass-77!"
        const val ALIAS_SERVICE = "E2E Campus Library"
    }

    private fun reusedCount(entries: List<VaultEntry>): Int =
        entries.groupBy { it.password }.values.filter { it.size > 1 }.sumOf { it.size }

    // ------------------------------------------------------------------ 5. security dashboard

    @Test
    fun test05_securityDashboard_showsRealCounts_andUpdatesAfterAddingReusedPassword() {
        ensureUnlocked()
        addEntryViaUi("Reuse Alpha", "alpha@example.com", REUSED_PASSWORD)

        openSecurityDashboard()
        compose.await("dashboard total to equal the repository size") {
            compose.intOf(ToolTags.SECURITY_TOTAL) == vaultEntries().size
        }
        val totalBefore = compose.intOf(ToolTags.SECURITY_TOTAL)!!
        assertTrue("dashboard must count the entry just added", totalBefore >= 1)
        val reusedBefore = reusedCount(vaultEntries())
        compose.await("reused count to match the repository ($reusedBefore)") {
            compose.intOf(ToolTags.SECURITY_REUSED) == reusedBefore
        }
        val weak = compose.intOf(ToolTags.SECURITY_WEAK)
        assertTrue("weak count must be within 0..total", weak != null && weak in 0..totalBefore)

        backToMore()
        addEntryViaUi("Reuse Bravo", "bravo@example.com", REUSED_PASSWORD)
        assertEquals(totalBefore + 1, vaultEntries().size)

        openSecurityDashboard()
        compose.await("dashboard total to grow to ${totalBefore + 1}") {
            compose.intOf(ToolTags.SECURITY_TOTAL) == totalBefore + 1
        }
        val reusedAfter = reusedCount(vaultEntries())
        assertTrue("both entries share a password", reusedAfter >= reusedBefore + 2)
        compose.await("reused count to update to $reusedAfter") {
            compose.intOf(ToolTags.SECURITY_REUSED) == reusedAfter
        }
    }

    // ------------------------------------------------------------------ 6. approvals

    @Test
    fun test06_approvals_approveDenyHistory_andApprovedCannotBeReapproved() {
        ensureUnlocked()
        openApprovalsTab()
        val repo = container.approvals

        val before = repo.requests.value.map { it.id }.toSet()
        compose.click(ToolTags.CREATE_DEMO_REQUEST)
        compose.await("a new demo request") { repo.requests.value.any { it.id !in before } }
        val first = repo.requests.value.first { it.id !in before }.id
        assertEquals(ApprovalStatus.PENDING, repo.requests.value.first { it.id == first }.status)

        compose.click(ToolTags.approve(first))
        compose.await("first request to be approved") {
            repo.requests.value.first { it.id == first }.status == ApprovalStatus.APPROVED
        }
        compose.awaitTagGone(ToolTags.approve(first))
        assertFalse("approved request must not offer Deny", compose.exists(ToolTags.deny(first)))
        compose.awaitTag(ToolTags.APPROVAL_FEEDBACK)

        val beforeSecond = repo.requests.value.map { it.id }.toSet()
        compose.click(ToolTags.CREATE_DEMO_REQUEST)
        compose.await("a second demo request") { repo.requests.value.any { it.id !in beforeSecond } }
        val second = repo.requests.value.first { it.id !in beforeSecond }.id
        compose.click(ToolTags.deny(second))
        compose.await("second request to be denied") {
            repo.requests.value.first { it.id == second }.status == ApprovalStatus.DENIED
        }
        compose.awaitTagGone(ToolTags.deny(second))
        assertFalse(compose.exists(ToolTags.approve(second)))

        // History lists both decisions.
        val historySize = repo.requests.value.count { it.status != ApprovalStatus.PENDING }
        assertTrue(historySize >= 2)
        compose.awaitText("Recent history ($historySize)", substring = true)
        compose.awaitText("Approved", substring = true)
        compose.awaitText("Denied", substring = true)
        assertTrue("approved request stays listed in history", compose.exists(ToolTags.requestDetails(first)))
        assertTrue("denied request stays listed in history", compose.exists(ToolTags.requestDetails(second)))

        // The repository refuses invalid transitions; the approved request stays approved.
        val reapprove = runCatching { runBlocking { repo.approve(first) } }
        assertTrue("re-approving an approved request must fail", reapprove.isFailure)
        val denyApproved = runCatching { runBlocking { repo.deny(first) } }
        assertTrue("denying an approved request must fail", denyApproved.isFailure)
        assertEquals(ApprovalStatus.APPROVED, repo.requests.value.first { it.id == first }.status)
        compose.waitForIdle()
        assertFalse(compose.exists(ToolTags.approve(first)))
    }

    // ------------------------------------------------------------------ 7. aliases

    @Test
    fun test07_aliases_createSearchCopyToggleDelete() {
        ensureUnlocked()
        openAliasesTab()
        val repo = container.aliases
        val before = repo.aliases.value.map { it.id }.toSet()

        compose.click(ToolTags.ALIASES_CREATE)
        compose.typeInto(ToolTags.ALIASES_SERVICE, ALIAS_SERVICE)
        compose.click(ToolTags.ALIASES_CONFIRM_CREATE)
        compose.await("alias to be created") { repo.aliases.value.any { it.id !in before } }
        val alias = repo.aliases.value.first { it.id !in before }
        assertEquals(ALIAS_SERVICE, alias.serviceName)
        assertTrue("new alias starts active", alias.isActive)
        compose.awaitTagGone(ToolTags.ALIASES_SERVICE)
        compose.awaitText(ALIAS_SERVICE)
        compose.awaitText(alias.aliasAddress)

        // Search.
        compose.typeInto(ToolTags.ALIASES_SEARCH, "zzqx-unmatched")
        compose.awaitText("No matching aliases", ignoreCase = true)
        compose.awaitTextGone(ALIAS_SERVICE)
        compose.typeInto(ToolTags.ALIASES_SEARCH, "campus lib")
        compose.awaitText(ALIAS_SERVICE)

        // Copy.
        compose.click(ToolTags.aliasCopy(alias.id))
        compose.await("copy feedback") {
            compose.textOrNull(ToolTags.ALIASES_FEEDBACK)?.contains("copied", ignoreCase = true) == true
        }

        // Disable / enable. Toggle via the Switch's semantic click: after scroll-to, the switch can sit under the
        // anchored "create alias" FAB, and a coordinate tap would hit the FAB instead.
        toggleAlias(alias.id)
        compose.await("alias to be disabled") { repo.aliases.value.first { it.id == alias.id }.isActive.not() }
        compose.await("switch to show Off") { compose.toggleState(ToolTags.aliasToggle(alias.id)) == ToggleableState.Off }
        // The switch stays disabled while the write is in flight (busy); wait until it accepts input again.
        compose.await("switch to be enabled again") {
            compose.onAllNodesWithTag(ToolTags.aliasToggle(alias.id), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.let { androidx.compose.ui.semantics.SemanticsProperties.Disabled !in it.config } == true
        }
        toggleAlias(alias.id)
        compose.await("alias to be enabled") { repo.aliases.value.first { it.id == alias.id }.isActive }
        compose.await("switch to show On") { compose.toggleState(ToolTags.aliasToggle(alias.id)) == ToggleableState.On }

        // Delete (confirmed).
        semanticClick(ToolTags.aliasDelete(alias.id))
        compose.click(ToolTags.ALIASES_CONFIRM_DELETE)
        compose.await("alias to be deleted") { repo.aliases.value.none { it.id == alias.id } }
        compose.awaitTagGone(ToolTags.aliasCopy(alias.id))
        compose.awaitTextGone(ALIAS_SERVICE)
    }

    // ------------------------------------------------------------------ 8. settings

    @Test
    fun test08_settings_themeAutoLockClipboardShowPasswords_andBack() {
        ensureUnlocked()
        openSettings()
        val settings = container.settings

        compose.click(ToolTags.THEME_DARK)
        compose.await("theme preference DARK") { settings.preferences.value.themeMode == ThemeMode.DARK }
        compose.await("dark option selected") { compose.isSelected(ToolTags.THEME_DARK) }
        compose.click(ToolTags.THEME_LIGHT)
        compose.await("theme preference LIGHT") { settings.preferences.value.themeMode == ThemeMode.LIGHT }
        compose.await("light option selected") { compose.isSelected(ToolTags.THEME_LIGHT) }
        assertFalse(compose.isSelected(ToolTags.THEME_DARK))

        // Auto-lock: pick an option different from the current one so its text is unique.
        val autoLock = listOf(15, 30, 1).first { it != settings.preferences.value.autoLockMinutes }
        compose.click(ToolTags.AUTO_LOCK_PREFERENCE)
        compose.clickText("$autoLock minutes")
        compose.await("auto-lock preference $autoLock") { settings.preferences.value.autoLockMinutes == autoLock }

        val clipboard = listOf(60, 120, 15).first { it != settings.preferences.value.clipboardClearSeconds }
        compose.click(ToolTags.CLIPBOARD_PREFERENCE)
        compose.clickText("$clipboard seconds")
        compose.await("clipboard preference $clipboard") { settings.preferences.value.clipboardClearSeconds == clipboard }

        val showBefore = settings.preferences.value.showPasswords
        compose.click(ToolTags.SHOW_PASSWORDS_PREFERENCE)
        compose.await("show-passwords preference to toggle") { settings.preferences.value.showPasswords != showBefore }

        backToMore()
        compose.awaitTagGone(ToolTags.THEME_DARK)
        assertTrue(compose.exists(AppTestTags.MAIN_SHELL))

        // Values survive leaving and re-entering the screen.
        compose.click(AppTestTags.MORE_SETTINGS)
        compose.awaitTag(ToolTags.THEME_LIGHT)
        compose.await("light option still selected") { compose.isSelected(ToolTags.THEME_LIGHT) }
        assertEquals(ThemeMode.LIGHT, settings.preferences.value.themeMode)
        assertEquals(autoLock, settings.preferences.value.autoLockMinutes)
        assertEquals(clipboard, settings.preferences.value.clipboardClearSeconds)
    }

    private fun toggleAlias(id: String) = semanticClick(ToolTags.aliasToggle(id))

    /** Clicks via the semantics action, immune to the anchored FAB overlapping the bottom of the scrolled list. */
    private fun semanticClick(tag: String) {
        compose.awaitTag(tag)
        compose.onAllNodesWithTag(tag, useUnmergedTree = true)[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
    }
}
