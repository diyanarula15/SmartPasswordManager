// OWNER: worker "state". LOCKED API — keep public signatures. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.vault.generator.GeneratorOptions
import com.legenddrry.feature.vault.generator.PasswordGenerator
import com.legenddrry.feature.vault.model.DEFAULT_CATEGORIES
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.model.toEntry
import com.legenddrry.feature.vault.model.validate
import com.legenddrry.feature.vault.strength.PasswordStrength
import com.legenddrry.feature.vault.strength.PasswordStrengthEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class GeneratorUiState(
    val options: GeneratorOptions = GeneratorOptions(),
    val password: String = "",
    val strength: PasswordStrength = PasswordStrengthEvaluator.evaluate(""),
    /** Categories for the save-to-vault editor dropdown. */
    val categories: List<String> = DEFAULT_CATEGORIES,
    /** Current vault entries (for duplicate checks). */
    val entries: List<VaultEntry> = emptyList(),
    /** One-shot snackbar text; UI calls onUserMessageShown() after showing it. */
    val userMessage: String? = null,
)

class GeneratorViewModel(
    private val repository: VaultRepository,
    private val generate: (GeneratorOptions) -> String = PasswordGenerator()::generate,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    init {
        regenerate()
        viewModelScope.launch { repository.entries.collect { list -> _uiState.update { it.copy(entries = list) } } }
    }

    /**
     * Applies new options: length coerced to MIN..MAX; disabling the last enabled category is
     * rejected (state unchanged + userMessage). Regenerates on success.
     */
    fun updateOptions(options: GeneratorOptions) {
        if (!options.hasAnyCategory) return
        _uiState.update { it.copy(options = options) }
        regenerate()
    }

    fun regenerate() {
        val pw = generate(_uiState.value.options)
        _uiState.update { it.copy(password = pw, strength = PasswordStrengthEvaluator.evaluate(pw)) }
    }

    fun isPasswordDuplicate(password: String): Boolean =
        password.isNotEmpty() && _uiState.value.entries.any { it.password == password }

    /** Adds a NEW entry (draft.id ignored). Validates first; calls [onSaved] only on success. */
    fun saveAsEntry(draft: EntryDraft, onSaved: () -> Unit = {}) {
        if (draft.validate().isNotEmpty()) return
        viewModelScope.launch {
            repository.add(draft.copy(id = null).toEntry(newId(), clock()))
            _uiState.update { it.copy(userMessage = "Entry saved") }
            onSaved()
        }
    }

    fun onUserMessageShown() = _uiState.update { it.copy(userMessage = null) }
}
