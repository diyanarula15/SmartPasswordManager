// LOCKED API (owner: adapters worker): public constructor (repository: VaultRepository, isWeak = default). SKELETON STUB.
package com.example.group4app.integration

import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.tools.contracts.SecuritySummary
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Real statistics computed from the live vault repository. isDemo stays false. */
class VaultSecuritySummaryProvider(
    repository: VaultRepository,
    private val isWeak: (String) -> Boolean = { it.length < 12 },
) : SecuritySummaryProvider {
    override val summary: Flow<SecuritySummary> = repository.entries.map { list ->
        SecuritySummary(list.size, list.count { isWeak(it.password) }, 0)
    }
}
