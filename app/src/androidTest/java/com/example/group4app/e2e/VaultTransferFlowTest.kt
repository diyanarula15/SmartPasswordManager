package com.example.group4app.e2e

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.legenddrry.core.VaultEntry
import com.legenddrry.core.transfer.BackupUnlockMethod
import com.legenddrry.core.transfer.VaultFileCodec
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual MainActivity + Android DocumentsUI save/open; no intercepted activity results. */
@RunWith(AndroidJUnit4::class)
class VaultTransferFlowTest : E2eTestBase() {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Test fun actualSafExportThenImportPreviewsReauthenticatesAndRestoresEveryField() {
        ensureUnlocked()
        val id = UUID.randomUUID().toString()
        val filename = "legenddrry-saf-$id.vault"
        val transferPassword = "Transfer-Audit-73!Local"
        val expected = VaultEntry(id, "SAF transfer account", "https://transfer.example.test/path", "student+π@example.test",
            "Saf-private-credential-92!", "Secure notes café\nSecond line 秘密", "Study", 1_700_000_123_456L)
        runBlocking { container.vaultRepository.add(expected) }
        openSettings()
        val originalFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        try {
            compose.click("vault-transfer-export")
            compose.typeInto("vault-export-master", TEST_MASTER_PASSWORD)
            compose.typeInto("vault-export-transfer", transferPassword)
            compose.typeInto("vault-export-confirm-password", transferPassword)
            compose.click("vault-export-confirm", AUTH_WAIT_MS)
            awaitNative("real create-document picker") { documentRoots().isNotEmpty() }
            chooseDownloads()
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename)
            }
            awaitNative("backup filename editor") {
                documentNodes().firstOrNull { it.isEditable && it.text?.toString()?.endsWith(".vault") == true }
                    ?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
            }
            nativeClick("native Save button") {
                it.text?.toString()?.equals("Save", ignoreCase = true) == true
            }
            compose.awaitText("Encrypted backup saved.", AUTH_WAIT_MS, substring = true)
            // Read ciphertext through shell permission solely to independently check the saved file.
            // Filename is generated from a UUID; no secret is ever passed to a shell command.
            val encrypted = shellBytes("cat /sdcard/Download/$filename")
            try {
                assertEquals(BackupUnlockMethod.TRANSFER_PASSWORD, VaultFileCodec().inspect(encrypted))
                assertFalse(String(encrypted, Charsets.ISO_8859_1).contains(expected.password))
            } finally { encrypted.fill(0) }

            runBlocking { container.vaultRepository.delete(id) }
            assertTrue(vaultEntries().isEmpty())
            // Cancel a genuine picker first; it must not mutate the vault or strand busy state.
            compose.click("vault-transfer-import")
            awaitNative("real open-document picker") { documentRoots().isNotEmpty() }
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            compose.awaitText("Import cancelled. Your vault is unchanged.", AUTH_WAIT_MS)
            assertTrue(vaultEntries().isEmpty())

            compose.click("vault-transfer-import")
            awaitNative("reopened document picker") { documentRoots().isNotEmpty() }
            chooseDownloads()
            nativeClick("saved encrypted backup in Downloads") {
                it.text?.toString() == filename || it.text?.toString() == filename.removeSuffix(".vault")
            }
            compose.awaitTag("vault-import-password", AUTH_WAIT_MS)
            compose.typeInto("vault-import-password", transferPassword)
            compose.click("vault-import-unlock", AUTH_WAIT_MS)
            compose.awaitText("Review vault import", AUTH_WAIT_MS)
            compose.awaitText("1 saved logins")
            compose.awaitText("Study · 1")
            assertTrue(vaultEntries().isEmpty()) // Preview never writes data.

            compose.typeInto("vault-import-master", "Wrong-destination-master-42!")
            compose.click("vault-import-commit", AUTH_WAIT_MS)
            compose.awaitText("Could not import.", AUTH_WAIT_MS, substring = true)
            assertTrue(vaultEntries().isEmpty())
            compose.typeInto("vault-import-master", TEST_MASTER_PASSWORD)
            compose.click("vault-import-commit", AUTH_WAIT_MS)
            compose.awaitText("Import complete: 1 imported", AUTH_WAIT_MS, substring = true)
            assertEquals(listOf(expected), vaultEntries())
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            // Remove only this test's unique synthetic encrypted backup from shared Downloads.
            shellBytes("rm -f /sdcard/Download/$filename").fill(0)
        }
    }

    private fun chooseDownloads() {
        nativeClick("document picker navigation drawer") { node ->
            node.contentDescription?.toString() in setOf("Show roots", "Show navigation drawer", "Navigate up")
        }
        nativeClick("Downloads storage location") { it.text?.toString() == "Downloads" }
    }

    private fun nativeClick(description: String, predicate: (AccessibilityNodeInfo) -> Boolean) {
        awaitNative(description) {
            documentNodes().filter(predicate).any { candidate ->
                var clickable: AccessibilityNodeInfo? = candidate
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
        }
    }

    private fun documentRoots(): List<AccessibilityNodeInfo> =
        (automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow))
            .filter { it.packageName?.toString()?.contains("documentsui") == true }

    private fun documentNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result.add(node)
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        documentRoots().forEach(::visit)
        return result
    }

    private fun awaitNative(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + AUTH_WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Native SAF action timed out: $description")
    }

    private fun shellBytes(command: String): ByteArray =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
}
