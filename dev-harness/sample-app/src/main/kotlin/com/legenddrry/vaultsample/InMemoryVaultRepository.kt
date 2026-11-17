// DEV SCAFFOLDING ONLY. Plain in-memory list: NOT encrypted, NOT persisted. Demo data only.
package com.legenddrry.vaultsample

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryVaultRepository(seed: List<VaultEntry> = emptyList()) : VaultRepository {
    private val state = MutableStateFlow(seed)
    override val entries: Flow<List<VaultEntry>> = state.asStateFlow()

    override suspend fun add(entry: VaultEntry) {
        require(state.value.none { it.id == entry.id }) { "Duplicate id ${entry.id}" }
        state.update { it + entry }
    }

    override suspend fun update(entry: VaultEntry) {
        require(state.value.any { it.id == entry.id }) { "Unknown id ${entry.id}" }
        state.update { list -> list.map { if (it.id == entry.id) entry else it } }
    }

    override suspend fun delete(id: String) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    companion object {
        /** Obviously fake demo values. */
        fun demo(): InMemoryVaultRepository {
            val now = System.currentTimeMillis()
            return InMemoryVaultRepository(
                listOf(
                    VaultEntry("demo-1", "Example Mail", "mail.example.com", "alice@example.com", "Demo-Pass-123!", "Demo entry", "Email", now),
                    VaultEntry("demo-2", "Example Social", "social.example.com", "alice", "Demo-Pass-123!", "", "Social", now - 60_000),
                    VaultEntry("demo-3", "Example Bank", "bank.example.com", "alice.b", "x9#Lq2!vT8@pZr4m", "Fake account", "Banking", now - 120_000),
                    VaultEntry("demo-4", "Work SSO", "sso.example.org", "a.smith", "weak", "", "Work", now - 180_000),
                )
            )
        }
    }
}
