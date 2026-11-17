package com.legenddrry.feature.vault.state

import com.legenddrry.feature.vault.generator.GeneratorOptions
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.testing.FakeVaultRepository
import com.legenddrry.feature.vault.testing.entry
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GeneratorViewModelTest {

    @get:Rule val mainRule = MainDispatcherRule()

    private val repo = FakeVaultRepository(listOf(entry("e1", password = "taken", category = "Games")))
    private val calls = mutableListOf<GeneratorOptions>()
    private val fakeGenerate: (GeneratorOptions) -> String = { o ->
        calls += o
        "gen-${calls.size}-len${o.length}"
    }

    private fun vm(generate: (GeneratorOptions) -> String = fakeGenerate) =
        GeneratorViewModel(repo, generate = generate, clock = { 555L }, newId = { "fresh" })

    @Test fun generatesOnInitAndLoadsEntries() = runTest {
        val vm = vm()
        assertEquals("gen-1-len16", vm.uiState.value.password)
        assertEquals(GeneratorOptions(), calls.single())
        assertEquals(1, vm.uiState.value.entries.size)
        assertTrue("Games" in vm.uiState.value.categories)
    }

    @Test fun regenerateUsesInjectedFunction() = runTest {
        val vm = vm()
        vm.regenerate()
        assertEquals("gen-2-len16", vm.uiState.value.password)
        assertEquals(2, calls.size)
    }

    @Test fun lengthIsCoerced() = runTest {
        val vm = vm()
        vm.updateOptions(GeneratorOptions(length = 5))
        assertEquals(12, vm.uiState.value.options.length)
        assertEquals(12, calls.last().length)
        vm.updateOptions(GeneratorOptions(length = 99))
        assertEquals(32, vm.uiState.value.options.length)
        vm.updateOptions(GeneratorOptions(length = 20, symbols = false))
        assertEquals(GeneratorOptions(length = 20, symbols = false), vm.uiState.value.options)
        assertEquals("gen-4-len20", vm.uiState.value.password)
    }

    @Test fun disablingLastCategoryIsRejected() = runTest {
        val vm = vm()
        val onlyDigits = GeneratorOptions(uppercase = false, lowercase = false, digits = true, symbols = false)
        vm.updateOptions(onlyDigits)
        val before = vm.uiState.value
        vm.updateOptions(onlyDigits.copy(digits = false))
        val after = vm.uiState.value
        assertEquals(onlyDigits, after.options)
        assertEquals(before.password, after.password)
        assertEquals("Select at least one character type", after.userMessage)
        assertEquals(2, calls.size)
    }

    @Test fun generatorExceptionIsCaught() = runTest {
        val vm = vm { throw IllegalArgumentException("boom") }
        assertEquals("", vm.uiState.value.password)
        vm.regenerate()
        assertEquals("", vm.uiState.value.password)
    }

    @Test fun entriesFailureDoesNotCrash() = runTest {
        repo.entriesError = RuntimeException("io")
        val vm = vm()
        assertTrue(vm.uiState.value.entries.isEmpty())
        assertFalse(vm.isPasswordDuplicate("taken"))
    }

    @Test fun duplicateCheck() = runTest {
        val vm = vm()
        assertTrue(vm.isPasswordDuplicate("taken"))
        assertFalse(vm.isPasswordDuplicate(vm.uiState.value.password))
        assertFalse(vm.isPasswordDuplicate(""))
    }

    @Test fun saveAsEntryCreatesNewEntryWithGeneratedPassword() = runTest {
        val vm = vm()
        val pw = vm.uiState.value.password
        var saved = false
        vm.saveAsEntry(EntryDraft(id = "e1", title = " Site ", password = pw, notes = "n"), onSaved = { saved = true })
        val added = repo.addCalls.single()
        assertEquals("fresh", added.id)
        assertEquals("Site", added.title)
        assertEquals(pw, added.password)
        assertEquals(555L, added.updatedAtEpochMs)
        assertTrue(repo.updateCalls.isEmpty())
        assertTrue(saved)
        assertEquals(VaultTestTags.MSG_ENTRY_SAVED, vm.uiState.value.userMessage)
        assertTrue(vm.isPasswordDuplicate(pw))
        vm.onUserMessageShown()
        assertNull(vm.uiState.value.userMessage)
    }

    @Test fun saveAsEntryInvalidDoesNotHitRepo() = runTest {
        val vm = vm()
        var saved = false
        vm.saveAsEntry(EntryDraft(title = "", password = "x"), onSaved = { saved = true })
        assertEquals("Title is required", vm.uiState.value.userMessage)
        assertTrue(repo.addCalls.isEmpty())
        assertFalse(saved)
    }

    @Test fun saveAsEntryFailure() = runTest {
        repo.failAdd = true
        val vm = vm()
        var saved = false
        vm.saveAsEntry(EntryDraft(title = "T", password = "p", notes = "n"), onSaved = { saved = true })
        assertEquals("Couldn't save entry", vm.uiState.value.userMessage)
        assertFalse(saved)
    }

    @Test fun repeatedSaveWhileWriteIsPendingCreatesOnlyOneEntry() = runTest {
        val gate = CompletableDeferred<Unit>()
        repo.addGate = gate
        val vm = vm()
        val draft = EntryDraft(title = "Pending", password = "secret", notes = "n")
        var saved = 0
        vm.saveAsEntry(draft) { saved++ }
        assertTrue(vm.uiState.value.isSaving)
        vm.saveAsEntry(draft) { saved++ }
        assertEquals(1, repo.addCalls.size)
        gate.complete(Unit)
        assertEquals(1, saved)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(1, repo.state.value.count { it.title == "Pending" })
    }

    @Test fun failedSaveAndCancellationAllowRetry() = runTest {
        val vm = vm()
        val draft = EntryDraft(title = "Retry", password = "secret", notes = "n")
        repo.failAdd = true
        vm.saveAsEntry(draft)
        assertFalse(vm.uiState.value.isSaving)
        repo.failAdd = false
        repo.addError = CancellationException("cancelled write")
        vm.saveAsEntry(draft)
        assertFalse(vm.uiState.value.isSaving)
        repo.addError = null
        var saved = false
        vm.saveAsEntry(draft) { saved = true }
        assertTrue(saved)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(1, repo.state.value.count { it.title == "Retry" })
    }

}
