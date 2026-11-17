// OWNER: worker "state". LOCKED API — keep signatures. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.model

import com.legenddrry.core.VaultEntry

const val DEFAULT_CATEGORY = "Other"
val DEFAULT_CATEGORIES: List<String> = listOf("Social", "Email", "Banking", "Shopping", "Work", "Entertainment", "Other")

/** Case-insensitive search over title, website, username, notes, category; [category] null == all. Sorted by title. */
fun filterEntries(entries: List<VaultEntry>, query: String, category: String?): List<VaultEntry> {
    val q = query.trim()
    return entries
        .filter { category == null || it.category.equals(category, ignoreCase = true) }
        .filter { q.isEmpty() || it.title.contains(q, true) || it.website.contains(q, true) || it.username.contains(q, true) }
        .sortedBy { it.title.lowercase() }
}

/** Ids of entries whose password is shared with at least one other entry (exact match, non-empty). */
fun findDuplicatePasswordIds(entries: List<VaultEntry>): Set<String> =
    entries.filter { it.password.isNotEmpty() }.groupBy { it.password }.values
        .filter { it.size > 1 }.flatten().map { it.id }.toSet()

/** DEFAULT_CATEGORIES followed by any extra categories used by entries (deduped, case-insensitive). */
fun categoriesFor(entries: List<VaultEntry>): List<String> =
    (DEFAULT_CATEGORIES + entries.map { it.category }.filter { it.isNotBlank() }).distinctBy { it.lowercase() }
