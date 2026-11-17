package com.legenddrry.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.core.crypto.AndroidKeystoreKeyWrapper
import com.legenddrry.core.transfer.BackupUnlockMethod
import com.legenddrry.core.transfer.VaultImportMode
import com.legenddrry.core.transfer.VaultTransferManager
import com.legenddrry.core.vault.EncryptedVault
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Uses real AndroidKeyStore aliases and disk encryption; no crypto/storage mocks. */
@RunWith(AndroidJUnit4::class)
class VaultTransferInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val token = UUID.randomUUID().toString()
    private val sourceDir = File(context.noBackupFilesDir, "transfer-source-$token")
    private val targetDir = File(context.noBackupFilesDir, "transfer-target-$token")
    private val sourceWrapper = AndroidKeystoreKeyWrapper("transfer-source-$token")
    private val targetWrapper = AndroidKeystoreKeyWrapper("transfer-target-$token")
    private val sessions = mutableListOf<EncryptedVault>()

    @After fun cleanup() {
        try {
            sessions.forEach { it.lock() }
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
        } finally {
            try { sourceWrapper.deleteKey() } finally { targetWrapper.deleteKey() }
        }
    }

    @Test fun bothBackupMethodsTransferTwentyEntriesAcrossIndependentKeystoresAndPersist() = runBlocking {
        val sourcePassword = "SourceMaster-42!Offline".toCharArray()
        val targetPassword = "DifferentMaster-73!Local".toCharArray()
        val transferPassword = "PortableTransfer-96!Safe".toCharArray()
        val backups = mutableMapOf<BackupUnlockMethod, ByteArray>()
        try {
            val expected = (1..20).map { index ->
                VaultEntry(
                    id = "portable-$index", title = "Transfer title $index", website = "https://service-$index.example.test",
                    username = "student-$index@example.test", password = "Secret-for-entry-$index!π🔒",
                    notes = "Private notes $index\nSecond line\t\\\"", category = if (index % 2 == 0) "Study" else "Personal",
                    updatedAtEpochMs = 1_700_000_000_000L + index,
                )
            }
            val source = newVault(sourceDir, sourceWrapper)
            assertEquals(AuthState.NEEDS_SETUP, source.awaitSettled())
            assertEquals(AuthResult.SUCCESS, source.setUp(sourcePassword.copyOf()))
            expected.forEach { source.add(it) }
            assertEquals(expected, source.snapshotForTransfer())
            val sourceManager = VaultTransferManager(source, source)
            for (method in BackupUnlockMethod.entries) {
                val masterInput = sourcePassword.copyOf()
                val transferInput = if (method == BackupUnlockMethod.TRANSFER_PASSWORD) transferPassword.copyOf() else null
                val file = sourceManager.exportVault(masterInput, method, transferInput)
                assertTrue(masterInput.all { it == '\u0000' })
                assertTrue(transferInput == null || transferInput.all { it == '\u0000' })
                assertEquals(method, sourceManager.inspectBackup(file))
                assertNoPlaintext(file, expected)
                backups[method] = file
            }
            assertEncryptedFiles(sourceDir, expected)
            source.lock()
            assertEquals(emptyList<VaultEntry>(), source.entries.first())
            // Original device's key is no longer available: portable restore must not use it.
            sourceWrapper.deleteKey()

            var target = newVault(targetDir, targetWrapper)
            assertEquals(AuthState.NEEDS_SETUP, target.awaitSettled())
            assertEquals(AuthResult.SUCCESS, target.setUp(targetPassword.copyOf()))
            for ((method, file) in backups) {
                val localOnly = expected.first().copy(id = "local-${method.name}", password = "Keep-until-verified-${method.name}")
                target.add(localOnly)
                val beforeEntries = target.snapshotForTransfer()
                val beforeDisk = encryptedSnapshot(targetDir)
                val manager = VaultTransferManager(target, target)
                val credential = if (method == BackupUnlockMethod.MASTER_PASSWORD) sourcePassword else transferPassword

                val wrongInput = "Incorrect-transfer-password".toCharArray()
                expectInvalidBackup { manager.unlockBackup(file, wrongInput) }
                assertTrue(wrongInput.all { it == '\u0000' })
                assertEquals(beforeEntries, target.snapshotForTransfer())
                assertDiskUnchanged(beforeDisk, targetDir)

                val damaged = file.copyOf()
                try {
                    damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
                    expectInvalidBackup { manager.unlockBackup(damaged, credential.copyOf()) }
                    assertEquals(beforeEntries, target.snapshotForTransfer())
                    assertDiskUnchanged(beforeDisk, targetDir)
                } finally { damaged.fill(0) }

                val portable = manager.unlockBackup(file, credential.copyOf())
                assertEquals(method, portable.unlockMethod)
                assertEquals(expected, portable.entries)
                val authorizingInput = targetPassword.copyOf()
                val result = manager.restoreVault(portable, VaultImportMode.REPLACE, authorizingInput)
                assertTrue(authorizingInput.all { it == '\u0000' })
                assertEquals(20, result.imported)
                assertEquals(0, result.skipped)
                assertEquals(expected, target.snapshotForTransfer())
                assertEncryptedFiles(targetDir, expected)
                target.lock()
                assertEquals(AuthState.LOCKED, target.state.value)
                assertEquals(emptyList<VaultEntry>(), target.entries.first())

                // Recreate the vault and wrapper, as after process restart, preserving only device key alias/files.
                target = newVault(targetDir, AndroidKeystoreKeyWrapper("transfer-target-$token"))
                assertEquals(AuthState.LOCKED, target.awaitSettled())
                assertEquals(AuthResult.WRONG_PASSWORD, target.unlock(sourcePassword.copyOf()))
                assertEquals(AuthResult.SUCCESS, target.unlock(targetPassword.copyOf()))
                assertEquals(expected, target.snapshotForTransfer())
                assertEncryptedFiles(targetDir, expected)
            }
        } finally {
            backups.values.forEach { it.fill(0) }
            sourcePassword.fill('\u0000')
            targetPassword.fill('\u0000')
            transferPassword.fill('\u0000')
        }
    }

    private fun newVault(dir: File, wrapper: AndroidKeystoreKeyWrapper): EncryptedVault =
        EncryptedVault(dir, wrapper).also(sessions::add)

    private suspend fun EncryptedVault.awaitSettled() =
        withTimeout(10_000) { state.first { it != AuthState.LOADING } }

    private suspend fun expectInvalidBackup(action: suspend () -> Unit) {
        try {
            action()
            fail("An unauthenticated backup must be rejected")
        } catch (_: IllegalArgumentException) {
            // Correct: codec errors are sanitized; destination remains unchanged below.
        }
    }

    private fun encryptedSnapshot(dir: File): Map<String, ByteArray> =
        requireNotNull(dir.listFiles()).filter { it.isFile }.associate { it.name to it.readBytes() }

    private fun assertDiskUnchanged(before: Map<String, ByteArray>, dir: File) {
        val after = encryptedSnapshot(dir)
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) -> assertArrayEquals("Failed authentication changed $name", bytes, after.getValue(name)) }
    }

    private fun assertEncryptedFiles(dir: File, entries: List<VaultEntry>) {
        assertTrue(File(dir, "meta.bin").isFile)
        assertTrue(File(dir, "vault.bin").isFile)
        requireNotNull(dir.listFiles()).filter { it.isFile }.forEach { file ->
            val raw = file.readBytes()
            try { assertNoPlaintext(raw, entries) } finally { raw.fill(0) }
        }
    }

    private fun assertNoPlaintext(bytes: ByteArray, entries: List<VaultEntry>) {
        // ASCII sentinels avoid charset-dependent comparisons while checking every record.
        val raw = String(bytes, Charsets.ISO_8859_1)
        entries.forEach { entry ->
            assertFalse("A saved password appeared in ciphertext", raw.contains(entry.password.substringBefore('π')))
            assertFalse("Private notes appeared in ciphertext", raw.contains(entry.notes.substringBefore('\n')))
        }
        assertFalse(raw.contains("SourceMaster-42!Offline"))
        assertFalse(raw.contains("DifferentMaster-73!Local"))
        assertFalse(raw.contains("PortableTransfer-96!Safe"))
    }
}
