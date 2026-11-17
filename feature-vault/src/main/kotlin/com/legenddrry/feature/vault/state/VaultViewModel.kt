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
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    private var collectJob: Job? = null

    init {
        subscribe()
    }

    private fun subscribe() {
        collectJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        collectJob = viewModelScope.launch {
            try {
                repository.entries.collect { list ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            entries = list,
                            visibleEntries = filterEntries(list, it.query, it.selectedCategory),
                            categories = categoriesFor(list),
                            duplicatePasswordIds = findDuplicatePasswordIds(list),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = MSG_LOAD_FAILED) }
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
        val errors = draft.validate()
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(userMessage = errors.values.first()) }
            return
        }
        viewModelScope.launch {
            val ok = try {
                if (draft.id == null) {
                    repository.add(draft.toEntry(newId(), clock()))
                } else {
                    repository.update(draft.toEntry(draft.id, clock()))
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (ok) {
                _uiState.update { it.copy(userMessage = VaultTestTags.MSG_ENTRY_SAVED) }
                onSaved()
            } else {
                _uiState.update { it.copy(userMessage = MSG_SAVE_FAILED) }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val message = try {
                repository.delete(id)
                VaultTestTags.MSG_ENTRY_DELETED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MSG_DELETE_FAILED
            }
            _uiState.update { it.copy(userMessage = message) }
        }
    }

    /** Re-subscribes to repository.entries after an error. */
    fun retry() = subscribe()

    fun onUserMessageShown() = _uiState.update { it.copy(userMessage = null) }

    companion object {
        const val MSG_LOAD_FAILED = "Couldn't load your vault"
        const val MSG_SAVE_FAILED = "Couldn't save entry"
        const val MSG_DELETE_FAILED = "Couldn't delete entry"
    }
}
