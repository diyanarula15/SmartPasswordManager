package com.legenddrry.feature.tools.aliases

import com.legenddrry.feature.tools.contracts.AliasesRepository
import com.legenddrry.feature.tools.contracts.EmailAlias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class InMemoryAliasesRepositoryTest {
    @Test fun createPublishesTrimmedActiveLocalRecordAtClockTime() = runBlocking {
        val repository = InMemoryAliasesRepository(clock = { 123456L })
        assertTrue(repository.aliases.value.isEmpty())
        val record = repository.create("  Student portal  ")
        assertEquals("Student portal", record.serviceName)
        assertEquals(123456L, record.createdAtEpochMs)
        assertTrue(record.isActive)
        assertTrue(record.aliasAddress.startsWith("demo."))
        assertTrue(record.aliasAddress.endsWith("@example.com"))
        assertEquals(listOf(record), repository.aliases.value)
    }

    @Test fun createNeverDuplicatesIdsOrAddressesEvenForSameService() = runBlocking {
        val repository = InMemoryAliasesRepository()
        val records = (1..100).map { repository.create("Same service") }
        assertEquals(100, records.map { it.id }.distinct().size)
        assertEquals(100, records.map { it.aliasAddress }.distinct().size)
        assertEquals(100, repository.aliases.value.size)
    }

    @Test fun concurrentCreatesDoNotLoseRecords() = runBlocking {
        val repository = InMemoryAliasesRepository()
        (1..100).map { number ->
            async(Dispatchers.Default) { repository.create("Service $number") }
        }.awaitAll()
        assertEquals(100, repository.aliases.value.size)
        assertEquals(100, repository.aliases.value.map { it.id }.distinct().size)
    }

    @Test fun toggleUpdatesOnlySelectedRecordAndPreservesOldSnapshot() = runBlocking {
        val repository = InMemoryAliasesRepository()
        val first = repository.create("Campus")
        val second = repository.create("Library")
        val snapshot = repository.aliases.value
        repository.setActive(first.id, false)
        assertFalse(repository.aliases.value.first { it.id == first.id }.isActive)
        assertTrue(repository.aliases.value.first { it.id == second.id }.isActive)
        assertTrue(snapshot.first { it.id == first.id }.isActive)
        repository.setActive(first.id, true)
        assertTrue(repository.aliases.value.first { it.id == first.id }.isActive)
    }

    @Test fun deleteRemovesSelectedAliasAndSupportsEmptyList() = runBlocking {
        val repository = InMemoryAliasesRepository()
        val alias = repository.create("Campus")
        repository.delete(alias.id)
        assertTrue(repository.aliases.value.isEmpty())
    }

    @Test fun missingRecordsRejectMutations() = runBlocking {
        val repository = InMemoryAliasesRepository()
        expectIllegalArgument { repository.setActive("missing", false) }
        expectIllegalArgument { repository.delete("missing") }
        assertTrue(repository.aliases.value.isEmpty())
    }

    @Test fun invalidNamesDoNotPublishRecords() = runBlocking {
        val repository = InMemoryAliasesRepository()
        listOf("", "   ", "x".repeat(121), "Campus\nPortal", "Campus\tPortal").forEach { invalid ->
            expectIllegalArgument { repository.create(invalid) }
        }
        assertTrue(repository.aliases.value.isEmpty())
        assertNull(aliasServiceError("x".repeat(120)))
        repository.create("x".repeat(120))
        assertEquals(1, repository.aliases.value.size)
    }

    @Test fun initialRecordsAreCopiedAndDuplicateIdsAreRejected() {
        val record = EmailAlias("id", "Campus", "demo@example.com", 1L, true)
        val seed = mutableListOf(record)
        val repository = InMemoryAliasesRepository(seed)
        seed.clear()
        assertEquals(listOf(record), repository.aliases.value)
        try {
            InMemoryAliasesRepository(listOf(record, record))
            fail("Duplicate seed IDs must fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun searchMatchesTrimmedCaseInsensitiveServiceOrAddressAndPreservesOrder() {
        val library = EmailAlias("1", "Campus Library", "demo.books@example.com", 1L, true)
        val portal = EmailAlias("2", "Student Portal", "demo.portal@example.com", 2L, false)
        val aliases = listOf(library, portal)
        assertEquals(listOf(library), filterAliases(aliases, "  LIBRARY  "))
        assertEquals(listOf(portal), filterAliases(aliases, "DEMO.PORTAL"))
        assertEquals(aliases, filterAliases(aliases, "example.com"))
        assertEquals(aliases, filterAliases(aliases, "  "))
        assertTrue(filterAliases(aliases, "unknown").isEmpty())
        assertTrue(filterAliases(emptyList(), "campus").isEmpty())
    }

    @Test fun searchWorksAgainstInjectedRepositoryData() {
        // This fake exercises filtering independently from the production memory adapter.
        val record = EmailAlias("mock", "Mock university", "mock@example.com", 0L, false)
        val repository = object : AliasesRepository {
            override val aliases = MutableStateFlow(listOf(record))
            override suspend fun create(serviceName: String): EmailAlias = error("Unused")
            override suspend fun setActive(id: String, active: Boolean) = error("Unused")
            override suspend fun delete(id: String) = error("Unused")
        }
        assertEquals(listOf(record), filterAliases(repository.aliases.value, "university"))
    }

    private suspend fun expectIllegalArgument(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) { }
    }
}
