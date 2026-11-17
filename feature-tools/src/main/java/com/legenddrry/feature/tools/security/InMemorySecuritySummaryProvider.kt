package com.legenddrry.feature.tools.security

import com.legenddrry.feature.tools.contracts.SecuritySummary
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Demonstration counts only. No passwords are stored, read or scanned here. */
class InMemorySecuritySummaryProvider(
    initial: SecuritySummary = SecuritySummary(totalPasswords = 12, weakPasswords = 3, reusedPasswords = 4),
) : SecuritySummaryProvider {
    private val mutableSummary = MutableStateFlow(initial)
    override val summary = mutableSummary.asStateFlow()
    override val isDemo = true

    fun update(summary: SecuritySummary) {
        mutableSummary.value = summary
    }
}
