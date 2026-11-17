// Public entry point; signature frozen by the integration contract.
package com.legenddrry.feature.vault

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legenddrry.core.VaultRepository
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.state.GeneratorViewModel
import com.legenddrry.feature.vault.state.EditorDraftViewModel
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import com.legenddrry.feature.vault.ui.common.copyToClipboard
import com.legenddrry.feature.vault.ui.editor.EntryEditorDialog
import com.legenddrry.feature.vault.ui.generator.GeneratorContent
import kotlinx.coroutines.launch

@Composable
fun GenerateRoute(repository: VaultRepository) {
    val vm: GeneratorViewModel = viewModel(
        key = "legenddrry-vault-generator",
        factory = viewModelFactory { initializer { GeneratorViewModel(repository) } },
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val editorVm: EditorDraftViewModel = viewModel(
        key = "legenddrry-generator-editor-draft",
        factory = viewModelFactory { initializer { EditorDraftViewModel() } },
    )
    val editorState by editorVm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.userMessage) {
        val message = state.userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.onUserMessageShown()
    }

    Scaffold(
        modifier = Modifier.testTag(VaultTestTags.GENERATOR_SCREEN),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        GeneratorContent(
            password = state.password,
            options = state.options,
            onOptionsChange = vm::updateOptions,
            onRegenerate = vm::regenerate,
            onCopy = {
                copyToClipboard(context, "Password", state.password)
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(VaultTestTags.MSG_PASSWORD_COPIED)
                }
            },
            onSave = { editorVm.open(EntryDraft(password = state.password)) },
            modifier = Modifier.padding(padding),
        )
    }

    editorState?.let { editor ->
        val close = editorVm::clear
        EntryEditorDialog(
            initial = editor.initial,
            draftOverride = editor.draft,
            onDraftChange = editorVm::update,
            categories = state.categories,
            isDuplicatePassword = vm::isPasswordDuplicate,
            onDismiss = close,
            onSave = { vm.saveAsEntry(it) { close() } },
            dialogTitle = "Save password",
        )
    }
}
