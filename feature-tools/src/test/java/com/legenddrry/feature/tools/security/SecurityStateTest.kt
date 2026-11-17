package com.legenddrry.feature.tools.security

import com.legenddrry.feature.tools.contracts.SecuritySummary
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityStateTest {
    @Test fun defaultProviderIsClearlyDemo() {
        val provider = InMemorySecuritySummaryProvider()
        assertTrue(provider.isDemo)
        assertEquals(SecuritySummary(12, 3, 4), provider.summary.value)
    }

    @Test fun productionProviderDefaultsToNotDemo() {
        assertFalse(provider(flowOf(SecuritySummary(0, 0, 0))).isDemo)
    }

    @Test fun loadingComesBeforeProviderValues() = runBlocking {
        val summary = SecuritySummary(20, 4, 6)
        assertEquals(
            listOf(SecurityUiState.Loading, SecurityUiState.Ready(summary)),
            securityStates(provider(flowOf(summary))).toList(),
        )
    }

    @Test fun loadingIsVisibleUntilProviderEmits() = runBlocking {
        assertEquals(listOf(SecurityUiState.Loading), securityStates(provider(emptyFlow())).toList())
    }

    @Test fun emptyPasswordStatisticsHaveUsefulRecommendation() = runBlocking {
        val summary = SecuritySummary(0, 0, 0)
        assertEquals(
            listOf(SecurityUiState.Loading, SecurityUiState.Ready(summary)),
            securityStates(provider(flowOf(summary))).toList(),
        )
        assertTrue(securityRecommendations(summary).single().contains("first password"))
    }

    @Test fun providerUpdatesReachDashboardWithoutManualRefresh() = runBlocking {
        val provider = InMemorySecuritySummaryProvider(SecuritySummary(0, 0, 0))
        val observed = mutableListOf<SecurityUiState>()
        val observer = launch { securityStates(provider).take(3).toList(observed) }
        yield()
        provider.update(SecuritySummary(8, 2, 3))
        observer.join()
        assertEquals(
            listOf(
                SecurityUiState.Loading,
                SecurityUiState.Ready(SecuritySummary(0, 0, 0)),
                SecurityUiState.Ready(SecuritySummary(8, 2, 3)),
            ),
            observed,
        )
    }

    @Test fun providerErrorsBecomeRetryableUiState() = runBlocking {
        val failingProvider = provider(flow { throw IllegalStateException("private adapter detail") })
        val observed = securityStates(failingProvider).toList()
        assertEquals(SecurityUiState.Loading, observed.first())
        assertTrue(observed.last() is SecurityUiState.Error)
        assertFalse((observed.last() as SecurityUiState.Error).message.contains("private adapter detail"))
    }

    @Test fun recommendationsReflectWeakAndReusedCounts() {
        val recommendations = securityRecommendations(SecuritySummary(10, 2, 3))
        assertTrue(recommendations.any { it.contains("weak passwords") })
        assertTrue(recommendations.any { it.contains("share a password") })
        assertTrue(recommendations.any { it.contains("multi-factor") })
    }

    @Test fun healthySummaryDoesNotClaimScanningOrPerfectSecurity() {
        val recommendations = securityRecommendations(SecuritySummary(10, 0, 0))
        assertTrue(recommendations.any { it.contains("connected provider") })
        assertFalse(recommendations.any { it.contains("100%") || it.contains("breach-free") })
    }

    @Test fun invalidCountsAreRejected() {
        val invalid = listOf(Triple(-1, 0, 0), Triple(1, -1, 0), Triple(1, 2, 0), Triple(1, 0, -1), Triple(1, 0, 2))
        invalid.forEach { (total, weak, reused) ->
            var rejected = false
            try { SecuritySummary(total, weak, reused) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("Expected invalid counts to fail: $total/$weak/$reused", rejected)
        }
    }

    private fun provider(values: Flow<SecuritySummary>) = object : SecuritySummaryProvider {
        override val summary = values
    }
}
