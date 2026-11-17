package com.legenddrry.feature.vault.ui.entries

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.legenddrry.core.VaultEntry
import com.legenddrry.feature.vault.ui.common.VaultTestTags

/** Title, search field and category chips shown above the entries list. */
@Composable
internal fun EntriesHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Text(
            "Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag(VaultTestTags.SEARCH_FIELD),
            placeholder = { Text("Search title, username, website") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") },
                modifier = Modifier.testTag(VaultTestTags.CATEGORY_CHIP_ALL),
            )
            categories.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(if (selectedCategory == category) null else category) },
                    label = { Text(category) },
                    modifier = Modifier.testTag(VaultTestTags.CATEGORY_CHIP_PREFIX + category),
                )
            }
        }
    }
}

@Composable
internal fun LetterAvatar(text: String, size: Dp = 44.dp) {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[Math.floorMod(text.lowercase().hashCode(), palette.size)]
    val letter = text.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = fg,
            style = if (size > 50.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun CategoryLabel(category: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            category,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun EntryRow(entry: VaultEntry, isDuplicate: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VaultTestTags.ENTRY_ROW_PREFIX + entry.id),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LetterAvatar(entry.title)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = entry.username.ifBlank { entry.website }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.username.isNotBlank() && entry.website.isNotBlank()) {
                    Text(
                        entry.website,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                CategoryLabel(entry.category)
                if (isDuplicate) {
                    Spacer(Modifier.height(6.dp))
                    DuplicateBadge(Modifier.testTag(VaultTestTags.ENTRY_DUPLICATE_BADGE_PREFIX + entry.id))
                }
            }
        }
    }
}

@Composable
private fun DuplicateBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = "Password reused" },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reused", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CenteredMessage(
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    body: String?,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
internal fun LoadingState() {
    Box(Modifier.fillMaxSize().testTag(VaultTestTags.LOADING_STATE), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit) {
    CenteredMessage(
        tag = VaultTestTags.ERROR_STATE,
        icon = Icons.Outlined.ErrorOutline,
        iconTint = MaterialTheme.colorScheme.error,
        title = "Couldn't load your vault",
        body = message,
    ) {
        Button(onClick = onRetry, modifier = Modifier.testTag(VaultTestTags.RETRY_BUTTON)) { Text("Retry") }
    }
}

@Composable
internal fun EmptyVaultState(onAdd: () -> Unit) {
    CenteredMessage(
        tag = VaultTestTags.EMPTY_STATE,
        icon = Icons.Outlined.Lock,
        title = "Your vault is empty",
        body = "Save your first password and it will show up here.",
    ) {
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add your first password")
        }
    }
}

@Composable
internal fun NoResultsState(onClearFilters: () -> Unit) {
    CenteredMessage(
        tag = VaultTestTags.NO_RESULTS_STATE,
        icon = Icons.Outlined.SearchOff,
        title = "No matches",
        body = "Try a different search or category.",
    ) {
        OutlinedButton(onClick = onClearFilters) { Text("Clear filters") }
    }
}
