package com.legenddrry.core.vault

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.core.transfer.VaultImportMode
import com.legenddrry.core.transfer.VaultImportResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

class VaultTransferStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val wrapper = FakeKeyWrapper()
    private val password = "test master password".toCharArray()
    private fun entry(id: String, title: String = id) = VaultEntry(id, title, "https://example.com", "user", "unique-secret-$id", "notes", "Login", 123L)
    private fun vault(dir: File, io: CoroutineDispatcher = Dispatchers.IO) = EncryptedVault(dir, wrapper, iterations = 1000, ioDispatcher = io)
    private suspend fun setUp(dir: File, io: CoroutineDispatcher = Dispatchers.IO): EncryptedVault = vault(dir, io).also {
        withTimeout(10_000) { it.state.first { state -> state != AuthState.LOADING } }
        assertEquals(AuthResult.SUCCESS, it.setUp(password))
    }
    private suspend fun reopen(dir: File): EncryptedVault = vault(dir).also {
        withTimeout(10_000) { it.state.first { state -> state != AuthState.LOADING } }
        assertEquals(AuthResult.SUCCESS, it.unlock(password))
    }

    @Test fun snapshotAndImportRequireUnlockedVault() = runBlocking {
        val dir = tmp.newFolder("locked")
        val vault = setUp(dir)
        vault.add(entry("old"))
        vault.lock()
        try { vault.snapshotForTransfer(); fail("Expected locked snapshot to fail") } catch (_: IllegalStateException) { }
        try { vault.importFromTransfer(listOf(entry("new")), VaultImportMode.REPLACE); fail("Expected locked import to fail") } catch (_: IllegalStateException) { }
        assertEquals(listOf(entry("old")), reopen(dir).snapshotForTransfer())
    }

    @Test fun mergeWritesAllRecordsOnceAndSurvivesReopen() = runBlocking {
        val dir = tmp.newFolder("merge")
        val vault = setUp(dir)
        vault.add(entry("old"))
        val input = listOf(entry("new"), entry("other"))
        assertEquals(VaultImportResult(2, 0, 0), vault.importFromTransfer(input, VaultImportMode.MERGE))
        val expected = listOf(entry("old")) + input
        assertEquals(expected, vault.snapshotForTransfer())
        vault.lock()
        assertEquals(expected, reopen(dir).snapshotForTransfer())
        assertFalse(File(dir, "vault.bin.tmp").exists())
    }

    @Test fun replacementIsCompleteAndEncryptedOnDisk() = runBlocking {
        val dir = tmp.newFolder("replace")
        val vault = setUp(dir)
        vault.add(entry("old"))
        val incoming = entry("new").copy(notes = "private-transfer-note-marker")
        vault.importFromTransfer(listOf(incoming), VaultImportMode.REPLACE)
        assertEquals(listOf(incoming), reopen(dir).snapshotForTransfer())
        val bytes = File(dir, "vault.bin").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains("private-transfer-note-marker"))
        assertFalse(bytes.contains(incoming.password))
    }

    @Test fun realFilesystemWriteFailurePreservesOriginalDiskAndCache() = runBlocking {
        val dir = tmp.newFolder("rollback")
        val vault = setUp(dir)
        vault.add(entry("old"))
        val before = File(dir, "vault.bin").readBytes()
        val blocker = File(dir, "vault.bin.tmp")
        assertTrue(blocker.mkdir())
        File(blocker, "nonempty").writeText("blocked")
        try {
            vault.importFromTransfer(listOf(entry("new")), VaultImportMode.REPLACE)
            fail("Expected disk write failure")
        } catch (_: IOException) { }
        assertTrue(before.contentEquals(File(dir, "vault.bin").readBytes()))
        assertEquals(listOf(entry("old")), vault.snapshotForTransfer())
        blocker.deleteRecursively()
        vault.lock()
        assertEquals(listOf(entry("old")), reopen(dir).snapshotForTransfer())
    }

    @Test fun invalidDirectImportDoesNotAlterDisk() = runBlocking {
        val dir = tmp.newFolder("invalid")
        val vault = setUp(dir)
        vault.add(entry("old"))
        val before = File(dir, "vault.bin").readBytes()
        try {
            vault.importFromTransfer(listOf(entry("same"), entry("same")), VaultImportMode.REPLACE)
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) { }
        assertTrue(before.contentEquals(File(dir, "vault.bin").readBytes()))
        assertEquals(listOf(entry("old")), vault.snapshotForTransfer())
    }

    @Test fun cancelledImportNeverStartsAWrite() = runBlocking {
        val dir = tmp.newFolder("cancelled")
        val vault = setUp(dir)
        vault.add(entry("old"))
        val before = File(dir, "vault.bin").readBytes()
        val job = async(start = CoroutineStart.LAZY) { vault.importFromTransfer(listOf(entry("new")), VaultImportMode.REPLACE) }
        job.cancelAndJoin()
        assertTrue(before.contentEquals(File(dir, "vault.bin").readBytes()))
        assertEquals(listOf(entry("old")), vault.snapshotForTransfer())
    }

    @Test fun lockingWhileImportWaitsForIoPreventsCommit() = runBlocking {
        val dir = tmp.newFolder("lock-race")
        val dispatcher = GateDispatcher()
        val vault = setUp(dir, dispatcher)
        vault.add(entry("old"))
        val before = File(dir, "vault.bin").readBytes()
        dispatcher.paused = true
        supervisorScope {
            val import = async { vault.importFromTransfer(listOf(entry("new")), VaultImportMode.REPLACE) }
            yield()
            assertTrue(dispatcher.hasPending())
            vault.lock()
            dispatcher.release()
            try { import.await(); fail("Expected locked commit to fail") } catch (_: IllegalStateException) { }
        }
        assertTrue(before.contentEquals(File(dir, "vault.bin").readBytes()))
        assertEquals(listOf(entry("old")), reopen(dir).snapshotForTransfer())
    }

    @Test fun cancellingImportQueuedForIoPreservesDiskAndCache() = runBlocking {
        val dir = tmp.newFolder("cancel-race")
        val dispatcher = GateDispatcher()
        val vault = setUp(dir, dispatcher)
        vault.add(entry("old"))
        val before = File(dir, "vault.bin").readBytes()
        dispatcher.paused = true
        val import = async { vault.importFromTransfer(listOf(entry("new")), VaultImportMode.REPLACE) }
        yield()
        assertTrue(dispatcher.hasPending())
        import.cancel()
        dispatcher.release()
        import.join()
        assertTrue(before.contentEquals(File(dir, "vault.bin").readBytes()))
        assertEquals(listOf(entry("old")), vault.snapshotForTransfer())
    }

    private class GateDispatcher : CoroutineDispatcher() {
        @Volatile var paused = false
        private val pending = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (paused) pending.add(block) else Dispatchers.IO.dispatch(context, block)
        }
        fun hasPending() = pending.isNotEmpty()
        fun release() {
            paused = false
            while (true) (pending.poll() ?: break).run()
        }
    }

    @Test fun unchangedImportSkipsWriteAndKeepsOriginalSnapshot() = runBlocking {
        val dir = tmp.newFolder("unchanged")
        val vault = setUp(dir)
        vault.add(entry("old"))
        val before = File(dir, "vault.bin").readBytes()
        assertEquals(VaultImportResult(0, 1, 0), vault.importFromTransfer(listOf(entry("old")), VaultImportMode.MERGE))
        assertTrue(before.contentEquals(File(dir, "vault.bin").readBytes()))
    }
}
