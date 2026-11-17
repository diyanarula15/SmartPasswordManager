package com.example.group4app.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises strength evaluation through both the editor and the production summary adapter. */
@RunWith(AndroidJUnit4::class)
class UnicodeVaultFlowTest : E2eTestBase() {
    @Test
    fun unicodePasswordCanBeSavedReopenedAndCountedBySecurityDashboard() {
        ensureUnlocked()
        val password = "İİİİpassword123!"
        addEntryViaUi("Unicode account", "unicode@example.com", password)
        assertEquals(password, vaultEntries().single().password)
        compose.clickText("Unicode account")
        compose.awaitTag(VaultTestTags.DETAIL_SCREEN)
        compose.click(VaultTestTags.DETAIL_TOGGLE_VISIBILITY)
        compose.await("Unicode password can be revealed") {
            compose.textOrNull(VaultTestTags.DETAIL_PASSWORD) == password
        }
        compose.click(VaultTestTags.DETAIL_CLOSE)
        openSecurityDashboard()
        compose.await("Unicode password is included in the live security statistics") {
            compose.intOf(ToolTags.SECURITY_TOTAL) == 1 &&
                compose.intOf(ToolTags.SECURITY_WEAK) != null
        }
    }
}
