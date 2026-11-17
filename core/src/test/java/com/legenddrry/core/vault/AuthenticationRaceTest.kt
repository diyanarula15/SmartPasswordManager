package com.legenddrry.core.vault

import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AuthenticationRaceTest {
    @get:Rule val temporary = TemporaryFolder()
    private val password = "race-test-master-password".toCharArray()

    @Test fun lockWhileSetupDerivesKeyPreventsSessionOpening() = runBlocking {
        val cpu = GateDispatcher()
        val vault = vault("setup", cpu)
        cpu.paused = true
        val setup = async(Dispatchers.Unconfined) { vault.setUp(password.copyOf()) }
        assertEquals(1, cpu.pendingCount)
        vault.lock()
        cpu.release()
        assertEquals(AuthResult.ERROR, setup.await())
        assertEquals(AuthState.LOCKED, vault.state.value)
        assertTrue(vault.entries.first().isEmpty())
        // Setup was durably committed; a fresh explicit unlock must still work.
        assertEquals(AuthResult.SUCCESS, vault.unlock(password.copyOf()))
        vault.lock()
    }

    @Test fun lockWhileUnlockDerivesKeyPreventsSessionOpening() = runBlocking {
        val cpu = GateDispatcher()
        val vault = vault("unlock", cpu)
        assertEquals(AuthResult.SUCCESS, vault.setUp(password.copyOf()))
        vault.lock()
        cpu.paused = true
        val unlock = async(Dispatchers.Unconfined) { vault.unlock(password.copyOf()) }
        assertEquals(1, cpu.pendingCount)
        vault.lock()
        cpu.release()
        assertEquals(AuthResult.ERROR, unlock.await())
        assertEquals(AuthState.LOCKED, vault.state.value)
        assertTrue(vault.entries.first().isEmpty())
        assertEquals(AuthResult.SUCCESS, vault.unlock(password.copyOf()))
        vault.lock()
    }

    @Test fun cancelledUnlockCannotPublishSessionAfterKeyDerivation() = runBlocking {
        val cpu = GateDispatcher()
        val vault = vault("cancel-unlock", cpu)
        assertEquals(AuthResult.SUCCESS, vault.setUp(password.copyOf()))
        vault.lock()
        cpu.paused = true
        val caller = StandardTestDispatcher()
        val unlock = async(caller) { vault.unlock(password.copyOf()) }
        caller.scheduler.runCurrent()
        assertEquals(1, cpu.pendingCount)
        // Derive the key, then cancel before the caller resumes from withContext.
        cpu.release()
        unlock.cancel()
        caller.scheduler.runCurrent()
        unlock.join()
        assertTrue(unlock.isCancelled)
        assertEquals(AuthState.LOCKED, vault.state.value)
        assertTrue(vault.entries.first().isEmpty())
        assertEquals(AuthResult.SUCCESS, vault.unlock(password.copyOf()))
        vault.lock()
    }

    @Test fun transferEpochChangesOnLockButNotPasswordReauthentication() = runBlocking {
        val vault = vault("epoch", GateDispatcher())
        assertEquals(AuthResult.SUCCESS, vault.setUp(password.copyOf()))
        val initial = vault.transferSessionGeneration()
        assertEquals(AuthResult.SUCCESS, vault.unlock(password.copyOf()))
        assertEquals(initial, vault.transferSessionGeneration())
        vault.lock()
        val locked = vault.transferSessionGeneration()
        assertTrue(locked != initial)
        assertEquals(AuthResult.SUCCESS, vault.unlock(password.copyOf()))
        assertEquals(locked, vault.transferSessionGeneration())
        vault.lock()
    }

    private fun vault(name: String, cpu: CoroutineDispatcher) = EncryptedVault(
        File(temporary.root, name), FakeKeyWrapper(), iterations = 1_000,
        ioDispatcher = Dispatchers.Unconfined, cpuDispatcher = cpu,
    )

    private class GateDispatcher : CoroutineDispatcher() {
        var paused = false
        private val pending = ArrayDeque<Runnable>()
        val pendingCount: Int get() = pending.size
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (paused) pending.addLast(block) else block.run()
        }
        fun release() {
            paused = false
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }
}
