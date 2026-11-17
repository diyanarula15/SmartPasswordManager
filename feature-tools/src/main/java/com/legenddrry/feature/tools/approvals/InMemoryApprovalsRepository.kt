package com.legenddrry.feature.tools.approvals

import com.legenddrry.feature.tools.contracts.ApprovalStatus
import com.legenddrry.feature.tools.contracts.ApprovalsRepository
import com.legenddrry.feature.tools.contracts.LoginRequest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session-only demo storage. No backend or third-party authorization occurs. */
class InMemoryApprovalsRepository(
    initialRequests: List<LoginRequest> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val demoRequestLifetimeMs: Long = 5 * 60 * 1_000L,
) : ApprovalsRepository {
    init {
        require(initialRequests.map { it.id }.distinct().size == initialRequests.size) {
            "Login request IDs must be unique."
        }
        require(demoRequestLifetimeMs > 0) { "Request lifetime must be positive." }
    }

    private val mutex = Mutex()
    private val mutableRequests = MutableStateFlow(initialRequests.toList())
    override val requests: StateFlow<List<LoginRequest>> = mutableRequests.asStateFlow()

    override suspend fun createDemoRequest(): LoginRequest = mutex.withLock {
        val now = clock()
        val request = LoginRequest(
            id = UUID.randomUUID().toString(),
            deviceName = "Simulated browser on demo laptop",
            approximateLocation = "Sample location: Bengaluru, India",
            requestedAtEpochMs = now,
            expiresAtEpochMs = now + demoRequestLifetimeMs,
            isDemo = true,
        )
        mutableRequests.value = listOf(request) + mutableRequests.value
        request
    }

    override suspend fun approve(id: String) = resolve(id, ApprovalStatus.APPROVED)

    override suspend fun deny(id: String) = resolve(id, ApprovalStatus.DENIED)

    private suspend fun resolve(id: String, status: ApprovalStatus) = mutex.withLock {
        val now = clock()
        expireLocked(now)
        val current = mutableRequests.value.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("This login request is no longer available.")
        check(current.status == ApprovalStatus.PENDING) {
            if (current.status == ApprovalStatus.EXPIRED) {
                "This login request has expired. Create a new demo request."
            } else {
                "This login request has already been ${current.status.name.lowercase()}."
            }
        }
        mutableRequests.value = mutableRequests.value.map { request ->
            if (request.id == id) request.copy(status = status, resolvedAtEpochMs = now) else request
        }
    }

    override suspend fun expirePendingRequests() = mutex.withLock { expireLocked(clock()) }

    private fun expireLocked(now: Long) {
        mutableRequests.value = mutableRequests.value.map { request ->
            if (request.status == ApprovalStatus.PENDING && request.expiresAtEpochMs <= now) {
                request.copy(status = ApprovalStatus.EXPIRED, resolvedAtEpochMs = request.expiresAtEpochMs)
            } else request
        }
    }
}
