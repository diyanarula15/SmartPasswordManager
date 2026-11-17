// Test-only fake. Plain in-memory storage: NO encryption, NO persistence.
package com.legenddrry.feature.vault.testing

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [VaultRepository] for instrumented UI tests.
 *
 * - Backed by a [MutableStateFlow]; [current] exposes the stored list for direct assertions.
 * - Records every add / update / delete call.
 * - [failEntries]: while true, each new collection of [entries] throws [entriesError]
 *   (lets tests drive the error state; flip it to false and press retry to recover).
 * - [holdEntries]: while true, each new collection of [entries] suspends without emitting
 *   (lets tests observe the loading state).
 */
class AndroidFakeVaultRepository(initial: List<VaultEntry> = emptyList()) : VaultRepository {

    private val state = MutableStateFlow(initial)

    @Volatile var failEntries: Boolean = false
    @Volatile var holdEntries: Boolean = false
    @Volatile var entriesError: Throwable = IllegalStateException("Simulated vault load failure")

    val addCalls = CopyOnWriteArrayList<VaultEntry>()
    val updateCalls = CopyOnWriteArrayList<VaultEntry>()
    val deleteCalls = CopyOnWriteArrayList<String>()
    val entriesSubscriptions = AtomicInteger(0)

    /** Snapshot of what is currently stored. */
    val current: List<VaultEntry> get() = state.value

    fun byId(id: String): VaultEntry? = state.value.firstOrNull { it.id == id }

    override val entries: Flow<List<VaultEntry>> = flow {
        entriesSubscriptions.incrementAndGet()
        if (holdEntries) awaitCancellation()
        if (failEntries) throw entriesError
        emitAll(state)
    }

    override suspend fun add(entry: VaultEntry) {
        addCalls += entry
        state.update { it + entry }
    }

    override suspend fun update(entry: VaultEntry) {
        updateCalls += entry
        state.update { list -> list.map { if (it.id == entry.id) entry else it } }
    }

    override suspend fun delete(id: String) {
        deleteCalls += id
        state.update { list -> list.filterNot { it.id == id } }
    }
}

/** Convenience builder for seeded entries. */
fun testEntry(
    id: String,
    title: String,
    username: String = "$id@example.com",
    password: String = "Pw-$id-Xy7!long",
    category: String = "Other",
    website: String = "",
    notes: String = "",
    updatedAtEpochMs: Long = 1_700_000_000_000L,
) = VaultEntry(
    id = id,
    title = title,
    website = website,
    username = username,
    password = password,
    notes = notes,
    category = category,
    updatedAtEpochMs = updatedAtEpochMs,
)
