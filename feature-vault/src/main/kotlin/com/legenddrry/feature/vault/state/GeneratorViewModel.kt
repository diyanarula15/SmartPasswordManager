// OWNER: worker "state". LOCKED API — keep public signatures.
package com.legenddrry.feature.vault.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.vault.generator.GeneratorOptions
import com.legenddrry.feature.vault.generator.PasswordGenerator
import com.legenddrry.feature.vault.model.DEFAULT_CATEGORIES
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.model.categoriesFor
import com.legenddrry.feature.vault.model.toEntry
import com.legenddrry.feature.vault.model.validate
import com.legenddrry.feature.vault.strength.PasswordStrength
import com.legenddrry.feature.vault.strength.PasswordStrengthEvaluator
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import kotlinx.coroutines.CancellationException
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
        viewModelScope.launch {
            try {
                repository.entries.collect { list ->
                    _uiState.update { it.copy(entries = list, categories = categoriesFor(list)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Duplicate checks are best-effort here: keep the current (possibly empty) list.
            }
        }
    }

    /**
     * Applies new options: length coerced to MIN..MAX; disabling the last enabled category is
     * rejected (state unchanged + userMessage). Regenerates on success.
     */
    fun updateOptions(options: GeneratorOptions) {
        if (!options.hasAnyCategory) {
            _uiState.update { it.copy(userMessage = MSG_NEED_CATEGORY) }
            return
        }
        val coerced = options.copy(
            length = options.length.coerceIn(GeneratorOptions.MIN_LENGTH, GeneratorOptions.MAX_LENGTH),
        )
        _uiState.update { it.copy(options = coerced) }
        regenerate()
    }

    fun regenerate() {
        val pw = try {
            generate(_uiState.value.options)
        } catch (e: IllegalArgumentException) {
            _uiState.update { it.copy(userMessage = MSG_GENERATE_FAILED) }
            return
        }
        _uiState.update { it.copy(password = pw, strength = PasswordStrengthEvaluator.evaluate(pw)) }
    }

    fun isPasswordDuplicate(password: String): Boolean =
        password.isNotEmpty() && _uiState.value.entries.any { it.password == password }

    /** Adds a NEW entry (draft.id ignored). Validates first; calls [onSaved] only on success. */
    fun saveAsEntry(draft: EntryDraft, onSaved: () -> Unit = {}) {
        val fresh = draft.copy(id = null)
        val errors = fresh.validate()
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(userMessage = errors.values.first()) }
            return
        }
        viewModelScope.launch {
            val ok = try {
                repository.add(fresh.toEntry(newId(), clock()))
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

    fun onUserMessageShown() = _uiState.update { it.copy(userMessage = null) }

    companion object {
        const val MSG_NEED_CATEGORY = "Select at least one character type"
        const val MSG_GENERATE_FAILED = "Couldn't generate password"
        const val MSG_SAVE_FAILED = "Couldn't save entry"
    }
}
