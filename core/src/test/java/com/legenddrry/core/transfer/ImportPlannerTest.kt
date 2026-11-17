package com.legenddrry.core.transfer

import com.legenddrry.core.VaultEntry
import org.junit.Assert.*
import org.junit.Test

class ImportPlannerTest {
    private fun entry(id: String, title: String = "Title") = VaultEntry(id, title, "https://example.com", "user", "secret", "notes", "Login", 123L)

    @Test fun mergePreservesConflictWithFreshIdAndEveryOtherField() {
        val existing = entry("a")
        val incoming = entry("a", "Other").copy(updatedAtEpochMs = 456L)
        val plan = ImportPlanner.plan(listOf(existing), listOf(incoming), VaultImportMode.MERGE) { "fresh" }
        assertEquals(listOf(existing, incoming.copy(id = "fresh")), plan.entries)
        assertEquals(VaultImportResult(1, 0, 1), plan.result)
    }

    @Test fun onlyNewSkipsStableIdEvenWhenChanged() {
        val plan = ImportPlanner.plan(listOf(entry("a")), listOf(entry("a", "Changed"), entry("b", "New")), VaultImportMode.ONLY_NEW)
        assertEquals(listOf(entry("a"), entry("b", "New")), plan.entries)
        assertEquals(VaultImportResult(1, 1, 0), plan.result)
    }

    @Test fun identicalContentIsSkippedAcrossDifferentIdsAndTimestamps() {
        for (mode in listOf(VaultImportMode.MERGE, VaultImportMode.ONLY_NEW)) {
            val plan = ImportPlanner.plan(listOf(entry("a")), listOf(entry("b").copy(updatedAtEpochMs = 999)), mode)
            assertEquals(listOf(entry("a")), plan.entries)
            assertEquals(VaultImportResult(0, 1, 0), plan.result)
        }
    }

    @Test fun duplicatesWithinIncomingAreSkippedByContent() {
        val plan = ImportPlanner.plan(emptyList(), listOf(entry("a"), entry("b")), VaultImportMode.MERGE)
        assertEquals(listOf(entry("a")), plan.entries)
        assertEquals(VaultImportResult(1, 1, 0), plan.result)
    }

    @Test fun replacementPreservesAllRecordsEvenWithMatchingContent() {
        val incoming = listOf(entry("a"), entry("b"))
        val plan = ImportPlanner.plan(listOf(entry("old")), incoming, VaultImportMode.REPLACE)
        assertEquals(incoming, plan.entries)
        assertEquals(VaultImportResult(2, 0, 0), plan.result)
    }

    @Test fun emptyReplacementClearsAllEntries() {
        assertEquals(emptyList<VaultEntry>(), ImportPlanner.plan(listOf(entry("old")), emptyList(), VaultImportMode.REPLACE).entries)
    }

    @Test fun generatedConflictIdsCannotStealLaterIncomingIds() {
        val ids = ArrayDeque(listOf("later", "fresh"))
        val incoming = listOf(entry("a", "Changed"), entry("later", "Later"))
        val plan = ImportPlanner.plan(listOf(entry("a")), incoming, VaultImportMode.MERGE) { ids.removeFirst() }
        assertEquals(listOf("a", "fresh", "later"), plan.entries.map { it.id })
    }

    @Test fun duplicateAndBlankIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ImportPlanner.validate(listOf(entry("a"), entry("a"))) }
        assertThrows(IllegalArgumentException::class.java) { ImportPlanner.validate(listOf(entry(" "))) }
    }

    @Test fun oversizeFieldsAndRecordCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ImportPlanner.validate(listOf(entry("a").copy(notes = "x".repeat(VaultTransferLimits.MAX_FIELD_CHARS + 1)))) }
        assertThrows(IllegalArgumentException::class.java) { ImportPlanner.validate(List(VaultTransferLimits.MAX_RECORDS + 1) { entry("$it") }) }
    }

    @Test fun aggregateEncodedSizeIsBoundedWithoutAllocatingJson() {
        val largeField = "x".repeat(VaultTransferLimits.MAX_FIELD_CHARS)
        val entries = List(130) { entry("$it").copy(notes = largeField) }
        assertThrows(IllegalArgumentException::class.java) { ImportPlanner.validate(entries) }
    }

    @Test fun combinedVaultLimitIsCheckedBeforeCommit() {
        val existing = List(VaultTransferLimits.MAX_RECORDS) { entry("$it", "$it") }
        assertThrows(IllegalArgumentException::class.java) {
            ImportPlanner.plan(existing, listOf(entry("extra", "Extra")), VaultImportMode.MERGE)
        }
    }

    @Test fun malformedUnicodeIsRejectedAndUnicodeFieldsArePreserved() {
        assertThrows(IllegalArgumentException::class.java) { ImportPlanner.validate(listOf(entry("a").copy(notes = "\uD800"))) }
        val unicode = entry("unicode").copy(title = "नमस्ते 🍃", notes = "中文\nline\u0000")
        assertEquals(listOf(unicode), ImportPlanner.plan(emptyList(), listOf(unicode), VaultImportMode.REPLACE).entries)
    }
}
