// OWNER: worker "state". LOCKED API — keep signatures. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.model

import com.legenddrry.core.VaultEntry

/** Editable form state for add/edit. id == null means "new entry". */
data class EntryDraft(
    val id: String? = null,
    val title: String = "",
    val website: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val category: String = DEFAULT_CATEGORY,
)

enum class DraftField { TITLE, WEBSITE, USERNAME, PASSWORD, NOTES, CATEGORY }

/** Returns field -> human readable error. Empty map == valid. Validates the normalized draft. */
fun EntryDraft.validate(): Map<DraftField, String> {
    val n = normalized()
    val errors = mutableMapOf<DraftField, String>()
    if (n.title.isEmpty()) errors[DraftField.TITLE] = "Title is required"
    if (n.password.isEmpty()) errors[DraftField.PASSWORD] = "Password is required"
    return errors
}

/** Trims title/website/username/category; keeps password and notes as typed. Blank category -> DEFAULT_CATEGORY. */
fun EntryDraft.normalized(): EntryDraft = copy(
    title = title.trim(),
    website = website.trim(),
    username = username.trim(),
    category = category.trim().ifEmpty { DEFAULT_CATEGORY },
)

/** Builds a VaultEntry from a (normalized) draft. Uses draft.id if present, else [newId]. */
fun EntryDraft.toEntry(newId: String, updatedAtEpochMs: Long): VaultEntry {
    val n = normalized()
    return VaultEntry(
        id = n.id ?: newId,
        title = n.title,
        website = n.website,
        username = n.username,
        password = n.password,
        notes = n.notes,
        category = n.category,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

fun VaultEntry.toDraft(): EntryDraft = EntryDraft(id, title, website, username, password, notes, category)
