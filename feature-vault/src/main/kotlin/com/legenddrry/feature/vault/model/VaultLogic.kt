package com.legenddrry.feature.vault.model

import com.legenddrry.core.VaultEntry

const val DEFAULT_CATEGORY = "Other"
val DEFAULT_CATEGORIES: List<String> = listOf("Social", "Email", "Banking", "Shopping", "Work", "Entertainment", "Other")

private val TITLE_ORDER: Comparator<VaultEntry> =
    compareBy<VaultEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.title }.thenBy { it.id }

/** Case-insensitive search over title, website, username, notes, category; [category] null == all. Sorted by title. */
fun filterEntries(entries: List<VaultEntry>, query: String, category: String?): List<VaultEntry> {
    val q = query.trim()
    val cat = category?.trim()
    return entries
        .filter { cat == null || it.category.trim().equals(cat, ignoreCase = true) }
        .filter { q.isEmpty() || it.matches(q) }
        .sortedWith(TITLE_ORDER)
}

private fun VaultEntry.matches(q: String): Boolean =
    title.contains(q, ignoreCase = true) ||
        website.contains(q, ignoreCase = true) ||
        username.contains(q, ignoreCase = true) ||
        notes.contains(q, ignoreCase = true) ||
        category.contains(q, ignoreCase = true)

/** Ids of entries whose password is shared with at least one other entry (exact match, non-empty). */
fun findDuplicatePasswordIds(entries: List<VaultEntry>): Set<String> =
    entries
        .filter { it.password.isNotEmpty() }
        .groupBy { it.password }
        .values
        .filter { group -> group.size > 1 }
        .flatMapTo(HashSet()) { group -> group.map { it.id } }

/** DEFAULT_CATEGORIES followed by any extra categories used by entries (deduped, case-insensitive). */
fun categoriesFor(entries: List<VaultEntry>): List<String> {
    val seen = DEFAULT_CATEGORIES.mapTo(HashSet()) { it.lowercase() }
    val extras = entries
        .map { it.category.trim() }
        .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
    return DEFAULT_CATEGORIES + extras
}
