// Public entry point; signature frozen by the integration contract.
package com.legenddrry.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.model.toDraft
import com.legenddrry.feature.vault.state.VaultViewModel
import com.legenddrry.feature.vault.state.EditorDraftViewModel
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import com.legenddrry.feature.vault.ui.common.copyToClipboard
import com.legenddrry.feature.vault.ui.editor.EntryEditorDialog
import com.legenddrry.feature.vault.ui.entries.EmptyVaultState
import com.legenddrry.feature.vault.ui.entries.EntriesHeader
import com.legenddrry.feature.vault.ui.entries.EntryDetailDialog
import com.legenddrry.feature.vault.ui.entries.EntryRow
import com.legenddrry.feature.vault.ui.entries.ErrorState
import com.legenddrry.feature.vault.ui.entries.LoadingState
import com.legenddrry.feature.vault.ui.entries.NoResultsState
import kotlinx.coroutines.launch

@Composable
fun EntriesRoute(repository: VaultRepository) {
    val vm: VaultViewModel = viewModel(
        key = "legenddrry-vault-entries",
        factory = viewModelFactory { initializer { VaultViewModel(repository) } },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val editorVm: EditorDraftViewModel = viewModel(
        key = "legenddrry-vault-editor-draft",
        factory = viewModelFactory { initializer { EditorDraftViewModel() } },
    )
    val editorState by editorVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Internal "navigation" state.
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    // Always resolve from current state so edits show and deletions close the detail.
    val selectedEntry = selectedId?.let { id -> state.entries.firstOrNull { it.id == id } }
    val deleteTarget = deleteTargetId?.let { id -> state.entries.firstOrNull { it.id == id } }

    LaunchedEffect(selectedId, selectedEntry, state.isLoading) {
        if (selectedId != null && selectedEntry == null && !state.isLoading) selectedId = null
    }
    LaunchedEffect(deleteTargetId, deleteTarget, state.isLoading) {
        if (deleteTargetId != null && deleteTarget == null && !state.isLoading) deleteTargetId = null
    }

    // One-shot VM messages ("Entry saved", "Entry deleted", ...).
    val userMessage = state.userMessage
    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            scope.launch { snackbarHostState.showSnackbar(userMessage) }
            vm.onUserMessageShown()
        }
    }
    fun showMessage(text: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }

    val openAddEditor = { editorVm.open(EntryDraft()) }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(VaultTestTags.ENTRIES_SCREEN),
        contentWindowInsets = WindowInsets.navigationBars,
        // While the full-screen detail is open its own SnackbarHost shows the same state.
        snackbarHost = { if (selectedEntry == null) SnackbarHost(snackbarHostState) },
        topBar = {
            EntriesHeader(
                query = state.query,
                onQueryChange = vm::onQueryChange,
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                onCategorySelected = vm::onCategorySelected,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = openAddEditor,
                modifier = Modifier.testTag(VaultTestTags.ADD_FAB),
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val error = state.errorMessage
            when {
                state.isLoading -> LoadingState()
                error != null -> ErrorState(error, onRetry = vm::retry)
                state.entries.isEmpty() -> EmptyVaultState(onAdd = openAddEditor)
                state.visibleEntries.isEmpty() -> NoResultsState(
                    onClearFilters = {
                        vm.onQueryChange("")
                        vm.onCategorySelected(null)
                    },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.visibleEntries, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            isDuplicate = entry.id in state.duplicatePasswordIds,
                            onClick = { selectedId = entry.id },
                        )
                    }
                }
            }
        }
    }

    if (selectedEntry != null) {
        val entry = selectedEntry
        EntryDetailDialog(
            entry = entry,
            reusedByCount = state.entries.count { it.id != entry.id && it.password == entry.password },
            snackbarHostState = snackbarHostState,
            onClose = { selectedId = null },
            onEdit = { editorVm.open(entry.toDraft()) },
            onDelete = { deleteTargetId = entry.id },
            onCopyUsername = {
                copyToClipboard(context, "Username", entry.username, sensitive = false)
                showMessage(VaultTestTags.MSG_USERNAME_COPIED)
            },
            onCopyPassword = {
                copyToClipboard(context, "Password", entry.password)
                showMessage(VaultTestTags.MSG_PASSWORD_COPIED)
            },
        )
    }

    editorState?.let { editor ->
        EntryEditorDialog(
            initial = editor.initial,
            draftOverride = editor.draft,
            onDraftChange = editorVm::update,
            categories = state.categories,
            isDuplicatePassword = { vm.isPasswordDuplicate(it, editor.initial.id) },
            onDismiss = editorVm::clear,
            onSave = { edited -> vm.save(edited) { editorVm.clear() } },
        )
    }

    if (deleteTarget != null) {
        val target = deleteTarget
        AlertDialog(
            modifier = Modifier.testTag(VaultTestTags.DELETE_DIALOG),
            onDismissRequest = { deleteTargetId = null },
            title = { Text("Delete ${target.title}?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.delete(target.id)
                        deleteTargetId = null
                        selectedId = null
                    },
                    modifier = Modifier.testTag(VaultTestTags.DELETE_CONFIRM),
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTargetId = null },
                    modifier = Modifier.testTag(VaultTestTags.DELETE_CANCEL),
                ) { Text("Cancel") }
            },
        )
    }
}
