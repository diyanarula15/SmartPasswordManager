package com.example.group4app.integration

import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileAliasesRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "aliases.json")
    private var now = 1_000L
    private fun repo(f: File = file()) = FileAliasesRepository(f) { now }

    @Test
    fun missingFileYieldsEmptyList() {
        assertTrue(repo().aliases.value.isEmpty())
        assertFalse(file().exists())
    }

    @Test
    fun createProducesDemoAddressNewestFirstAndPersists() = runTest {
        val r = repo()
        val a = r.create("  GitHub ")
        now = 2_000L
        val b = r.create("Example Shop")

        assertEquals("GitHub", a.serviceName)
        assertEquals(1_000L, a.createdAtEpochMs)
        assertTrue(a.isActive)
        assertEquals("demo.${a.id.replace("-", "")}@example.com", a.aliasAddress)
        assertTrue(Regex("^demo\\.[0-9a-f]{32}@example\\.com$").matches(a.aliasAddress))
        assertEquals(36, a.id.length) // UUID
        assertEquals(listOf(b, a), r.aliases.value)
        assertTrue(file().exists())
        assertFalse(File(tmp.root, "aliases.json.tmp").exists())

        assertEquals(listOf(b, a), repo().aliases.value)
    }

    @Test
    fun duplicateServiceNamesGetDistinctRecords() = runTest {
        val r = repo()
        val a = r.create("Mail")
        val b = r.create("Mail")
        assertTrue(a.id != b.id && a.aliasAddress != b.aliasAddress)
    }

    @Test
    fun searchByServiceViaListFiltering() = runTest {
        val r = repo()
        r.create("GitHub"); r.create("GitLab"); r.create("Bank")
        val hits = r.aliases.value.filter { it.serviceName.contains("git", ignoreCase = true) }
        assertEquals(setOf("GitHub", "GitLab"), hits.map { it.serviceName }.toSet())
    }

    @Test
    fun invalidServiceNamesRejected() = runTest {
        val r = repo()
        for (bad in listOf("", "   ", "a".repeat(121), "line\nbreak")) {
            try {
                r.create(bad)
                fail("Expected IllegalArgumentException for '$bad'")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(r.aliases.value.isEmpty())
        assertFalse(file().exists())
        r.create("a".repeat(120))
    }

    @Test
    fun setActiveAndDeletePersist() = runTest {
        val r = repo()
        val a = r.create("One")
        val b = r.create("Two")
        r.setActive(a.id, false)
        assertFalse(r.aliases.value.first { it.id == a.id }.isActive)
        assertFalse(repo().aliases.value.first { it.id == a.id }.isActive)
        r.setActive(a.id, true)
        assertTrue(repo().aliases.value.first { it.id == a.id }.isActive)

        r.delete(b.id)
        assertEquals(listOf(a.id), r.aliases.value.map { it.id })
        assertEquals(listOf(a.id), repo().aliases.value.map { it.id })
    }

    @Test
    fun unknownIdRejectedLikeInMemoryRepository() = runTest {
        val r = repo()
        r.create("One")
        for (op in listOf<suspend () -> Unit>({ r.setActive("nope", false) }, { r.delete("nope") })) {
            try {
                op()
                fail("Expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(1, r.aliases.value.size)
    }

    @Test
    fun corruptFileYieldsEmptyListAndIsPreserved() = runTest {
        file().writeText("{not json")
        val r = repo()
        assertTrue(r.aliases.value.isEmpty())
        val corrupt = File(tmp.root, "aliases.json.corrupt")
        assertTrue(corrupt.exists())
        assertEquals("{not json", corrupt.readText())

        r.create("Fresh")
        assertEquals(1, repo().aliases.value.size)
        assertEquals("{not json", corrupt.readText())
    }

    @Test
    fun structurallyInvalidRecordsTreatedAsCorrupt() {
        file().writeText("""{"version":1,"aliases":[{"id":"x","serviceName":"S"}]}""")
        assertTrue(repo().aliases.value.isEmpty())
        assertTrue(File(tmp.root, "aliases.json.corrupt").exists())

        file().writeText("[]")
        assertTrue(repo().aliases.value.isEmpty())
        assertTrue(File(tmp.root, "aliases.json.corrupt.1000").exists())
    }

    @Test
    fun storedFormatIsVersionedJson() = runTest {
        val a = repo().create("Svc")
        val root = JSONObject(file().readText())
        assertEquals(1, root.getInt("version"))
        val obj = root.getJSONArray("aliases").getJSONObject(0)
        assertEquals(a.id, obj.getString("id"))
        assertEquals("Svc", obj.getString("serviceName"))
        assertEquals(a.aliasAddress, obj.getString("aliasAddress"))
        assertEquals(1_000L, obj.getLong("createdAtEpochMs"))
        assertTrue(obj.getBoolean("isActive"))
    }

    @Test
    fun concurrentCreatesAreSerialized() = runTest {
        val r = repo()
        (1..20).map { i -> async { r.create("Svc $i") } }.awaitAll()
        assertEquals(20, r.aliases.value.size)
        assertEquals(20, repo().aliases.value.size)
    }

    @Test
    fun createsMissingParentDirectory() = runTest {
        val nested = File(tmp.root, "a/b/aliases.json")
        repo(nested).create("Svc")
        assertEquals(1, repo(nested).aliases.value.size)
    }
}
