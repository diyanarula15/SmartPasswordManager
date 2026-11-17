package com.legenddrry.feature.tools.approvals

import com.legenddrry.feature.tools.contracts.ApprovalsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lifecycle belongs to the supplied scope; no global coroutine scope is retained. */
internal class ApprovalStateHolder(
    private val repository: ApprovalsRepository,
    private val scope: CoroutineScope,
) {
    val requests = repository.requests
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableFeedback = MutableStateFlow<String?>(null)
    val feedback = mutableFeedback.asStateFlow()

    fun createDemoRequest() = perform("Demo login request created locally.") {
        repository.createDemoRequest()
    }

    fun approve(id: String) = perform(
        if (repository.requests.value.firstOrNull { it.id == id }?.isDemo != false)
            "Demo request approved locally. No external login was authorized."
        else "Login request approved.",
    ) { repository.approve(id) }

    fun deny(id: String) = perform(
        if (repository.requests.value.firstOrNull { it.id == id }?.isDemo != false)
            "Demo request denied locally." else "Login request denied.",
    ) { repository.deny(id) }

    fun dismissFeedback() { mutableFeedback.value = null }

    suspend fun refreshExpirations() {
        try {
            repository.expirePendingRequests()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableFeedback.value = error.message ?: "Could not refresh login request statuses."
        }
    }

    private fun perform(success: String, action: suspend () -> Unit) {
        if (!mutableBusy.compareAndSet(false, true)) return
        scope.launch {
            try {
                action()
                mutableFeedback.value = success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableFeedback.value = error.message ?: "Could not update the login request. Try again."
            } finally {
                mutableBusy.value = false
            }
        }
    }
}
