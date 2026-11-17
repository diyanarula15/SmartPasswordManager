package com.legenddrry.core.transfer

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.core.vault.EncryptedVault
import com.legenddrry.core.vault.FakeKeyWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultTransferEndToEndTest {
    @get:Rule val temporary = TemporaryFolder()
    private suspend fun newVault(dir: File, wrapper: FakeKeyWrapper, password: String): EncryptedVault {
        val vault = EncryptedVault(dir, wrapper, iterations = 1000, ioDispatcher = Dispatchers.Unconfined)
        withTimeout(10_000) { vault.state.first { it != AuthState.LOADING } }
        assertEquals(AuthResult.SUCCESS, vault.setUp(password.toCharArray()))
        return vault
    }

    @Test fun twentyRecordsCrossIndependentEncryptedVaultsUsingBothPortableMethods() = runBlocking {
        val sourceDir = temporary.newFolder("source-device")
        val destinationDir = temporary.newFolder("destination-device")
        val source = newVault(sourceDir, FakeKeyWrapper(), "Source master password 2026!")
        val destinationWrapper = FakeKeyWrapper()
        val destination = newVault(destinationDir, destinationWrapper, "Destination DIFFERENT master 2026!")
        val records = (0 until 20).map { i -> VaultEntry("stable-$i", "Service $i", "https://service$i.example/path", "学生+$i@example.com",
            "ActualCredentialMarker-$i-秘密🔑" + if (i == 19) "x".repeat(8192) else "", "Secure note $i\nUnicode café • Ω", "Category ${i % 4}", 1700000000000L + i) }
        records.forEach { source.add(it) }
        val from = VaultTransferManager(source, source)
        val to = VaultTransferManager(destination, destination)
        BackupUnlockMethod.entries.forEach { method ->
            val bytes = from.exportVault("Source master password 2026!".toCharArray(), method,
                if (method == BackupUnlockMethod.TRANSFER_PASSWORD) "Separate-transfer-password-2026!".toCharArray() else null)
            val file = File(temporary.root,"${method.name}.vault").apply { writeBytes(bytes) }
            val copied = File(temporary.root,"copied-${method.name}.vault").also { file.copyTo(it) }
            assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("ActualCredentialMarker"))
            assertEquals(method, to.inspectBackup(copied.readBytes()))
            val secret = if(method == BackupUnlockMethod.MASTER_PASSWORD) "Source master password 2026!" else "Separate-transfer-password-2026!"
            val decoded = to.unlockBackup(copied.readBytes(),secret.toCharArray())
            assertEquals(records,decoded.entries)
            val result = to.restoreVault(decoded,VaultImportMode.REPLACE,"Destination DIFFERENT master 2026!".toCharArray())
            assertEquals(20,result.imported)
            assertEquals(records,destination.snapshotForTransfer())
            bytes.fill(0)
        }
        destination.lock()
        val reopened = EncryptedVault(destinationDir,destinationWrapper,iterations=1000,ioDispatcher=Dispatchers.Unconfined)
        withTimeout(10_000) { reopened.state.first { it != AuthState.LOADING } }
        assertEquals(AuthResult.WRONG_PASSWORD,reopened.unlock("Source master password 2026!".toCharArray()))
        assertEquals(AuthResult.SUCCESS,reopened.unlock("Destination DIFFERENT master 2026!".toCharArray()))
        assertEquals(records,reopened.snapshotForTransfer())
        assertFalse(String(File(destinationDir,"vault.bin").readBytes(),Charsets.ISO_8859_1).contains("ActualCredentialMarker"))
    }

    @Test fun failedAuthenticationNeverChangesDestinationAndSecretsAreCleared() = runBlocking {
        val dir = temporary.newFolder("protected")
        val vault = newVault(dir,FakeKeyWrapper(),"Local master password 2026!")
        val original = VaultEntry("keep","Keep","https://keep.example","u","p","note","Personal",42)
        vault.add(original)
        val manager = VaultTransferManager(vault,vault)
        val before = File(dir,"vault.bin").readBytes()
        val wrong = "Wrong password 2026!".toCharArray()
        try {
            manager.restoreVault(PortableVault(emptyList(),1,BackupUnlockMethod.TRANSFER_PASSWORD),VaultImportMode.REPLACE,wrong)
            fail("Wrong local master password must never replace data")
        } catch (_: IllegalArgumentException) { }
        assertTrue(wrong.all { it == '\u0000' })
        try {
            manager.restoreVault(PortableVault(emptyList(),1,BackupUnlockMethod.TRANSFER_PASSWORD),VaultImportMode.MERGE,"wrong local password".toCharArray())
            fail("Merge must verify the local password too")
        } catch (_: IllegalArgumentException) { }
        assertArrayEquals(before,File(dir,"vault.bin").readBytes())
        assertEquals(listOf(original),vault.snapshotForTransfer())
        val exportWrong = "Wrong password 2026!".toCharArray()
        try { manager.exportVault(exportWrong,BackupUnlockMethod.MASTER_PASSWORD);fail("Must reauthenticate export") }
        catch (_: IllegalArgumentException) { }
        assertTrue(exportWrong.all { it == '\u0000' })
    }
}
