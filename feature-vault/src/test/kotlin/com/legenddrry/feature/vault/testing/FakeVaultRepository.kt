package com.legenddrry.feature.vault.testing

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/** In-memory [VaultRepository] for unit tests, with failure switches. */
class FakeVaultRepository(initial: List<VaultEntry> = emptyList()) : VaultRepository {

    val state = MutableStateFlow(initial)

    /** When non-null, collecting [entries] throws this (checked per subscription). */
    var entriesError: Throwable? = null

    /** When non-null, collection suspends until it completes (simulates slow first load). */
    var entriesGate: CompletableDeferred<Unit>? = null

    var failAdd = false
    var failUpdate = false
    var failDelete = false

    var subscriptions = 0
        private set
    val addCalls = mutableListOf<VaultEntry>()
    val updateCalls = mutableListOf<VaultEntry>()
    val deleteCalls = mutableListOf<String>()

    override val entries: Flow<List<VaultEntry>> = flow {
        subscriptions++
        entriesGate?.await()
        entriesError?.let { throw it }
        emitAll(state)
    }

    override suspend fun add(entry: VaultEntry) {
        addCalls += entry
        if (failAdd) throw IllegalStateException("add failed")
        state.update { it + entry }
    }

    override suspend fun update(entry: VaultEntry) {
        updateCalls += entry
        if (failUpdate) throw IllegalStateException("update failed")
        state.update { list -> list.map { if (it.id == entry.id) entry else it } }
    }

    override suspend fun delete(id: String) {
        deleteCalls += id
        if (failDelete) throw IllegalStateException("delete failed")
        state.update { list -> list.filterNot { it.id == id } }
    }
}

fun entry(
    id: String,
    title: String = "Title $id",
    website: String = "",
    username: String = "",
    password: String = "pw-$id",
    notes: String = "",
    category: String = "Other",
    updatedAtEpochMs: Long = 0L,
) = VaultEntry(id, title, website, username, password, notes, category, updatedAtEpochMs)
