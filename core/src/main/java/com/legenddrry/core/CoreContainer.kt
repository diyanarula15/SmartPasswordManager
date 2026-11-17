// LOCKED API (owner: core worker). Keep `CoreContainer.create(context)`, `auth`, `vaultRepository`.
package com.legenddrry.core

import android.content.Context
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.crypto.AndroidKeystoreKeyWrapper
import com.legenddrry.core.vault.EncryptedVault
import java.io.File
import com.legenddrry.core.transfer.VaultTransferManager

/**
 * Application-scoped core services. Create ONCE (Application.onCreate) and share the instances.
 *
 * [auth] starts in LOADING and moves to NEEDS_SETUP or LOCKED on its own (background IO, a
 * single file-existence check). [vaultRepository] emits an empty list while not UNLOCKED and
 * throws IllegalStateException("Vault is locked") from add/update/delete while locked.
 *
 * Storage: `noBackupFilesDir/legenddrry/` (excluded from cloud backup; the metadata is also bound
 * to this device's Android Keystore, so a copied vault cannot be opened elsewhere).
 */
class CoreContainer private constructor(
    val auth: AuthManager,
    val vaultRepository: VaultRepository,
    val transfer: VaultTransferManager,
) {
    companion object {
        private const val DIR_NAME = "legenddrry"

        fun create(context: Context): CoreContainer {
            val dir = File(context.applicationContext.noBackupFilesDir, DIR_NAME)
            val vault = EncryptedVault(dir, AndroidKeystoreKeyWrapper())
            return CoreContainer(
                vault,
                vault,
                VaultTransferManager(vault, vault, sessionGeneration = vault::transferSessionGeneration),
            )
        }
    }
}
