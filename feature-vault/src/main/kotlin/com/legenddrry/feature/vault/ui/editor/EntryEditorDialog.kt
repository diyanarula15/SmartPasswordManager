// OWNER: worker "entries-ui". LOCKED API — keep signature. Used by GenerateRoute too. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.legenddrry.feature.vault.model.EntryDraft
import com.legenddrry.feature.vault.model.normalized
import com.legenddrry.feature.vault.model.validate
import com.legenddrry.feature.vault.ui.common.VaultTestTags

/**
 * Add/edit form. Validates with [EntryDraft.validate] on save; shows field errors and does NOT call
 * [onSave] while invalid. [onSave] receives the normalized draft. Shows a warning
 * (VaultTestTags.EDITOR_DUPLICATE_WARNING) when [isDuplicatePassword] returns true for the current password.
 */
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
    AlertDialog(
        modifier = Modifier.testTag(VaultTestTags.EDITOR),
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column {
                OutlinedTextField(draft.title, { draft = draft.copy(title = it) }, Modifier.testTag(VaultTestTags.EDITOR_TITLE), label = { Text("Title") })
                OutlinedTextField(draft.password, { draft = draft.copy(password = it) }, Modifier.testTag(VaultTestTags.EDITOR_PASSWORD), label = { Text("Password") })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (draft.validate().isEmpty()) onSave(draft.normalized()) }, Modifier.testTag(VaultTestTags.EDITOR_SAVE)) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, Modifier.testTag(VaultTestTags.EDITOR_CANCEL)) { Text("Cancel") } },
    )
}
