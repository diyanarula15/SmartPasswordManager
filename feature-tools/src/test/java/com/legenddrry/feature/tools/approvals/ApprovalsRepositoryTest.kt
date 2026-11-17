package com.legenddrry.feature.tools.approvals

import com.legenddrry.feature.tools.contracts.ApprovalStatus
import com.legenddrry.feature.tools.contracts.LoginRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApprovalsRepositoryTest {
    private fun request(id: String = "seed", expiry: Long = 2_000L, status: ApprovalStatus = ApprovalStatus.PENDING) =
        LoginRequest(id, "Test browser", "Sample location", 1_000, expiry, status)

    @Test fun emptyRepositoryHasNoRequests() {
        assertTrue(InMemoryApprovalsRepository().requests.value.isEmpty())
    }

    @Test fun demoCreationPublishesLocalPendingRequest() = runBlocking {
        val repository = InMemoryApprovalsRepository(clock = { 1_000L }, demoRequestLifetimeMs = 500L)
        val created = repository.createDemoRequest()
        assertEquals(listOf(created), repository.requests.value)
        assertEquals(ApprovalStatus.PENDING, created.status)
        assertEquals(1_000L, created.requestedAtEpochMs)
        assertEquals(1_500L, created.expiresAtEpochMs)
        assertTrue(created.isDemo)
        assertTrue(created.approximateLocation.contains("Sample"))
    }

    @Test fun approvalMovesRequestIntoHistory() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 1_500L })
        repository.approve("seed")
        val updated = repository.requests.value.single()
        assertEquals(ApprovalStatus.APPROVED, updated.status)
        assertEquals(1_500L, updated.resolvedAtEpochMs)
        assertEquals(0, repository.requests.value.count { it.status == ApprovalStatus.PENDING })
        assertEquals(1, repository.requests.value.count { it.status != ApprovalStatus.PENDING })
    }

    @Test fun denyPendingRequest() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 1_500L })
        repository.deny("seed")
        assertEquals(ApprovalStatus.DENIED, repository.requests.value.single().status)
    }

    @Test fun repeatedApprovalFailsAndPreservesFirstDecision() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 1_500L })
        repository.approve("seed")
        assertFails<IllegalStateException> { repository.approve("seed") }
        assertFails<IllegalStateException> { repository.deny("seed") }
        assertEquals(ApprovalStatus.APPROVED, repository.requests.value.single().status)
    }

    @Test fun deniedRequestCannotBeApproved() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 1_500L })
        repository.deny("seed")
        assertFails<IllegalStateException> { repository.approve("seed") }
        assertEquals(ApprovalStatus.DENIED, repository.requests.value.single().status)
    }

    @Test fun expiredRequestCannotBeApprovedWithoutPriorSweep() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 2_000L })
        assertFails<IllegalStateException> { repository.approve("seed") }
        val expired = repository.requests.value.single()
        assertEquals(ApprovalStatus.EXPIRED, expired.status)
        assertEquals(2_000L, expired.resolvedAtEpochMs)
    }

    @Test fun expiredRequestCannotBeDenied() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 2_100L })
        assertFails<IllegalStateException> { repository.deny("seed") }
        assertEquals(ApprovalStatus.EXPIRED, repository.requests.value.single().status)
    }

    @Test fun expirySweepOnlyExpiresOverduePendingRequests() = runBlocking {
        val approved = request("approved", status = ApprovalStatus.APPROVED)
        val repository = InMemoryApprovalsRepository(
            listOf(request("overdue"), request("future", expiry = 5_000L), approved), clock = { 2_000L })
        repository.expirePendingRequests()
        assertEquals(listOf(ApprovalStatus.EXPIRED, ApprovalStatus.PENDING, ApprovalStatus.APPROVED),
            repository.requests.value.map { it.status })
        assertEquals(approved, repository.requests.value.last())
        val snapshot = repository.requests.value
        repository.expirePendingRequests()
        assertEquals(snapshot, repository.requests.value)
    }

    @Test fun historyRetainsAllDecisionsAndExpiry() = runBlocking {
        var now = 1_500L
        val repository = InMemoryApprovalsRepository(
            listOf(request("a"), request("b"), request("c")), clock = { now })
        repository.approve("a")
        repository.deny("b")
        now = 2_000L
        repository.expirePendingRequests()
        assertEquals(setOf(ApprovalStatus.APPROVED, ApprovalStatus.DENIED, ApprovalStatus.EXPIRED),
            repository.requests.value.map { it.status }.toSet())
    }

    @Test fun missingRequestFailsWithoutLosingData() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 1_500L })
        assertFails<IllegalArgumentException> { repository.approve("missing") }
        assertEquals(listOf(request()), repository.requests.value)
    }

    @Test fun concurrentDecisionsResolveExactlyOnce() = runBlocking {
        val repository = InMemoryApprovalsRepository(listOf(request()), clock = { 1_500L })
        val results = listOf(
            async(Dispatchers.Default) { runCatching { repository.approve("seed") } },
            async(Dispatchers.Default) { runCatching { repository.deny("seed") } },
        ).awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertNotEquals(ApprovalStatus.PENDING, repository.requests.value.single().status)
    }

    @Test fun concurrentCreationHasUniqueIdsAndKeepsEveryRequest() = runBlocking {
        val repository = InMemoryApprovalsRepository(clock = { 1_000L })
        val created = (1..100).map { async(Dispatchers.Default) { repository.createDemoRequest() } }.awaitAll()
        assertEquals(100, created.map { it.id }.distinct().size)
        assertEquals(100, repository.requests.value.size)
    }

    @Test fun duplicateSeedIdsAreRejected() {
        assertFails<IllegalArgumentException> { InMemoryApprovalsRepository(listOf(request(), request())) }
    }

    @Test fun callerChangingSeedListDoesNotMutateRepository() {
        val seed = mutableListOf(request())
        val repository = InMemoryApprovalsRepository(seed)
        seed.clear()
        assertEquals(1, repository.requests.value.size)
    }

    private inline fun <reified T : Throwable> assertFails(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            if (error is T) return
            throw AssertionError("Expected ${T::class.java.simpleName}, got $error", error)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
