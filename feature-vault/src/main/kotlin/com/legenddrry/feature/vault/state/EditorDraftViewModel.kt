package com.legenddrry.feature.vault.state

import androidx.lifecycle.ViewModel
import com.legenddrry.feature.vault.model.EntryDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Initial and edited values stay only in the unlocked session's ViewModelStore, never a Bundle. */
data class EditorDraftUiState(val initial: EntryDraft, val draft: EntryDraft)

class EditorDraftViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<EditorDraftUiState?>(null)
    val state = mutableState.asStateFlow()

    fun open(initial: EntryDraft) {
        mutableState.value = EditorDraftUiState(initial, initial)
    }

    fun update(draft: EntryDraft) {
        mutableState.update { current -> current?.copy(draft = draft) }
    }

    fun clear() {
        mutableState.value = null
    }

    override fun onCleared() {
        // Strings are immutable; release all references rather than serializing secret fields.
        clear()
    }
}
