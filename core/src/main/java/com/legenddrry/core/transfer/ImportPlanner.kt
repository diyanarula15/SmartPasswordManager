package com.legenddrry.core.transfer

import com.legenddrry.core.VaultEntry
import java.util.UUID

internal data class VaultImportPlan(val entries: List<VaultEntry>, val result: VaultImportResult)

/** Pure planning; record content means all six user fields, independent of ID and timestamp. */
internal object ImportPlanner {
    fun validate(entries: List<VaultEntry>) {
        require(entries.size <= VaultTransferLimits.MAX_RECORDS) { "The backup contains too many records." }
        val ids = HashSet<String>()
        // Conservative JSON/envelope budget avoids allocating oversized encoded plaintext
        // even when a caller bypasses the bounded file decoder.
        var encodedBytes = 256L
        for (entry in entries) {
            encodedBytes += 160L // field names, separators and a signed Long timestamp
            require(entry.id.isNotBlank() && ids.add(entry.id)) { "The backup contains invalid record identifiers." }
            for (field in listOf(entry.id, entry.title, entry.website, entry.username, entry.password, entry.notes, entry.category)) {
                require(field.length <= VaultTransferLimits.MAX_FIELD_CHARS) { "A backup field is too large." }
                var index = 0
                while (index < field.length) {
                    val character = field[index]
                    if (character.isHighSurrogate()) {
                        require(index + 1 < field.length && field[index + 1].isLowSurrogate()) { "A backup field contains invalid text." }
                        encodedBytes += 4
                        index += 2
                    } else {
                        require(!character.isLowSurrogate()) { "A backup field contains invalid text." }
                        encodedBytes += when {
                            character < ' ' -> 6
                            character == '"' || character == '\\' -> 2
                            character.code < 0x80 -> 1
                            character.code < 0x800 -> 2
                            else -> 3
                        }
                        index++
                    }
                }
                require(encodedBytes <= VaultTransferLimits.MAX_FILE_BYTES) { "The backup is too large." }
            }
        }
    }

    fun plan(
        existing: List<VaultEntry>, incoming: List<VaultEntry>, mode: VaultImportMode,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): VaultImportPlan {
        validate(incoming)
        if (mode == VaultImportMode.REPLACE) {
            return VaultImportPlan(incoming.toList(), VaultImportResult(incoming.size, 0, 0))
        }
        val result = existing.toMutableList()
        val ids = existing.mapTo(HashSet()) { it.id }
        val reservedIds = (existing.asSequence() + incoming.asSequence()).mapTo(HashSet()) { it.id }
        val contents = existing.mapTo(HashSet()) { content(it) }
        var skipped = 0
        var conflicts = 0
        for (entry in incoming) {
            val value = content(entry)
            if (value in contents || (mode == VaultImportMode.ONLY_NEW && entry.id in ids)) {
                skipped++
                continue
            }
            val accepted = if (entry.id in ids) {
                var fresh: String
                var attempts = 0
                do {
                    check(attempts++ < 100) { "Could not allocate a record identifier." }
                    fresh = newId()
                } while (fresh.isBlank() || fresh.length > VaultTransferLimits.MAX_FIELD_CHARS || fresh in reservedIds)
                conflicts++
                reservedIds.add(fresh)
                entry.copy(id = fresh)
            } else entry
            result.add(accepted)
            ids.add(accepted.id)
            contents.add(value)
        }
        require(result.size <= VaultTransferLimits.MAX_RECORDS) { "The combined vault contains too many records." }
        validate(result)
        return VaultImportPlan(result.toList(), VaultImportResult(result.size - existing.size, skipped, conflicts))
    }

    private fun content(entry: VaultEntry): List<String> = listOf(
        entry.title, entry.website, entry.username, entry.password, entry.notes, entry.category,
    )
}
