package com.legenddrry.feature.tools.aliases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.components.EmptyToolState
import com.legenddrry.feature.tools.components.ToolCard
import com.legenddrry.feature.tools.components.ToolPage
import com.legenddrry.feature.tools.components.formatToolTimestamp
import com.legenddrry.feature.tools.contracts.AliasesRepository
import com.legenddrry.feature.tools.contracts.EmailAlias
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AliasesFeature(repository: AliasesRepository) {
    val aliases by repository.aliases.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var serviceName by rememberSaveable { mutableStateOf("") }
    var createAttempted by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun perform(success: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        feedback = null
        failed = false
        scope.launch {
            try {
                action()
                feedback = success
                failed = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                feedback = failure.message ?: "Could not update the alias. Please try again."
                failed = true
            } finally {
                busy = false
            }
        }
    }

    fun copy(alias: EmailAlias) {
        try {
            clipboard.setText(AnnotatedString(alias.aliasAddress))
            feedback = "Alias copied to clipboard. This demo address does not receive email."
            failed = false
        } catch (failure: Exception) {
            feedback = "Could not copy the alias. Please try again."
            failed = true
        }
    }

    ToolPage("Hide My Email", "Demo Aliases — Email Forwarding Not Enabled") {
        ToolCard {
            Text("Local demonstration", style = MaterialTheme.typography.titleMedium)
            Text("These saved records use example.com. They do not create working email addresses or forward mail. Enabling a record only changes its local status.")
            Button(
                onClick = {
                    serviceName = ""
                    createAttempted = false
                    feedback = null
                    failed = false
                    creating = true
                },
                enabled = !busy,
                modifier = Modifier.testTag("aliases-create"),
            ) { Text("Create demo alias") }
        }
        feedback?.let {
            Text(
                it,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("aliases-feedback"),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search aliases") },
            supportingText = { Text("Search website, service, or alias address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("aliases-search"),
        )
        val filtered = filterAliases(aliases, query)
        Text("Saved aliases (${filtered.size})", style = MaterialTheme.typography.titleLarge)
        if (aliases.isEmpty()) {
            EmptyToolState("No aliases yet", "Create a demo alias for a website or service to see it here.")
        } else if (filtered.isEmpty()) {
            EmptyToolState("No matching aliases", "Try a different service name or email address.")
        } else {
            filtered.forEach { alias ->
                AliasCard(
                    alias = alias,
                    busy = busy,
                    onCopy = { copy(alias) },
                    onToggle = { active ->
                        perform(if (active) "Demo alias enabled." else "Demo alias disabled.") {
                            repository.setActive(alias.id, active)
                        }
                    },
                    onDetails = {
                        feedback = null
                        failed = false
                        detailId = alias.id
                    },
                    onDelete = {
                        feedback = null
                        failed = false
                        deleteId = alias.id
                    },
                )
            }
        }
    }

    if (creating) {
        val error = aliasServiceError(serviceName)
        AlertDialog(
            onDismissRequest = { if (!busy) creating = false },
            title = { Text("Create demo alias") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Save a local record for a website or service. Email forwarding is not enabled.")
                    OutlinedTextField(
                        value = serviceName,
                        onValueChange = { serviceName = it },
                        label = { Text("Website or service") },
                        singleLine = true,
                        isError = createAttempted && error != null,
                        supportingText = {
                            if (createAttempted && error != null) Text(error)
                            else Text("For example: Student portal")
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("aliases-service"),
                    )
                    if (failed) AliasDialogFeedback(feedback, true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.testTag("aliases-confirm-create"),
                    onClick = {
                        createAttempted = true
                        if (error == null) {
                            perform("Demo alias created. Email forwarding is not enabled.") {
                                repository.create(serviceName)
                                query = ""
                                creating = false
                            }
                        }
                    },
                ) { Text("Create record") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }, enabled = !busy) { Text("Cancel") }
            },
        )
    }

    aliases.firstOrNull { it.id == detailId }?.let { alias ->
        AlertDialog(
            onDismissRequest = { detailId = null },
            title = { Text("Alias details") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(alias.serviceName, style = MaterialTheme.typography.titleMedium)
                    Text(alias.aliasAddress)
                    Text("Created ${formatToolTimestamp(alias.createdAtEpochMs)}")
                    Text(if (alias.isActive) "Active local record" else "Disabled local record")
                    Text("Demo address — email forwarding is not enabled.")
                    AliasDialogFeedback(feedback, failed)
                }
            },
            confirmButton = { TextButton(onClick = { detailId = null }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { copy(alias) }) { Text("Copy alias") } },
        )
    }

    aliases.firstOrNull { it.id == deleteId }?.let { alias ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleteId = null },
            title = { Text("Delete alias?") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Remove the local demo alias for ${alias.serviceName}? This cannot be undone.")
                    if (failed) AliasDialogFeedback(feedback, true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    modifier = Modifier.testTag("aliases-confirm-delete"),
                    onClick = {
                        perform("Demo alias deleted.") {
                            repository.delete(alias.id)
                            deleteId = null
                            if (detailId == alias.id) detailId = null
                        }
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteId = null }, enabled = !busy) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AliasCard(
    alias: EmailAlias,
    busy: Boolean,
    onCopy: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
) {
    ToolCard {
        Text(alias.serviceName, style = MaterialTheme.typography.titleMedium)
        Text(alias.aliasAddress, style = MaterialTheme.typography.bodyLarge)
        Text("Created ${formatToolTimestamp(alias.createdAtEpochMs)}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                if (alias.isActive) "Active local record" else "Disabled local record",
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = alias.isActive,
                onCheckedChange = onToggle,
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Enable alias for ${alias.serviceName}" }
                    .testTag("alias-toggle-${alias.id}"),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedButton(onClick = onCopy, modifier = Modifier.testTag("alias-copy-${alias.id}")) {
                Text("Copy")
            }
            OutlinedButton(onClick = onDetails, modifier = Modifier.testTag("alias-details-${alias.id}")) {
                Text("Details")
            }
            TextButton(onClick = onDelete, enabled = !busy, modifier = Modifier.testTag("alias-delete-${alias.id}")) {
                Text("Delete")
            }
        }
    }
}

@Composable
private fun AliasDialogFeedback(feedback: String?, failed: Boolean) {
    feedback?.let {
        Text(
            it,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("aliases-dialog-feedback"),
        )
    }
}
