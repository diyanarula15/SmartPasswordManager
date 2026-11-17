package com.example.group4app.integration

import java.io.File
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reproduces cancellation after rename but before withContext resumes the caller. */
class AliasCancellationCommitTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun cancelledCreatePublishesDurableStateBeforeTheNextWrite() = runBlocking {
        val file = File(temp.root, "aliases.json")
        val io = QueuedDispatcher()
        val repository = FileAliasesRepository(file, ioDispatcher = io)
        val create = launch(start = CoroutineStart.UNDISPATCHED) { repository.create("First service") }
        io.runNext() // Atomic file write has completed; caller's resumption is still queued.
        assertTrue(file.exists())
        create.cancelAndJoin()
        assertEquals(listOf("First service"), repository.aliases.value.map { it.serviceName })
        assertEquals(repository.aliases.value, FileAliasesRepository(file).aliases.value)

        val second = async(start = CoroutineStart.UNDISPATCHED) { repository.create("Second service") }
        io.runNext()
        second.await()
        assertEquals(setOf("First service", "Second service"), FileAliasesRepository(file).aliases.value.map { it.serviceName }.toSet())
    }

    @Test fun cancelledToggleAndDeleteKeepMemoryAndDiskConsistent() = runBlocking {
        val file = File(temp.root, "aliases.json")
        val io = QueuedDispatcher()
        val repository = FileAliasesRepository(file, ioDispatcher = io)
        val create = async(start = CoroutineStart.UNDISPATCHED) { repository.create("Service") }
        io.runNext()
        val alias = create.await()

        val toggle = launch(start = CoroutineStart.UNDISPATCHED) { repository.setActive(alias.id, false) }
        io.runNext()
        toggle.cancelAndJoin()
        assertEquals(false, repository.aliases.value.single().isActive)
        assertEquals(repository.aliases.value, FileAliasesRepository(file).aliases.value)

        val delete = launch(start = CoroutineStart.UNDISPATCHED) { repository.delete(alias.id) }
        io.runNext()
        delete.cancelAndJoin()
        assertTrue(repository.aliases.value.isEmpty())
        assertTrue(FileAliasesRepository(file).aliases.value.isEmpty())
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun runNext() { check(queue.isNotEmpty()) { "Expected an IO dispatch" }; queue.removeFirst().run() }
    }
}
