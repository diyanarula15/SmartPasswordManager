// Used by GenerateRoute too.
package com.legenddrry.feature.vault.ui.editor

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.legenddrry.feature.vault.LocalVaultDisplayOptions
import com.legenddrry.feature.vault.model.DraftField
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.model.normalized
import com.legenddrry.feature.vault.model.validate
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import com.legenddrry.feature.vault.ui.components.StrengthMeter

/**
 * Add/edit form. Validates with [EntryDraft.validate] on save; shows field errors and does NOT call
 * [onSave] while invalid. [onSave] receives the normalized draft. Shows a warning
 * (VaultTestTags.EDITOR_DUPLICATE_WARNING) when [isDuplicatePassword] returns true for the current password.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorDialog(
    initial: EntryDraft,
    categories: List<String>,
    isDuplicatePassword: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (EntryDraft) -> Unit,
    dialogTitle: String = if (initial.id == null) "Add password" else "Edit password",
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var saveAttempted by remember(initial) { mutableStateOf(false) }
    val showByDefault = LocalVaultDisplayOptions.current.showPasswordsByDefault
    var passwordVisible by rememberSaveable { mutableStateOf(showByDefault) }
    var categoryExpanded by remember { mutableStateOf(false) }

    // Errors appear only after the first save attempt, then update live.
    val errors = if (saveAttempted) draft.validate() else emptyMap()
    val isDuplicate = draft.password.isNotEmpty() && isDuplicatePassword(draft.password)
    val categoryOptions = remember(categories, draft.category) {
        (categories + draft.category).filter { it.isNotBlank() }.distinct()
    }

    val trySave = {
        saveAttempted = true
        if (draft.validate().isEmpty()) onSave(draft.normalized())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Let the dialog window resize above the keyboard.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }

        Scaffold(
            modifier = Modifier.fillMaxSize().testTag(VaultTestTags.EDITOR),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = { Text(dialogTitle) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag(VaultTestTags.EDITOR_CANCEL)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        TextButton(onClick = trySave, modifier = Modifier.testTag(VaultTestTags.EDITOR_SAVE)) {
                            Text("Save")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val titleError = errors[DraftField.TITLE]
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.EDITOR_TITLE),
                    label = { Text("Title") },
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { msg ->
                        { Text(msg, modifier = Modifier.testTag(VaultTestTags.EDITOR_ERROR_TITLE)) }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = draft.website,
                    onValueChange = { draft = draft.copy(website = it) },
                    modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.EDITOR_WEBSITE),
                    label = { Text("Website") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = draft.username,
                    onValueChange = { draft = draft.copy(username = it) },
                    modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.EDITOR_USERNAME),
                    label = { Text("Username or email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )

                val passwordError = errors[DraftField.PASSWORD]
                OutlinedTextField(
                    value = draft.password,
                    onValueChange = { draft = draft.copy(password = it) },
                    modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.EDITOR_PASSWORD),
                    label = { Text("Password") },
                    singleLine = true,
                    isError = passwordError != null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.testTag(VaultTestTags.EDITOR_PASSWORD_TOGGLE),
                        ) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    supportingText = passwordError?.let { msg ->
                        { Text(msg, modifier = Modifier.testTag(VaultTestTags.EDITOR_ERROR_PASSWORD)) }
                    },
                )
                StrengthMeter(draft.password, modifier = Modifier.fillMaxWidth())
                if (isDuplicate) {
                    Row(
                        Modifier.testTag(VaultTestTags.EDITOR_DUPLICATE_WARNING),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "This password is already used by another entry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = draft.category,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag(VaultTestTags.EDITOR_CATEGORY),
                        label = { Text("Category") },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        categoryOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    draft = draft.copy(category = option)
                                    categoryExpanded = false
                                },
                                modifier = Modifier.testTag(VaultTestTags.EDITOR_CATEGORY_OPTION_PREFIX + option),
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { draft = draft.copy(notes = it) },
                    modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.EDITOR_NOTES),
                    label = { Text("Notes") },
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
