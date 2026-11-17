package com.legenddrry.feature.tools.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.components.ToolCard
import com.legenddrry.feature.tools.components.ToolPage
import com.legenddrry.feature.tools.contracts.SettingsRepository
import com.legenddrry.feature.tools.contracts.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsFeature(repository: SettingsRepository) {
    val preferences by repository.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    fun update(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Could not save your preference. Please try again."
            }
        }
    }

    ToolPage("Settings", "Make LegenDDRRY feel right for you.") {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("settings_error")) }
        ToolCard {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ThemeMode.SYSTEM -> "System default"
                        ThemeMode.LIGHT -> "Light mode"
                        ThemeMode.DARK -> "Dark mode"
                    }
                    Row(
                        Modifier.fillMaxWidth().testTag("theme_${mode.name.lowercase()}")
                            .selectable(
                                selected = preferences.themeMode == mode,
                                role = Role.RadioButton,
                                onClick = { update { repository.setThemeMode(mode) } },
                            ).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = preferences.themeMode == mode, onClick = null)
                        Text(label)
                    }
                }
            }
        }
        ToolCard {
            Text("Security preferences", style = MaterialTheme.typography.titleLarge)
            Text(
                "These preferences are saved here. Auto-lock, clipboard clearing and password visibility must be enforced by the host application.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PreferenceRow("Auto-lock preference", "${preferences.autoLockMinutes} minutes", "auto_lock_preference") { dialog = "auto-lock" }
            PreferenceRow("Clipboard clearing preference", "${preferences.clipboardClearSeconds} seconds", "clipboard_preference") { dialog = "clipboard" }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show passwords by default", style = MaterialTheme.typography.titleMedium)
                    Text("Visibility preference for the host vault", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = preferences.showPasswords,
                    onCheckedChange = { update { repository.setShowPasswords(it) } },
                    modifier = Modifier.testTag("show_passwords_preference").semantics {
                        contentDescription = "Show passwords by default"
                    },
                )
            }
        }
        ToolCard {
            Text("Application information", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { dialog = "about" }, modifier = Modifier.testTag("about_button")) { Text("About LegenDDRRY") }
            TextButton(onClick = { dialog = "privacy" }, modifier = Modifier.testTag("privacy_button")) { Text("Privacy and data storage") }
            TextButton(onClick = { dialog = "demo" }, modifier = Modifier.testTag("limitations_button")) { Text("Demo feature limitations") }
        }
    }

    when (dialog) {
        "auto-lock" -> DurationDialog(
            title = "Auto-lock preference",
            options = listOf(1, 5, 15, 30),
            selected = preferences.autoLockMinutes,
            unit = "minutes",
            onDismiss = { dialog = null },
            onSelect = { update { repository.setAutoLockMinutes(it) }; dialog = null },
        )
        "clipboard" -> DurationDialog(
            title = "Clipboard clearing preference",
            options = listOf(15, 30, 60, 120),
            selected = preferences.clipboardClearSeconds,
            unit = "seconds",
            onDismiss = { dialog = null },
            onSelect = { update { repository.setClipboardClearSeconds(it) }; dialog = null },
        )
        "about", "privacy", "demo" -> {
            val (title, body) = when (dialog) {
                "about" -> "About LegenDDRRY" to "LegenDDRRY helps students organize their digital security. These tools include local login approval demonstrations, email alias records, vault health summaries and appearance preferences."
                "privacy" -> "Privacy and data storage" to "Storage is supplied by the host application. The standalone demo stores records and preferences only in memory; they reset when its process ends. This feature module does not implement encryption or a password database. No network requests are made by its demo repositories."
                else -> "Demo feature limitations" to "Demo login requests are local simulations and do not authorize third-party logins. Demo aliases use reserved example addresses and cannot receive or forward email. Demonstration dashboard counts are simulated; no breach detection or external scanning is performed. Security preferences require host-app enforcement."
            }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(title) },
                text = { Text(body, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
            )
        }
    }
}

@Composable
private fun PreferenceRow(title: String, value: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DurationDialog(
    title: String,
    options: List<Int>,
    selected: Int,
    unit: String,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                Text("Preference only; host-app enforcement is required.")
                (options + selected).distinct().sorted().forEach { duration ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = selected == duration,
                            role = Role.RadioButton,
                            onClick = { onSelect(duration) },
                        ).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(selected = selected == duration, onClick = null)
                        Text("$duration $unit")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
