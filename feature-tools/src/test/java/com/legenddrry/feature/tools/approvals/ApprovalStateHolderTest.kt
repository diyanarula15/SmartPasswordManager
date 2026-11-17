package com.legenddrry.feature.tools.approvals

import com.legenddrry.feature.tools.contracts.ApprovalStatus
import com.legenddrry.feature.tools.contracts.ApprovalsRepository
import com.legenddrry.feature.tools.contracts.LoginRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApprovalStateHolderTest {
    @Test fun actionsUpdatePublishedStateAndFeedback() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = InMemoryApprovalsRepository(clock = { 1_000L })
            val holder = ApprovalStateHolder(repository, scope)
            holder.createDemoRequest()
            assertEquals(1, holder.requests.value.size)
            assertTrue(holder.feedback.value!!.contains("created locally"))
            assertFalse(holder.feedbackIsError.value)
            assertFalse(holder.busy.value)
            holder.approve(holder.requests.value.single().id)
            assertEquals(ApprovalStatus.APPROVED, holder.requests.value.single().status)
            assertTrue(holder.feedback.value!!.contains("No external login"))
            holder.approve(holder.requests.value.single().id)
            assertTrue(holder.feedback.value!!.contains("already been approved"))
            assertTrue(holder.feedbackIsError.value)
            assertFalse(holder.busy.value)
            holder.dismissFeedback()
            assertNull(holder.feedback.value)
            assertFalse(holder.feedbackIsError.value)
        } finally { scope.cancel() }
    }

    @Test fun mutationErrorsAreDisplayedAndBusyResets() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val holder = ApprovalStateHolder(FailingRepository(), scope)
            holder.createDemoRequest()
            assertEquals("Storage unavailable", holder.feedback.value)
            assertTrue(holder.feedbackIsError.value)
            assertFalse(holder.busy.value)
        } finally { scope.cancel() }
    }

    @Test fun expirationErrorsAreDisplayed() = runBlocking {
        val holder = ApprovalStateHolder(FailingRepository(), this)
        holder.refreshExpirations()
        assertEquals("Storage unavailable", holder.feedback.value)
        assertTrue(holder.feedbackIsError.value)
    }

    private class FailingRepository : ApprovalsRepository {
        override val requests = MutableStateFlow(emptyList<LoginRequest>())
        override suspend fun createDemoRequest(): LoginRequest = error("Storage unavailable")
        override suspend fun approve(id: String): Unit = error("Storage unavailable")
        override suspend fun deny(id: String): Unit = error("Storage unavailable")
        override suspend fun expirePendingRequests(): Unit = error("Storage unavailable")
    }
}
