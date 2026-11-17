// OWNER: worker "state". LOCKED API — keep public signatures. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.vault.model.DEFAULT_CATEGORIES
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.model.categoriesFor
import com.legenddrry.feature.vault.model.filterEntries
import com.legenddrry.feature.vault.model.findDuplicatePasswordIds
import com.legenddrry.feature.vault.model.toEntry
import com.legenddrry.feature.vault.model.validate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class VaultUiState(
    val isLoading: Boolean = true,
    /** Non-null when loading the entries flow failed; UI shows error state + retry. */
    val errorMessage: String? = null,
    /** All entries from the repository. */
    val entries: List<VaultEntry> = emptyList(),
    /** Entries after search + category filter, sorted by title. */
    val visibleEntries: List<VaultEntry> = emptyList(),
    val query: String = "",
    /** null == "All". */
    val selectedCategory: String? = null,
    /** Categories for chips and the editor dropdown. */
    val categories: List<String> = DEFAULT_CATEGORIES,
    /** Ids of entries whose password is reused by another entry. */
    val duplicatePasswordIds: Set<String> = emptySet(),
    /** One-shot snackbar text; UI calls onUserMessageShown() after showing it. */
    val userMessage: String? = null,
)

class VaultViewModel(
    private val repository: VaultRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        // STUB: no error handling / retry.
        viewModelScope.launch {
            repository.entries.collect { list ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        entries = list,
                        visibleEntries = filterEntries(list, it.query, it.selectedCategory),
                        categories = categoriesFor(list),
                        duplicatePasswordIds = findDuplicatePasswordIds(list),
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) = _uiState.update {
        it.copy(query = query, visibleEntries = filterEntries(it.entries, query, it.selectedCategory))
    }

    fun onCategorySelected(category: String?) = _uiState.update {
        it.copy(selectedCategory = category, visibleEntries = filterEntries(it.entries, it.query, category))
    }

    fun entry(id: String): VaultEntry? = _uiState.value.entries.firstOrNull { it.id == id }

    /** True if another entry (id != [excludeId]) already uses exactly [password]. */
    fun isPasswordDuplicate(password: String, excludeId: String?): Boolean =
        password.isNotEmpty() && _uiState.value.entries.any { it.id != excludeId && it.password == password }

    /** Validates; adds when draft.id == null, else updates. Calls [onSaved] only on success. */
    fun save(draft: EntryDraft, onSaved: () -> Unit = {}) {
        if (draft.validate().isNotEmpty()) return
        viewModelScope.launch {
            val entry = draft.toEntry(newId(), clock())
            if (draft.id == null) repository.add(entry) else repository.update(entry)
            _uiState.update { it.copy(userMessage = "Entry saved") }
            onSaved()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { it.copy(userMessage = "Entry deleted") }
        }
    }

    /** Re-subscribes to repository.entries after an error. */
    fun retry() {}

    fun onUserMessageShown() = _uiState.update { it.copy(userMessage = null) }
}
