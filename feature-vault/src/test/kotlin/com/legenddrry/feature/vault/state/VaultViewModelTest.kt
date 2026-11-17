package com.legenddrry.feature.vault.state

import com.legenddrry.feature.vault.model.DEFAULT_CATEGORIES
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.testing.FakeVaultRepository
import com.legenddrry.feature.vault.testing.entry
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VaultViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakeVaultRepository(
        listOf(
            entry("b", title = "Bank", username = "alice", password = "shared", category = "Banking"),
            entry("a", title = "Amazon", website = "amazon.com", password = "shared", category = "Shopping"),
            entry("c", title = "Club", notes = "golf", password = "solo", category = "Hobby"),
        ),
    )
    private var ids = 0
    private fun vm() = VaultViewModel(repo, clock = { 1234L }, newId = { "new-${++ids}" })

    @Test fun loadingThenLoaded() = runTest {
        val gate = CompletableDeferred<Unit>()
        repo.entriesGate = gate
        val vm = vm()
        assertTrue(vm.uiState.value.isLoading)
        gate.complete(Unit)
        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertNull(s.errorMessage)
        assertEquals(listOf("a", "b", "c"), s.visibleEntries.map { it.id })
        assertEquals(DEFAULT_CATEGORIES + "Hobby", s.categories)
        assertEquals(setOf("a", "b"), s.duplicatePasswordIds)
    }

    @Test fun errorStateAndRetryRecovers() = runTest {
        repo.entriesError = IllegalStateException("db locked")
        val vm = vm()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("Couldn't load your vault", vm.uiState.value.errorMessage)

        repo.entriesError = null
        val gate = CompletableDeferred<Unit>()
        repo.entriesGate = gate
        vm.retry()
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)
        gate.complete(Unit)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(3, vm.uiState.value.entries.size)
        assertEquals(2, repo.subscriptions)
    }

    @Test fun retryCancelsPreviousCollection() = runTest {
        val vm = vm()
        vm.retry()
        assertEquals(2, repo.subscriptions)
        repo.state.value = listOf(entry("z"))
        assertEquals(listOf("z"), vm.uiState.value.entries.map { it.id })
    }

    @Test fun recomputesOnEmission() = runTest {
        val vm = vm()
        vm.onQueryChange("o")
        repo.state.value = repo.state.value + entry("d", title = "Dojo", password = "solo", category = "Sport")
        val s = vm.uiState.value
        assertEquals(listOf("a", "c", "d"), s.visibleEntries.map { it.id })
        assertEquals(setOf("a", "b", "c", "d"), s.duplicatePasswordIds)
        assertTrue("Sport" in s.categories)
    }

    @Test fun searchAndCategoryFilter() = runTest {
        val vm = vm()
        vm.onQueryChange("ALICE")
        assertEquals(listOf("b"), vm.uiState.value.visibleEntries.map { it.id })
        vm.onQueryChange("golf")
        assertEquals(listOf("c"), vm.uiState.value.visibleEntries.map { it.id })
        vm.onQueryChange("amazon.com")
        assertEquals(listOf("a"), vm.uiState.value.visibleEntries.map { it.id })
        vm.onQueryChange("")
        vm.onCategorySelected("banking")
        assertEquals(listOf("b"), vm.uiState.value.visibleEntries.map { it.id })
        vm.onQueryChange("amazon")
        assertTrue(vm.uiState.value.visibleEntries.isEmpty())
        vm.onCategorySelected(null)
        assertEquals(listOf("a"), vm.uiState.value.visibleEntries.map { it.id })
    }

    @Test fun selectedCategoryKeptWhenItDisappears() = runTest {
        val vm = vm()
        vm.onCategorySelected("Hobby")
        assertEquals(listOf("c"), vm.uiState.value.visibleEntries.map { it.id })
        repo.state.value = repo.state.value.filterNot { it.id == "c" }
        assertEquals("Hobby", vm.uiState.value.selectedCategory)
        assertTrue(vm.uiState.value.visibleEntries.isEmpty())
    }

    @Test fun addNewEntry() = runTest {
        val vm = vm()
        var saved = 0
        vm.save(EntryDraft(title = " New ", password = "pw", category = ""), onSaved = { saved++ })
        val added = repo.addCalls.single()
        assertEquals("new-1", added.id)
        assertEquals("New", added.title)
        assertEquals("Other", added.category)
        assertEquals(1234L, added.updatedAtEpochMs)
        assertTrue(repo.updateCalls.isEmpty())
        assertEquals(1, saved)
        assertEquals(VaultTestTags.MSG_ENTRY_SAVED, vm.uiState.value.userMessage)
        assertTrue(vm.uiState.value.entries.any { it.id == "new-1" })
        vm.onUserMessageShown()
        assertNull(vm.uiState.value.userMessage)
    }

    @Test fun editExistingEntry() = runTest {
        val vm = vm()
        var saved = false
        vm.save(EntryDraft(id = "c", title = "Club 2", password = "x"), onSaved = { saved = true })
        val updated = repo.updateCalls.single()
        assertEquals("c", updated.id)
        assertEquals("Club 2", updated.title)
        assertEquals(1234L, updated.updatedAtEpochMs)
        assertTrue(repo.addCalls.isEmpty())
        assertEquals(0, ids)
        assertTrue(saved)
        assertEquals("Club 2", vm.entry("c")?.title)
    }

    @Test fun invalidDraftDoesNotHitRepository() = runTest {
        val vm = vm()
        var saved = false
        vm.save(EntryDraft(title = "  ", password = ""), onSaved = { saved = true })
        assertEquals("Title is required", vm.uiState.value.userMessage)
        vm.save(EntryDraft(id = "c", title = "x", password = "p", website = "bad site"), onSaved = { saved = true })
        assertEquals("Enter a valid website", vm.uiState.value.userMessage)
        assertTrue(repo.addCalls.isEmpty() && repo.updateCalls.isEmpty())
        assertFalse(saved)
    }

    @Test fun saveFailureShowsMessage() = runTest {
        val vm = vm()
        repo.failAdd = true
        repo.failUpdate = true
        var saved = false
        vm.save(EntryDraft(title = "T", password = "p"), onSaved = { saved = true })
        assertEquals("Couldn't save entry", vm.uiState.value.userMessage)
        vm.onUserMessageShown()
        vm.save(EntryDraft(id = "c", title = "T", password = "p"), onSaved = { saved = true })
        assertEquals("Couldn't save entry", vm.uiState.value.userMessage)
        assertFalse(saved)
    }

    @Test fun deleteSuccessAndFailure() = runTest {
        val vm = vm()
        vm.delete("a")
        assertEquals(listOf("a"), repo.deleteCalls)
        assertEquals(VaultTestTags.MSG_ENTRY_DELETED, vm.uiState.value.userMessage)
        assertNull(vm.entry("a"))
        assertTrue(vm.uiState.value.duplicatePasswordIds.isEmpty())

        repo.failDelete = true
        vm.delete("b")
        assertEquals("Couldn't delete entry", vm.uiState.value.userMessage)
        assertTrue(vm.entry("b") != null)
    }

    @Test fun isPasswordDuplicate() = runTest {
        val vm = vm()
        assertTrue(vm.isPasswordDuplicate("solo", excludeId = null))
        assertFalse(vm.isPasswordDuplicate("solo", excludeId = "c"))
        assertTrue(vm.isPasswordDuplicate("shared", excludeId = "a"))
        assertFalse(vm.isPasswordDuplicate("", excludeId = null))
        assertFalse(vm.isPasswordDuplicate("nope", excludeId = null))
    }

    @Test fun repeatedSaveWhileWriteIsPendingCreatesOnlyOneEntry() = runTest {
        val gate = CompletableDeferred<Unit>()
        repo.addGate = gate
        val vm = vm()
        val draft = EntryDraft(title = "Pending", password = "secret")
        var saved = 0
        vm.save(draft) { saved++ }
        assertTrue(vm.uiState.value.isSaving)
        vm.save(draft) { saved++ }
        assertEquals(1, repo.addCalls.size)
        gate.complete(Unit)
        assertEquals(1, saved)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(1, repo.state.value.count { it.title == "Pending" })
    }

    @Test fun failedSaveAndCancellationAllowRetry() = runTest {
        val vm = vm()
        val draft = EntryDraft(title = "Retry", password = "secret")
        repo.failAdd = true
        vm.save(draft)
        assertFalse(vm.uiState.value.isSaving)
        repo.failAdd = false
        repo.addError = CancellationException("cancelled write")
        vm.save(draft)
        assertFalse(vm.uiState.value.isSaving)
        repo.addError = null
        var saved = false
        vm.save(draft) { saved = true }
        assertTrue(saved)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(1, repo.state.value.count { it.title == "Retry" })
    }

}
