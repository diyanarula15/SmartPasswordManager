package com.example.group4app.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.legenddrry.core.transfer.*
import com.legenddrry.feature.tools.components.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

private enum class TransferDialog { NONE, EXPORT, UNLOCK, PREVIEW }

/** Ciphertext and decrypted previews live only for this composition/session, never saved state. */
private class TransferBuffers {
    var pendingExport: ByteArray? = null
    var importFile: ByteArray? = null
    var preview: PortableVault? = null
    fun clear() {
        pendingExport?.fill(0)
        importFile?.fill(0)
        pendingExport = null
        importFile = null
        preview = null
    }
}

/** Real offline encrypted transfer UI. Host supplies the real session/auth/vault manager. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultTransferSection(manager: VaultTransferManager) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val buffers = remember(manager) { TransferBuffers() }
    var dialog by remember { mutableStateOf(TransferDialog.NONE) }
    var method by remember { mutableStateOf(BackupUnlockMethod.TRANSFER_PASSWORD) }
    var importMethod by remember { mutableStateOf(BackupUnlockMethod.TRANSFER_PASSWORD) }
    var mode by remember { mutableStateOf(VaultImportMode.MERGE) }
    var master by remember { mutableStateOf("") }
    var transfer by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var unlockCredential by remember { mutableStateOf("") }
    var replaceConfirmed by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }

    fun clearSecrets() {
        master = ""
        transfer = ""
        confirmation = ""
        unlockCredential = ""
    }
    fun message(text: String, failed: Boolean = false) {
        feedback = text
        error = failed
    }
    fun dismiss() {
        if (busy) return
        buffers.clear()
        clearSecrets()
        dialog = TransferDialog.NONE
        attempted = false
        replaceConfirmed = false
    }
    fun open(next: TransferDialog) {
        buffers.clear()
        clearSecrets()
        feedback = null
        attempted = false
        replaceConfirmed = false
        if (next == TransferDialog.EXPORT) method = BackupUnlockMethod.TRANSFER_PASSWORD
        dialog = next
    }

    DisposableEffect(buffers) {
        onDispose {
            operation?.cancel()
            buffers.clear()
            clearSecrets()
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/octet-stream") {
            override fun createIntent(context: android.content.Context, input: String): android.content.Intent =
                super.createIntent(context, input).putExtra(android.content.Intent.EXTRA_LOCAL_ONLY, true)
        },
    ) { uri ->
        val encrypted = buffers.pendingExport
        if (uri == null || encrypted == null) {
            buffers.pendingExport?.fill(0)
            buffers.pendingExport = null
            busy = false
            if (uri != null) {
                operation = scope.launch {
                    val removed = withContext(Dispatchers.IO) { removeIncompleteDocument(resolver, uri) }
                    message(if (removed) "Export cancelled. The empty document was removed."
                        else "Export cancelled. An empty document may remain; delete it from the selected folder.", !removed)
                }
            } else message("Export cancelled. No backup was saved.")
        } else {
            progress = "Saving encrypted backup…"
            operation = scope.launch {
                var committed = false
                try {
                    withContext(Dispatchers.IO) {
                        resolver.openOutputStream(uri, "wt")?.use { output ->
                            output.write(encrypted)
                            output.flush()
                        } ?: throw IllegalStateException("Could not open destination")
                    }
                    ensureActive()
                    committed = true
                    message("Encrypted backup saved. Keep its password separately and safely.")
                } catch (_: CancellationException) {
                    message("Export cancelled.")
                } catch (_: Exception) {
                    message("Could not save the backup. Try another on-device destination.", true)
                } finally {
                    if (!committed) {
                        val removed = withContext(NonCancellable + Dispatchers.IO) {
                            removeIncompleteDocument(resolver, uri)
                        }
                        if (!removed) message("Backup was not saved completely. An incomplete file may remain; delete it from your selected folder.", true)
                    }
                    encrypted.fill(0)
                    buffers.pendingExport = null
                    busy = false
                    progress = ""
                }
            }
        }
    }

    val openDocument = rememberLauncherForActivityResult(object : ActivityResultContracts.OpenDocument() {
        override fun createIntent(context: android.content.Context, input: Array<String>): android.content.Intent =
            super.createIntent(context, input).putExtra(android.content.Intent.EXTRA_LOCAL_ONLY, true)
    }) { uri ->
        if (uri == null) {
            busy = false
            message("Import cancelled. Your vault is unchanged.")
        } else {
            busy = true
            progress = "Reading encrypted backup…"
            operation = scope.launch {
                var file: ByteArray? = null
                try {
                    withContext(Dispatchers.IO) {
                        file = resolver.openInputStream(uri)?.use(::readVaultFileBounded)
                            ?: throw IllegalStateException("Could not open backup")
                    }
                    ensureActive()
                    val readFile = requireNotNull(file)
                    importMethod = manager.inspectBackup(readFile)
                    buffers.importFile = readFile
                    file = null
                    clearSecrets()
                    attempted = false
                    feedback = null
                    dialog = TransferDialog.UNLOCK
                } catch (_: CancellationException) {
                    message("Import cancelled. Your vault is unchanged.")
                } catch (_: Exception) {
                    buffers.clear()
                    message("Could not read this backup. Choose a valid .vault file no larger than 16 MiB.", true)
                } finally {
                    file?.fill(0)
                    busy = false
                    progress = ""
                }
            }
        }
    }

    ToolSectionTitle("Vault Backup & Transfer", "Move your encrypted vault between your own devices.")
    ToolCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIconTile("shield")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("A backup you control", style = ToolsType.title)
                Text("Encrypted .vault files · No account or server", style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Export includes all saved login fields, notes and categories. Import shows a preview before changing your vault.", style = ToolsType.body)
        ToolNotice("Keep it offline", "Android’s file picker may show cloud providers. Choose on-device storage for an offline transfer. Never share a backup password with the file.", "info")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolActionButton("Export vault", { open(TransferDialog.EXPORT) }, enabled = !busy, icon = "lock", modifier = Modifier.testTag("vault-transfer-export"))
            ToolActionButton("Import vault", {
                open(TransferDialog.NONE)
                try {
                    busy = true
                    progress = "Choose an encrypted .vault file…"
                    openDocument.launch(arrayOf("application/octet-stream", "application/x-vault", "*/*"))
                } catch (_: Exception) {
                    busy = false
                    message("The file picker could not open. Please try again.", true)
                }
            }, enabled = !busy, icon = "devices", kind = ToolButtonKind.OUTLINED, modifier = Modifier.testTag("vault-transfer-import"))
        }
        if (busy) TransferProgress(progress) {
            val running = operation
            if (running?.isActive == true) {
                progress = "Cancelling transfer…"
                running.cancel()
                clearSecrets()
            } else {
                buffers.clear()
                clearSecrets()
                busy = false
                dialog = TransferDialog.NONE
                message("Transfer cancelled.")
            }
        }
        feedback?.let { ToolFeedback(it, error, onDismiss = { feedback = null }) }
    }

    when (dialog) {
        TransferDialog.EXPORT -> {
            val transferError = transferPasswordError(transfer)
            val masterError = master.isEmpty() || master.length > VaultTransferLimits.MAX_PASSWORD_CHARS
            ToolDialog(
                "Export encrypted vault", { dismiss() },
                confirmButton = {
                    ToolActionButton("Choose destination", {
                        attempted = true
                        val valid = !masterError && (method == BackupUnlockMethod.MASTER_PASSWORD || (transferError == null && transferPasswordsMatch(transfer, confirmation)))
                        if (valid && !busy) {
                            busy = true
                            feedback = null
                            progress = "Verifying and encrypting…"
                            val masterChars = master.toCharArray()
                            val transferChars = if (method == BackupUnlockMethod.TRANSFER_PASSWORD) transfer.toCharArray() else null
                            clearSecrets()
                            operation = scope.launch {
                                var encrypted: ByteArray? = null
                                try {
                                    encrypted = manager.exportVault(masterChars, method, transferChars)
                                    ensureActive()
                                    buffers.pendingExport = encrypted
                                    encrypted = null
                                    dialog = TransferDialog.NONE
                                    progress = "Choose where to save your backup…"
                                    val date = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())
                                    createDocument.launch("LegenDDRRY-$date.vault")
                                } catch (_: CancellationException) {
                                    buffers.clear()
                                    clearSecrets()
                                    dialog = TransferDialog.NONE
                                    busy = false
                                    message("Export cancelled.")
                                } catch (_: Exception) {
                                    buffers.clear()
                                    busy = false
                                    message("Could not export. Check your master password and make sure the vault is unlocked.", true)
                                } finally {
                                    masterChars.fill('\u0000')
                                    transferChars?.fill('\u0000')
                                    encrypted?.fill(0)
                                }
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.testTag("vault-export-confirm"))
                },
                dismissButton = { ToolActionButton("Cancel", { dismiss() }, enabled = !busy, kind = ToolButtonKind.TEXT) },
            ) {
                Text("How would you like to unlock this vault on another device?", style = ToolsType.title)
                Text("Your master password is verified before each export. Only an encrypted file is saved.", style = ToolsType.body)
                TransferChoice("Separate transfer password (recommended)", "Use a new password to unlock this backup.", method == BackupUnlockMethod.TRANSFER_PASSWORD, !busy) { method = BackupUnlockMethod.TRANSFER_PASSWORD }
                TransferChoice("Master password", "This backup requires your current master password on import.", method == BackupUnlockMethod.MASTER_PASSWORD, !busy) { method = BackupUnlockMethod.MASTER_PASSWORD }
                SecureTransferField(master, { master = it }, "Current master password", !busy, attempted && masterError, "Enter your current master password.", "vault-export-master")
                if (method == BackupUnlockMethod.TRANSFER_PASSWORD) {
                    Text("Keep this transfer password separately. If you lose it, this backup cannot be recovered.", style = ToolsType.body)
                    SecureTransferField(transfer, { transfer = it }, "Transfer password", !busy, attempted && transferError != null, transferError ?: "Use at least 12 characters and three character types.", "vault-export-transfer")
                    SecureTransferField(confirmation, { confirmation = it }, "Confirm transfer password", !busy, attempted && !transferPasswordsMatch(transfer, confirmation), "Both transfer passwords must match.", "vault-export-confirm-password")
                }
                if (busy) TransferProgress(progress) { operation?.cancel() }
                if (error) feedback?.let { ToolFeedback(it, true) }
            }
        }
        TransferDialog.UNLOCK -> ToolDialog(
            "Unlock your vault backup", { dismiss() },
            confirmButton = {
                ToolActionButton("Preview backup", {
                    attempted = true
                    val file = buffers.importFile
                    if (file != null && unlockCredential.isNotEmpty() && unlockCredential.length <= VaultTransferLimits.MAX_PASSWORD_CHARS && !busy) {
                        val credential = unlockCredential.toCharArray()
                        unlockCredential = ""
                        busy = true
                        feedback = null
                        progress = "Unlocking backup…"
                        operation = scope.launch {
                            try {
                                val decoded = manager.unlockBackup(file, credential)
                                ensureActive()
                                buffers.preview = decoded
                                buffers.importFile?.fill(0)
                                buffers.importFile = null
                                mode = VaultImportMode.MERGE
                                master = ""
                                attempted = false
                                replaceConfirmed = false
                                dialog = TransferDialog.PREVIEW
                            } catch (_: CancellationException) {
                                buffers.clear()
                                dialog = TransferDialog.NONE
                                message("Import cancelled. Your vault is unchanged.")
                            } catch (_: Exception) {
                                message("Could not unlock this backup. The password may be incorrect or the file may be damaged.", true)
                            } finally {
                                credential.fill('\u0000')
                                busy = false
                                progress = ""
                            }
                        }
                    }
                }, enabled = !busy, modifier = Modifier.testTag("vault-import-unlock"))
            },
            dismissButton = { ToolActionButton("Cancel", { dismiss() }, enabled = !busy, kind = ToolButtonKind.TEXT) },
        ) {
            Text(if (importMethod == BackupUnlockMethod.TRANSFER_PASSWORD) "This file uses a separate transfer password." else "This file uses the master password that was current when it was exported.", style = ToolsType.body)
            SecureTransferField(unlockCredential, { unlockCredential = it }, if (importMethod == BackupUnlockMethod.TRANSFER_PASSWORD) "Transfer password" else "Backup master password", !busy, attempted && (unlockCredential.isEmpty() || unlockCredential.length > VaultTransferLimits.MAX_PASSWORD_CHARS), "Enter the password used to create this backup.", "vault-import-password")
            if (busy) TransferProgress(progress) { operation?.cancel() }
            if (error) feedback?.let { ToolFeedback(it, true) }
        }
        TransferDialog.PREVIEW -> buffers.preview?.let { preview ->
            val categoryCounts = remember(preview) {
                preview.entries.groupingBy { it.category.ifBlank { "Uncategorized" } }
                    .eachCount().toSortedMap().toList()
            }
            ToolDialog(
                "Review vault import", { dismiss() },
                confirmButton = {
                    ToolActionButton("Import now", {
                        attempted = true
                        if (master.isNotEmpty() && master.length <= VaultTransferLimits.MAX_PASSWORD_CHARS && (mode != VaultImportMode.REPLACE || replaceConfirmed) && !busy) {
                            val currentMaster = master.toCharArray()
                            master = ""
                            busy = true
                            feedback = null
                            progress = "Verifying and importing…"
                            operation = scope.launch {
                                try {
                                    val result = manager.restoreVault(preview, mode, currentMaster)
                                    buffers.clear()
                                    dialog = TransferDialog.NONE
                                    message("Import complete: ${result.imported} imported, ${result.skipped} skipped, ${result.preservedConflicts} conflicts preserved.")
                                } catch (_: CancellationException) {
                                    buffers.clear()
                                    dialog = TransferDialog.NONE
                                    message("Import interrupted. Check your vault before retrying.")
                                } catch (_: Exception) {
                                    message("Could not import. Check your current master password and make sure the vault is unlocked. Your existing vault was not replaced by a partial import.", true)
                                } finally {
                                    currentMaster.fill('\u0000')
                                    busy = false
                                    progress = ""
                                }
                            }
                        }
                    }, enabled = !busy, modifier = Modifier.testTag("vault-import-commit"))
                },
                dismissButton = { ToolActionButton("Cancel", { dismiss() }, enabled = !busy, kind = ToolButtonKind.TEXT) },
            ) {
                Text("${preview.entries.size} saved logins", style = ToolsType.titleLarge)
                Text("${preview.entries.count { it.notes.isNotBlank() }} entries with secure notes · ${preview.entries.map { it.category.ifBlank { "Uncategorized" } }.toSet().size} categories · ${preview.entries.size} total records", style = ToolsType.body)
                Text("Backup created ${formatToolTimestamp(preview.createdAtEpochMs)}", style = ToolsType.caption)
                Text("Categories", style = ToolsType.label)
                categoryCounts.take(12).forEach { (category, count) ->
                    Text("${category.take(120)} · $count", style = ToolsType.body)
                }
                if (categoryCounts.size > 12) {
                    Text("${categoryCounts.size - 12} more categories are included in this backup.", style = ToolsType.caption)
                }
                Text("How should these records be imported?", style = ToolsType.label)
                TransferChoice("Merge", "Keep existing records. Conflicting IDs are preserved as separate records.", mode == VaultImportMode.MERGE, !busy) { mode = VaultImportMode.MERGE }
                TransferChoice("Only new", "Skip matching IDs and identical records.", mode == VaultImportMode.ONLY_NEW, !busy) { mode = VaultImportMode.ONLY_NEW }
                TransferChoice("Replace vault", "Remove current records and replace them with this backup.", mode == VaultImportMode.REPLACE, !busy) { mode = VaultImportMode.REPLACE }
                if (mode == VaultImportMode.REPLACE) {
                    ToolFeedback("Replacing removes all records currently in this vault. Export a backup first if you need to keep them.", true)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(replaceConfirmed, { replaceConfirmed = it }, enabled = !busy, modifier = Modifier.testTag("vault-import-replace-confirm"))
                        Text("I understand that my current vault will be replaced.", style = ToolsType.body)
                    }
                    if (attempted && !replaceConfirmed) ToolFeedback("Confirm replacement before importing.", true)
                }
                SecureTransferField(master, { master = it }, "Current master password", !busy, attempted && (master.isEmpty() || master.length > VaultTransferLimits.MAX_PASSWORD_CHARS), "Re-enter your current master password to authorize this import.", "vault-import-master")
                if (busy) TransferProgress(progress) { operation?.cancel() }
                if (error) feedback?.let { ToolFeedback(it, true) }
            }
        }
        TransferDialog.NONE -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SecureTransferField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, isError: Boolean, supporting: String, tag: String) {
    var visible by remember { mutableStateOf(false) }
    // Deliberately use remember, NOT rememberTextFieldState (which saves state).
    // SecureTextField disables copy/cut and drag-copy even in visible mode.
    val fieldState = remember { TextFieldState(value) }
    val latestChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (fieldState.text.toString() != value) {
            fieldState.edit { replace(0, length, value) }
        }
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { latestChange(it) }
    }
    DisposableEffect(fieldState) {
        onDispose { fieldState.edit { replace(0, length, "") } }
    }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = ToolsType.label)
        OutlinedSecureTextField(
            state = fieldState,
            enabled = enabled, isError = isError,
            textStyle = ToolsType.bodyLarge,
            inputTransformation = InputTransformation {
                if (length > VaultTransferLimits.MAX_PASSWORD_CHARS) revertAllChanges()
            },
            textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag(tag),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }, modifier = Modifier.semantics { contentDescription = if (visible) "Hide $label" else "Show $label" }) {
                    ToolIcon(if (visible) "lock" else "visibility", tint = colors.primary)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surfaceVariant, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        Text(supporting, style = ToolsType.caption, color = if (isError) colors.error else colors.onSurfaceVariant)
    }
}

@Composable
private fun TransferChoice(title: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null, enabled = enabled)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ToolsType.label)
            Text(detail, style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransferProgress(text: String, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(text, style = ToolsType.body)
        ToolActionButton("Cancel transfer", onCancel, kind = ToolButtonKind.TEXT)
    }
}

internal fun transferPasswordError(password: String): String? {
    if (password.length < 12) return "Use at least 12 characters."
    if (password.length > VaultTransferLimits.MAX_PASSWORD_CHARS) return "Use 1,024 characters or fewer."
    if (password.toSet().size < 6) return "Use at least six distinct characters."
    val types = listOf(password.any(Char::isLowerCase), password.any(Char::isUpperCase), password.any(Char::isDigit), password.any { !it.isLetterOrDigit() }).count { it }
    return if (types < 3) "Use at least three types: lowercase, uppercase, numbers or symbols." else null
}

internal fun transferPasswordsMatch(password: String, confirmation: String): Boolean =
    password.isNotEmpty() && password == confirmation
