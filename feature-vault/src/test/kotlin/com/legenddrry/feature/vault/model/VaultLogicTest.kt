package com.legenddrry.feature.vault.model

import com.legenddrry.feature.vault.testing.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultLogicTest {

    private val entries = listOf(
        entry("3", title = "github", website = "github.com", username = "octo", notes = "work account", category = "Work"),
        entry("1", title = "Gmail", website = "mail.google.com", username = "alice@gmail.com", category = "Email"),
        entry("2", title = "Bank", website = "bank.example", username = "alice", notes = "PIN in safe", category = "Banking"),
        entry("4", title = "Arcade", category = "Games"),
        entry("0", title = "gmail", category = "email"),
    )

    private fun ids(query: String, category: String? = null) = filterEntries(entries, query, category).map { it.id }

    @Test fun emptyQueryReturnsAllSortedByTitleThenId() =
        assertEquals(listOf("4", "2", "3", "0", "1"), ids("   "))

    @Test fun searchByTitle() = assertEquals(listOf("2"), ids("BANK"))
    @Test fun searchByWebsite() = assertEquals(listOf("1"), ids("google"))
    @Test fun searchByUsername() = assertEquals(listOf("3"), ids("Octo"))
    @Test fun searchByNotes() = assertEquals(listOf("2"), ids("pin in"))
    @Test fun searchByCategory() = assertEquals(listOf("4"), ids("games"))
    @Test fun queryIsTrimmed() = assertEquals(listOf("3"), ids("  octo  "))
    @Test fun noMatches() = assertTrue(ids("zzz").isEmpty())

    @Test fun categoryFilterIsCaseInsensitive() = assertEquals(listOf("0", "1"), ids("", "EMAIL"))
    @Test fun categoryCombinedWithSearch() {
        assertEquals(listOf("1"), ids("alice", "Email"))
        assertEquals(listOf("2"), ids("alice", "Banking"))
        assertTrue(ids("octo", "Email").isEmpty())
    }
    @Test fun unknownCategoryYieldsNothing() = assertTrue(ids("", "Nope").isEmpty())

    @Test fun duplicates() {
        val list = listOf(
            entry("a", password = "same"), entry("b", password = "same"), entry("c", password = "unique"),
            entry("d", password = ""), entry("e", password = ""), entry("f", password = "Same"),
        )
        assertEquals(setOf("a", "b"), findDuplicatePasswordIds(list))
        assertTrue(findDuplicatePasswordIds(emptyList()).isEmpty())
    }

    @Test fun categoriesDefaultsThenSortedExtrasDeduped() {
        val list = listOf(
            entry("1", category = "zeta"), entry("2", category = "email"), entry("3", category = "Alpha"),
            entry("4", category = "ZETA"), entry("5", category = "  "), entry("6", category = " beta "),
        )
        assertEquals(DEFAULT_CATEGORIES + listOf("Alpha", "beta", "zeta"), categoriesFor(list))
        assertEquals(DEFAULT_CATEGORIES, categoriesFor(emptyList()))
    }
}
