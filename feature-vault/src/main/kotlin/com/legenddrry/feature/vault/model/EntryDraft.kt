// OWNER: worker "state". LOCKED API — keep signatures.
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

const val TITLE_MAX_LENGTH = 100
const val NOTES_MAX_LENGTH = 1000

/**
 * Returns field -> human readable error. Empty map == valid. Validates the normalized draft.
 * Iteration order of the returned map follows [DraftField] declaration order, so
 * `validate().values.first()` is the first error top-to-bottom in the editor.
 */
fun EntryDraft.validate(): Map<DraftField, String> {
    val n = normalized()
    val errors = linkedMapOf<DraftField, String>()
    when {
        n.title.isEmpty() -> errors[DraftField.TITLE] = "Title is required"
        n.title.length > TITLE_MAX_LENGTH -> errors[DraftField.TITLE] = "Title is too long (max $TITLE_MAX_LENGTH)"
    }
    if (n.website.isNotEmpty() && !isValidWebsite(n.website)) errors[DraftField.WEBSITE] = "Enter a valid website"
    if (n.password.isBlank()) errors[DraftField.PASSWORD] = "Password is required"
    if (n.notes.length > NOTES_MAX_LENGTH) errors[DraftField.NOTES] = "Notes are too long (max $NOTES_MAX_LENGTH)"
    if (n.category.isBlank()) errors[DraftField.CATEGORY] = "Category is required"
    return errors
}

private val SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

/** Host or URL: no whitespace, optional http(s):// prefix, host contains a dot (not at an edge) or is "localhost". */
internal fun isValidWebsite(website: String): Boolean {
    if (website.any { it.isWhitespace() }) return false
    val rest = website.replaceFirst(SCHEME, "")
    if (rest.isEmpty()) return false
    val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':')
    if (host.isEmpty()) return false
    if (host.equals("localhost", ignoreCase = true)) return true
    return host.contains('.') && !host.startsWith('.') && !host.endsWith('.') && !host.contains("..")
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
