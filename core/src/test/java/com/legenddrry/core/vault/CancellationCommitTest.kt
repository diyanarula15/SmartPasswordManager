package com.legenddrry.core.vault

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CancellationCommitTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cancellationOnDispatcherReturnCannotLoseCommittedRecordOnNextWrite() = runBlocking {
        val io = PausedIoDispatcher()
        val caller = StandardTestDispatcher()
        val wrapper = FakeKeyWrapper()
        val dir = File(temporary.root, "cancel-after-commit")
        val password = "cancel-commit-master".toCharArray()
        val vault = EncryptedVault(dir, wrapper, iterations = 1_000, ioDispatcher = io, cpuDispatcher = Dispatchers.Unconfined)
        var reopened: EncryptedVault? = null
        try {
            assertEquals(AuthState.NEEDS_SETUP, vault.state.first { it != AuthState.LOADING })
            assertEquals(AuthResult.SUCCESS, vault.setUp(password.copyOf()))
            val first = entry("first")
            val second = entry("second")
            io.pause = true
            val mutation = launch(caller) { vault.add(first) }
            // The mutation suspends while its actual disk write waits in the I/O dispatcher.
            caller.scheduler.runCurrent()
            assertEquals(1, io.pendingCount)
            // Complete the encrypted atomic write, but do not run the caller continuation.
            io.completeNext()
            // This is the exact prompt-cancellation window on return from withContext(IO).
            mutation.cancel()
            caller.scheduler.runCurrent()
            mutation.join()
            assertTrue(mutation.isCancelled)
            io.pause = false

            // Disk has committed: the live session must already expose the same record.
            assertEquals(listOf(first), vault.entries.first())
            assertEquals(listOf(first), vault.snapshotForTransfer())
            vault.add(second)
            assertEquals(listOf(first, second), vault.snapshotForTransfer())
            vault.lock()

            reopened = EncryptedVault(dir, wrapper, iterations = 1_000, ioDispatcher = io, cpuDispatcher = Dispatchers.Unconfined)
            assertEquals(AuthState.LOCKED, reopened.state.first { it != AuthState.LOADING })
            assertEquals(AuthResult.SUCCESS, reopened.unlock(password.copyOf()))
            // The next ordinary CRUD write must preserve the canceled caller's committed entry.
            assertEquals(listOf(first, second), reopened.snapshotForTransfer())
        } finally {
            io.pause = false
            vault.lock()
            reopened?.lock()
            password.fill('\u0000')
        }
    }

    private fun entry(id: String) = VaultEntry(
        id, "Title $id", "https://$id.example.test", "user-$id", "secret-$id", "notes-$id", "Study", 42,
    )

    /** Gates only dispatch; the production write/encryption/rename implementation runs intact. */
    private class PausedIoDispatcher : CoroutineDispatcher() {
        var pause = false
        private val pending = ArrayDeque<Runnable>()
        val pendingCount: Int get() = pending.size
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (pause) pending.addLast(block) else block.run()
        }
        fun completeNext() { pending.removeFirst().run() }
    }
}
