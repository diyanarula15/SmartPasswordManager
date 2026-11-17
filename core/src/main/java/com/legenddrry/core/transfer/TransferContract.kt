package com.legenddrry.core.transfer

import com.legenddrry.core.VaultEntry

enum class BackupUnlockMethod { MASTER_PASSWORD, TRANSFER_PASSWORD }
enum class VaultImportMode { MERGE, REPLACE, ONLY_NEW }
data class PortableVault(val entries: List<VaultEntry>, val createdAtEpochMs: Long, val unlockMethod: BackupUnlockMethod)
data class VaultImportResult(val imported: Int, val skipped: Int, val preservedConflicts: Int)

/** Implemented by the existing encrypted vault; a single encrypted atomic write, never per-record CRUD. */
interface VaultTransferStore {
    suspend fun snapshotForTransfer(): List<VaultEntry>
    suspend fun importFromTransfer(entries: List<VaultEntry>, mode: VaultImportMode): VaultImportResult
}

object VaultTransferLimits {
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_RECORDS = 10_000
    const val MAX_FIELD_CHARS = 131_072
    const val MAX_PASSWORD_CHARS = 1_024
}
