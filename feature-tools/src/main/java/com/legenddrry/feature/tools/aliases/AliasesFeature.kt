package com.legenddrry.feature.tools.aliases

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.components.EmptyToolState
import com.legenddrry.feature.tools.components.ToolActionButton
import com.legenddrry.feature.tools.components.ToolButtonKind
import com.legenddrry.feature.tools.components.ToolCard
import com.legenddrry.feature.tools.components.ToolDialog
import com.legenddrry.feature.tools.components.ToolFeedback
import com.legenddrry.feature.tools.components.ToolIcon
import com.legenddrry.feature.tools.components.ToolIconTile
import com.legenddrry.feature.tools.components.ToolNotice
import com.legenddrry.feature.tools.components.ToolPage
import com.legenddrry.feature.tools.components.ToolSectionTitle
import com.legenddrry.feature.tools.components.ToolTextField
import com.legenddrry.feature.tools.components.ToolsType
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
    val colors = MaterialTheme.colorScheme

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

    fun clearFeedback() {
        feedback = null
        failed = false
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

    Box(Modifier.fillMaxSize()) {
        // Reserve a permanent action strip: an in-scroll footer cannot stop the FAB from
        // intercepting taps on a card that is visible partway through the list.
        Box(Modifier.fillMaxSize().padding(bottom = 88.dp)) {
            ToolPage("Hide my email", "Demo aliases — email forwarding not enabled") {
                ToolNotice(
                    title = "Local demo aliases",
                    message = "Local demonstration records only. These example.com addresses do not receive or forward email.",
                    icon = "mail",
                )
                feedback?.let { message ->
                    Box(Modifier.testTag("aliases-feedback")) {
                        ToolFeedback(message, failed, onDismiss = { clearFeedback() })
                    }
                }
                ToolSectionTitle("Your aliases", "A separate local record for each service.")
                ToolTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search aliases",
                    supportingText = "Search website, service, or alias address",
                    leadingIcon = "search",
                    search = true,
                    modifier = Modifier.testTag("aliases-search"),
                )
                val filtered = filterAliases(aliases, query)
                Text(
                    "Saved aliases (${filtered.size})",
                    style = ToolsType.label,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
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
                                clearFeedback()
                                detailId = alias.id
                            },
                            onDelete = {
                                clearFeedback()
                                deleteId = alias.id
                            },
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = {
                if (!busy) {
                    serviceName = ""
                    createAttempted = false
                    clearFeedback()
                    creating = true
                }
            },
            icon = { ToolIcon("add", tint = colors.onSecondary) },
            text = { Text("Create demo alias", style = ToolsType.label) },
            shape = RoundedCornerShape(28.dp),
            containerColor = colors.secondary,
            contentColor = colors.onSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                .testTag("aliases-create").semantics { if (busy) disabled() },
        )
    }

    if (creating) {
        val error = aliasServiceError(serviceName)
        ToolDialog(
            title = "Create demo alias",
            onDismiss = { if (!busy) creating = false },
            confirmButton = {
                ToolActionButton(
                    text = "Create record",
                    enabled = !busy,
                    modifier = Modifier.testTag("aliases-confirm-create"),
                    icon = "add",
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
                )
            },
            dismissButton = {
                ToolActionButton("Cancel", { creating = false }, enabled = !busy, kind = ToolButtonKind.TEXT)
            },
        ) {
            Text("Save a local record for a website or service. Email forwarding is not enabled.", style = ToolsType.body)
            ToolTextField(
                value = serviceName,
                onValueChange = { serviceName = it },
                label = "Website or service",
                isError = createAttempted && error != null,
                supportingText = if (createAttempted && error != null) error else "For example: Student portal",
                enabled = !busy,
                leadingIcon = "mail",
                modifier = Modifier.testTag("aliases-service"),
            )
            if (failed) AliasDialogFeedback(feedback, true)
        }
    }

    aliases.firstOrNull { it.id == detailId }?.let { alias ->
        ToolDialog(
            title = "Alias details",
            onDismiss = { detailId = null },
            confirmButton = {
                ToolActionButton("Close", { detailId = null }, kind = ToolButtonKind.TEXT)
            },
            dismissButton = {
                ToolActionButton("Copy alias", { copy(alias) }, icon = "copy", kind = ToolButtonKind.TONAL)
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolIconTile("mail")
                Text(alias.serviceName, style = ToolsType.title, color = colors.onSurface)
            }
            Text(alias.aliasAddress, style = ToolsType.bodyLarge, color = colors.onSurface)
            AliasStatus(alias.isActive)
            AliasMetadata("schedule", "Created ${formatToolTimestamp(alias.createdAtEpochMs)}")
            Text("Demo address — email forwarding is not enabled.", style = ToolsType.body)
            AliasDialogFeedback(feedback, failed)
        }
    }

    aliases.firstOrNull { it.id == deleteId }?.let { alias ->
        ToolDialog(
            title = "Delete alias?",
            onDismiss = { if (!busy) deleteId = null },
            confirmButton = {
                ToolActionButton(
                    text = "Delete",
                    enabled = !busy,
                    icon = "delete",
                    modifier = Modifier.testTag("aliases-confirm-delete"),
                    onClick = {
                        perform("Demo alias deleted.") {
                            repository.delete(alias.id)
                            deleteId = null
                            if (detailId == alias.id) detailId = null
                        }
                    },
                )
            },
            dismissButton = {
                ToolActionButton("Cancel", { deleteId = null }, enabled = !busy, kind = ToolButtonKind.TEXT)
            },
        ) {
            ToolIconTile("delete")
            Text("Remove the local demo alias for ${alias.serviceName}? This cannot be undone.", style = ToolsType.body)
            if (failed) AliasDialogFeedback(feedback, true)
        }
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
    val colors = MaterialTheme.colorScheme
    ToolCard(onClick = onDetails) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ToolIconTile("mail")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(alias.serviceName, style = ToolsType.title, color = colors.onSurface)
                Text("Demo email alias", style = ToolsType.caption, color = colors.onSurfaceVariant)
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(48.dp).testTag("alias-copy-${alias.id}")
                    .semantics { contentDescription = "Copy alias for ${alias.serviceName}" },
            ) { ToolIcon("copy", tint = colors.primary) }
        }
        Text(alias.aliasAddress, style = ToolsType.bodyLarge, color = colors.onSurface)
        AliasMetadata("schedule", "Created ${formatToolTimestamp(alias.createdAtEpochMs)}")
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().animateContentSize(tween(150)),
        ) {
            Box(Modifier.weight(1f)) { AliasStatus(alias.isActive) }
            Switch(
                checked = alias.isActive,
                onCheckedChange = onToggle,
                enabled = !busy,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    checkedBorderColor = colors.primary,
                    uncheckedThumbColor = colors.onSurfaceVariant,
                    uncheckedTrackColor = colors.surfaceVariant,
                    uncheckedBorderColor = colors.outline,
                ),
                modifier = Modifier.semantics { contentDescription = "Enable alias for ${alias.serviceName}" }
                    .testTag("alias-toggle-${alias.id}"),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolActionButton(
                "Details", onDetails, icon = "info", kind = ToolButtonKind.TONAL,
                modifier = Modifier.testTag("alias-details-${alias.id}"),
            )
            ToolActionButton(
                "Delete", onDelete, enabled = !busy, icon = "delete", kind = ToolButtonKind.TEXT,
                modifier = Modifier.testTag("alias-delete-${alias.id}"),
            )
        }
    }
}

@Composable
private fun AliasStatus(active: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (active) colors.primaryContainer else colors.surfaceVariant,
        tween(150), label = "aliasStatusContainer",
    )
    val content by animateColorAsState(
        if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
        tween(150), label = "aliasStatusContent",
    )
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIcon(if (active) "check_circle" else "pause", tint = content, size = 18.dp, filled = active)
            Text(if (active) "Active local record" else "Disabled local record", style = ToolsType.caption)
        }
    }
}

@Composable
private fun AliasMetadata(icon: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolIcon(icon, size = 18.dp)
        Text(text, style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AliasDialogFeedback(feedback: String?, failed: Boolean) {
    feedback?.let { message ->
        Box(Modifier.testTag("aliases-dialog-feedback")) {
            ToolFeedback(message, isError = failed)
        }
    }
}
