package com.example.group4app.integration

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.tools.contracts.SecuritySummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecuritySummaryProviderTest {

    private class FakeVault(initial: List<VaultEntry> = emptyList()) : VaultRepository {
        val state = MutableStateFlow(initial)
        override val entries: Flow<List<VaultEntry>> = state
        override suspend fun add(entry: VaultEntry) { state.value = state.value + entry }
        override suspend fun update(entry: VaultEntry) {
            state.value = state.value.map { if (it.id == entry.id) entry else it }
        }
        override suspend fun delete(id: String) { state.value = state.value.filterNot { it.id == id } }
    }

    private fun entry(id: String, password: String) =
        VaultEntry(id, "T$id", "example.com", "user", password, "", "General", 0L)

    private val strong1 = "x9#Lq2!vT8@pZr4m"
    private val strong2 = "Qm7&wR2^zK9!bN4\$tY"

    @Test
    fun emptyVaultIsAllZeroAndNotDemo() = runBlocking {
        val provider = VaultSecuritySummaryProvider(FakeVault())
        assertEquals(SecuritySummary(0, 0, 0), provider.summary.first())
        assertFalse(provider.isDemo)
    }

    @Test
    fun defaultWeakRuleCountsBelowStrongAndEmpty() = runBlocking {
        assertTrue(VaultSecuritySummaryProvider.isWeakByStrength(""))
        assertTrue(VaultSecuritySummaryProvider.isWeakByStrength("weak"))
        assertTrue(VaultSecuritySummaryProvider.isWeakByStrength("password"))
        assertFalse(VaultSecuritySummaryProvider.isWeakByStrength(strong1))
        assertFalse(VaultSecuritySummaryProvider.isWeakByStrength(strong2))

        val vault = FakeVault(
            listOf(entry("1", ""), entry("2", "weak"), entry("3", "password"), entry("4", strong1), entry("5", strong2))
        )
        assertEquals(SecuritySummary(5, 3, 0), VaultSecuritySummaryProvider(vault).summary.first())
    }

    @Test
    fun customWeakPredicateIsUsed() = runBlocking {
        val vault = FakeVault(listOf(entry("1", "aaaa"), entry("2", "bbbbbbbb"), entry("3", "c")))
        val provider = VaultSecuritySummaryProvider(vault) { it.length < 5 }
        assertEquals(SecuritySummary(3, 2, 0), provider.summary.first())
    }

    @Test
    fun reuseCountsEntriesNotGroupsAndIgnoresEmpty() = runBlocking {
        val vault = FakeVault(
            listOf(
                entry("1", strong1), entry("2", strong1), entry("3", strong1), entry("4", strong2),
            )
        )
        assertEquals(SecuritySummary(4, 0, 3), VaultSecuritySummaryProvider(vault).summary.first())

        val mixed = FakeVault(
            listOf(
                entry("1", strong1), entry("2", strong1),
                entry("3", "weak"), entry("4", "weak"),
                entry("5", ""), entry("6", ""),
                entry("7", strong2),
                entry("8", strong1.uppercase()), // exact match only
            )
        )
        val s = VaultSecuritySummaryProvider(mixed).summary.first()
        assertEquals(8, s.totalPasswords)
        assertEquals(4, s.reusedPasswords)
        assertEquals(4, s.weakPasswords) // two "weak", two empty
    }

    @Test
    fun emitsUpdatesOnNewVaultEmissionsAndSkipsDuplicates() = runBlocking {
        val vault = FakeVault()
        val provider = VaultSecuritySummaryProvider(vault)
        val received = Channel<SecuritySummary>(Channel.UNLIMITED)
        val job = launch { provider.summary.collect { received.send(it) } }
        suspend fun next() = withTimeout(5_000) { received.receive() }

        assertEquals(SecuritySummary(0, 0, 0), next())
        vault.add(entry("1", strong1))
        assertEquals(SecuritySummary(1, 0, 0), next())
        vault.add(entry("2", strong1))
        assertEquals(SecuritySummary(2, 0, 2), next())
        // Same statistics (title change only) must not be re-emitted.
        vault.update(entry("2", strong1).copy(title = "Renamed"))
        vault.add(entry("3", "weak"))
        assertEquals(SecuritySummary(3, 1, 2), next())
        vault.delete("1")
        assertEquals(SecuritySummary(2, 1, 0), next())

        job.cancel()
        assertTrue(received.tryReceive().isFailure)
    }
}
