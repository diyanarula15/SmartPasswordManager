package com.legenddrry.feature.tools.security

import com.legenddrry.feature.tools.contracts.SecuritySummary
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal sealed interface SecurityUiState {
    data object Loading : SecurityUiState
    data class Ready(val summary: SecuritySummary) : SecurityUiState
    data class Error(val message: String) : SecurityUiState
}

/** Cold flow: every collection starts loading and then follows the injected provider. */
internal fun securityStates(provider: SecuritySummaryProvider): Flow<SecurityUiState> = provider.summary
    .map<SecuritySummary, SecurityUiState> { SecurityUiState.Ready(it) }
    .onStart { emit(SecurityUiState.Loading) }
    .catch { failure ->
        if (failure is CancellationException) throw failure
        emit(SecurityUiState.Error("Password statistics are unavailable. Please try again."))
    }

internal fun securityRecommendations(summary: SecuritySummary): List<String> = buildList {
    if (summary.totalPasswords == 0) {
        add("Add your first password in the vault to see its health summary.")
    } else {
        if (summary.weakPasswords > 0) add("Replace weak passwords in your vault with longer, randomly generated passwords.")
        if (summary.reusedPasswords > 0) add("Give every account its own password. Update entries that share a password.")
        if (summary.weakPasswords == 0 && summary.reusedPasswords == 0) add("No weak or reused entries are reported by the connected provider. Keep using unique, strong passwords.")
        add("Enable multi-factor authentication on accounts that support it.")
    }
}
