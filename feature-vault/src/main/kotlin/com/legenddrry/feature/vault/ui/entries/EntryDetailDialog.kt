package com.legenddrry.feature.vault.ui.entries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.legenddrry.core.VaultEntry
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import com.legenddrry.feature.vault.ui.components.StrengthMeter
import java.text.DateFormat
import java.util.Date

internal const val MASKED_PASSWORD = "••••••••"

/**
 * Full-screen detail for one entry. Hosts its own [SnackbarHost] (sharing [snackbarHostState]
 * with the list screen) so copy feedback is visible above the list while the detail is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryDetailDialog(
    entry: VaultEntry,
    reusedByCount: Int,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopyUsername: () -> Unit,
    onCopyPassword: () -> Unit,
) {
    // Keyed by id: visibility resets to hidden whenever a (different) entry is opened.
    var passwordVisible by remember(entry.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag(VaultTestTags.DETAIL_SCREEN),
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets(0),
                    title = { Text("Details") },
                    navigationIcon = {
                        IconButton(onClick = onClose, modifier = Modifier.testTag(VaultTestTags.DETAIL_CLOSE)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = onEdit, modifier = Modifier.testTag(VaultTestTags.DETAIL_EDIT)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.testTag(VaultTestTags.DETAIL_DELETE)) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LetterAvatar(entry.title, size = 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag(VaultTestTags.DETAIL_TITLE),
                        )
                        Spacer(Modifier.height(4.dp))
                        CategoryLabel(entry.category)
                    }
                }

                // Credentials
                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    DetailRow(icon = Icons.Outlined.Language, label = "Website", value = entry.website.ifBlank { "—" })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    DetailRow(
                        icon = Icons.Outlined.Person,
                        label = "Username",
                        value = entry.username.ifBlank { "—" },
                        valueTag = VaultTestTags.DETAIL_USERNAME,
                    ) {
                        IconButton(
                            onClick = onCopyUsername,
                            modifier = Modifier.testTag(VaultTestTags.DETAIL_COPY_USERNAME),
                        ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy username") }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    DetailRow(
                        icon = Icons.Outlined.Key,
                        label = "Password",
                        value = if (passwordVisible) entry.password else MASKED_PASSWORD,
                        valueTag = VaultTestTags.DETAIL_PASSWORD,
                        monospace = true,
                    ) {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.testTag(VaultTestTags.DETAIL_TOGGLE_VISIBILITY),
                        ) {
                            Icon(
                                if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                        IconButton(
                            onClick = onCopyPassword,
                            modifier = Modifier.testTag(VaultTestTags.DETAIL_COPY_PASSWORD),
                        ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy password") }
                    }
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        StrengthMeter(entry.password, showSuggestions = true)
                    }
                }

                if (reusedByCount > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.DUPLICATE_WARNING),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Reused password", style = MaterialTheme.typography.titleSmall)
                                val noun = if (reusedByCount == 1) "entry" else "entries"
                                Text(
                                    "This password is used by $reusedByCount other $noun",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                OutlinedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    DetailRow(icon = Icons.AutoMirrored.Outlined.Notes, label = "Notes", value = entry.notes.ifBlank { "No notes" })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    DetailRow(icon = Icons.Outlined.Category, label = "Category", value = entry.category)
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    DetailRow(icon = Icons.Outlined.Schedule, label = "Last updated", value = formatDate(entry.updatedAtEpochMs))
                }
                Spacer(Modifier.height(72.dp)) // room for snackbars
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueTag: String? = null,
    monospace: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                modifier = if (valueTag != null) Modifier.testTag(valueTag) else Modifier,
            )
        }
        if (trailing != null) Row { trailing() } else Spacer(Modifier.width(12.dp))
    }
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
