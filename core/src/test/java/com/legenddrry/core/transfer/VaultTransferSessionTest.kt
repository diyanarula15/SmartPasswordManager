package com.legenddrry.core.transfer

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VaultTransferSessionTest {
    @Test fun exportRejectsSessionLockedDuringSnapshotAndWipesBothCredentials() = runBlocking {
        val auth = FakeAuth()
        val store = FakeStore { auth.lock() }
        val master = "Local master password 123!".toCharArray()
        val transfer = "Transfer password 123!".toCharArray()
        try {
            VaultTransferManager(auth, store).exportVault(master, BackupUnlockMethod.TRANSFER_PASSWORD, transfer)
            fail("A transfer must not continue once the vault locks")
        } catch (_: IllegalStateException) { }
        assertTrue(master.all { it == '\u0000' })
        assertTrue(transfer.all { it == '\u0000' })
    }

    @Test fun successfulVerificationDoesNotAuthorizeAnAlreadyLockedSession() = runBlocking {
        val auth = FakeAuth().apply { lockDuringVerification = true }
        val store = FakeStore()
        val master = "Local master password 123!".toCharArray()
        try {
            VaultTransferManager(auth, store).restoreVault(
                PortableVault(emptyList(), 42, BackupUnlockMethod.MASTER_PASSWORD),
                VaultImportMode.REPLACE,
                master,
            )
            fail("A verification result must not authorize writes after locking")
        } catch (_: IllegalStateException) { }
        assertFalse(store.imported)
        assertTrue(master.all { it == '\u0000' })
    }

    @Test fun exportRejectsLockThenReunlockDuringSnapshot() = runBlocking {
        val auth = FakeAuth()
        val store = FakeStore {
            auth.lock()
            auth.state.value = AuthState.UNLOCKED
        }
        val master = "Local master password 123!".toCharArray()
        try {
            VaultTransferManager(auth, store, sessionGeneration = { auth.epoch }).exportVault(
                master, BackupUnlockMethod.MASTER_PASSWORD,
            )
            fail("An old transfer cannot continue in a later unlocked session")
        } catch (_: IllegalStateException) { }
        assertEquals(AuthState.UNLOCKED, auth.state.value)
        assertTrue(master.all { it == '\u0000' })
    }

    @Test fun restoreRejectsLockThenReunlockDuringVerification() = runBlocking {
        val auth = FakeAuth().apply {
            lockDuringVerification = true
            reopenAfterLock = true
        }
        val store = FakeStore()
        try {
            VaultTransferManager(auth, store, sessionGeneration = { auth.epoch }).restoreVault(
                PortableVault(emptyList(), 42, BackupUnlockMethod.MASTER_PASSWORD),
                VaultImportMode.REPLACE, "Local master password 123!".toCharArray(),
            )
            fail("An old verification cannot authorize importing into a later session")
        } catch (_: IllegalStateException) { }
        assertEquals(AuthState.UNLOCKED, auth.state.value)
        assertFalse(store.imported)
    }

    @Test fun lockedVaultRejectsBackupPreviewAndWipesCredential() = runBlocking {
        val auth = FakeAuth().apply { lock() }
        val password = "Backup password 123!".toCharArray()
        try {
            VaultTransferManager(auth, FakeStore()).unlockBackup(byteArrayOf(), password)
            fail("A locked local vault must not expose a backup preview")
        } catch (_: IllegalStateException) { }
        assertTrue(password.all { it == '\u0000' })
    }

    private class FakeAuth : AuthManager {
        override val state = MutableStateFlow(AuthState.UNLOCKED)
        var lockDuringVerification = false
        var reopenAfterLock = false
        var epoch = 0L
        override suspend fun setUp(masterPassword: CharArray) = AuthResult.ALREADY_SET_UP
        override suspend fun unlock(masterPassword: CharArray): AuthResult {
            if (lockDuringVerification) lock()
            if (reopenAfterLock) state.value = AuthState.UNLOCKED
            return AuthResult.SUCCESS
        }
        override fun lock() { epoch++; state.value = AuthState.LOCKED }
    }

    private class FakeStore(private val snapshot: () -> Unit = {}) : VaultTransferStore {
        var imported = false
        override suspend fun snapshotForTransfer(): List<VaultEntry> {
            snapshot()
            return emptyList()
        }
        override suspend fun importFromTransfer(entries: List<VaultEntry>, mode: VaultImportMode): VaultImportResult {
            imported = true
            return VaultImportResult(entries.size, 0, 0)
        }
    }
}
