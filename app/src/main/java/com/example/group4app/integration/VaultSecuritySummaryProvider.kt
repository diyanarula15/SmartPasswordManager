package com.example.group4app.integration

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.tools.contracts.SecuritySummary
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider
import com.legenddrry.feature.vault.strength.PasswordStrengthEvaluator
import com.legenddrry.feature.vault.strength.StrengthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Real statistics computed from the live vault repository. isDemo stays false.
 *
 * - weak: entries whose password [isWeak] (default: empty, or PasswordStrengthEvaluator level
 *   below STRONG, i.e. VERY_WEAK, WEAK or FAIR).
 * - reused: number of entries whose exact non-empty password is shared with at least one other
 *   entry (3 entries sharing one password + 1 unique -> 3).
 *
 * Computed on Dispatchers.Default; consecutive identical summaries are not re-emitted.
 * Passwords are never logged or retained.
 */
class VaultSecuritySummaryProvider(
    repository: VaultRepository,
    private val isWeak: (String) -> Boolean = { isWeakByStrength(it) },
) : SecuritySummaryProvider {

    override val summary: Flow<SecuritySummary> = repository.entries
        .map { entries -> summarize(entries, isWeak) }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()

    override val isDemo: Boolean get() = false

    companion object {
        /** Default weakness rule: empty, or evaluated below [StrengthLevel.STRONG]. */
        fun isWeakByStrength(password: String): Boolean =
            password.isEmpty() ||
                PasswordStrengthEvaluator.evaluate(password).level < StrengthLevel.STRONG

        internal fun summarize(entries: List<VaultEntry>, isWeak: (String) -> Boolean): SecuritySummary {
            val weak = entries.count { isWeak(it.password) }
            val reused = entries
                .filter { it.password.isNotEmpty() }
                .groupingBy { it.password }
                .eachCount()
                .values
                .filter { it >= 2 }
                .sum()
            return SecuritySummary(totalPasswords = entries.size, weakPasswords = weak, reusedPasswords = reused)
        }
    }
}
