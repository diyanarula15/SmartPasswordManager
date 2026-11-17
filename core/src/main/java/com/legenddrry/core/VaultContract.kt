// Frozen shared contract (agreed with Developer 2's feature-vault module). Do not change.
package com.legenddrry.core

import kotlinx.coroutines.flow.Flow

data class VaultEntry(
    val id: String,
    val title: String,
    val website: String,
    val username: String,
    val password: String,
    val notes: String,
    val category: String,
    val updatedAtEpochMs: Long
)

interface VaultRepository {
    val entries: Flow<List<VaultEntry>>
    suspend fun add(entry: VaultEntry)
    suspend fun update(entry: VaultEntry)
    suspend fun delete(id: String)
}
