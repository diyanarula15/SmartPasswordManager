package com.legenddrry.core.transfer

import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Session-scoped orchestration. Portable encryption never uses the local Keystore key. */
class VaultTransferManager(
    private val auth: AuthManager,
    private val store: VaultTransferStore,
    private val codec: VaultFileCodec = VaultFileCodec(),
) {
    suspend fun exportVault(
        masterPassword: CharArray,
        method: BackupUnlockMethod,
        transferPassword: CharArray? = null,
    ): ByteArray {
        try {
            verify(masterPassword)
            val credential = if (method == BackupUnlockMethod.MASTER_PASSWORD) masterPassword
                else requireNotNull(transferPassword) { "Enter a transfer password." }
            if (method == BackupUnlockMethod.TRANSFER_PASSWORD) {
                val types = listOf(credential.any(Char::isLowerCase), credential.any(Char::isUpperCase), credential.any(Char::isDigit), credential.any { !it.isLetterOrDigit() }).count { it }
                require(credential.size in 12..VaultTransferLimits.MAX_PASSWORD_CHARS && types >= 3 && credential.toSet().size >= 6) {
                    "Use at least 12 characters, three character types and six distinct characters."
                }
            }
            val snapshot = store.snapshotForTransfer()
            var encrypted: ByteArray? = null
            try {
                withContext(Dispatchers.Default) {
                    encrypted = codec.encrypt(PortableVault(snapshot, System.currentTimeMillis(), method), credential)
                }
                return requireNotNull(encrypted).also { encrypted = null }
            } finally { encrypted?.fill(0) }
        } finally {
            masterPassword.fill('\u0000')
            transferPassword?.fill('\u0000')
        }
    }

    fun inspectBackup(file: ByteArray): BackupUnlockMethod = codec.inspect(file)

    suspend fun unlockBackup(file: ByteArray, credential: CharArray): PortableVault {
        try {
            check(auth.state.value == AuthState.UNLOCKED) { "Unlock your local vault before importing." }
            return withContext(Dispatchers.Default) { codec.decrypt(file, credential) }
        } finally { credential.fill('\u0000') }
    }

    suspend fun restoreVault(
        vault: PortableVault,
        mode: VaultImportMode,
        masterPassword: CharArray? = null,
    ): VaultImportResult {
        try {
            check(auth.state.value == AuthState.UNLOCKED) { "Unlock your local vault before importing." }
            verify(requireNotNull(masterPassword) { "Enter your local master password to authorize the import." })
            return store.importFromTransfer(vault.entries, mode)
        } finally { masterPassword?.fill('\u0000') }
    }

    private suspend fun verify(password: CharArray) {
        require(password.size in 1..VaultTransferLimits.MAX_PASSWORD_CHARS) { "Enter your master password." }
        check(auth.state.value == AuthState.UNLOCKED) { "Unlock your vault first." }
        when (auth.unlock(password)) {
            AuthResult.SUCCESS -> Unit
            AuthResult.TOO_MANY_ATTEMPTS -> throw IllegalStateException("Too many attempts. Wait before trying again.")
            else -> throw IllegalArgumentException("Unable to verify your master password.")
        }
    }
}
