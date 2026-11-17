package com.legenddrry.feature.tools.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.legenddrry.feature.tools.components.ToolsType
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

    ToolPage("Settings", "A few thoughtful choices for your everyday security.") {
        error?.let {
            Box(Modifier.testTag("settings_error")) {
                ToolFeedback(it, isError = true, onDismiss = { error = null })
            }
        }
        ToolSectionTitle("Appearance", "Choose the theme that feels comfortable.")
        ToolCard {
            Text("Theme", style = ToolsType.title)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    val selected = preferences.themeMode == mode
                    val container by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        tween(150), label = "themeSelection",
                    )
                    val contentColor by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        tween(150), label = "themeSelectionText",
                    )
                    val (label, icon) = when (mode) {
                        ThemeMode.SYSTEM -> "System default" to "system"
                        ThemeMode.LIGHT -> "Light mode" to "light"
                        ThemeMode.DARK -> "Dark mode" to "dark"
                    }
                    Column(
                        Modifier.weight(1f).heightIn(min = 88.dp)
                            .testTag("theme_${mode.name.lowercase()}")
                            .clip(RoundedCornerShape(14.dp)).background(container)
                            .selectable(selected, role = Role.RadioButton,
                                onClick = { update { repository.setThemeMode(mode) } })
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        ToolIcon(icon, tint = contentColor, filled = selected)
                        Text(label, style = ToolsType.caption, color = contentColor, textAlign = TextAlign.Center)
                    }
                }
            }
            Text("Your host application applies this appearance preference.", style = ToolsType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolSectionTitle("Security preferences")
        ToolNotice(
            "Preferences, ready for your app",
            "These preferences are saved here. Auto-lock, clipboard clearing and password visibility must be enforced by the host application.",
            icon = "tune",
        )
        ToolCard {
            PreferenceRow("Auto-lock preference", "${preferences.autoLockMinutes} minutes", "lock", "auto_lock_preference") { dialog = "auto-lock" }
            PreferenceRow("Clipboard clearing preference", "${preferences.clipboardClearSeconds} seconds", "copy", "clipboard_preference") { dialog = "clipboard" }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ToolIconTile("visibility")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Show passwords by default", style = ToolsType.title)
                    Text("Visibility preference for the host vault", style = ToolsType.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = preferences.showPasswords,
                    onCheckedChange = { update { repository.setShowPasswords(it) } },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("show_passwords_preference").semantics {
                        contentDescription = "Show passwords by default"
                    },
                )
            }
        }
        ToolSectionTitle("Application information")
        ToolCard {
            InformationRow("About LegenDDRRY", "A calmer place for your digital life", "info", "about_button") { dialog = "about" }
            InformationRow("Privacy and data storage", "Understand where your data lives", "shield", "privacy_button", privacy = true) { dialog = "privacy" }
            InformationRow("Demo feature limitations", "What this demonstration can do", "help", "limitations_button") { dialog = "demo" }
        }
    }

    when (dialog) {
        "auto-lock" -> DurationDialog(
            title = "Auto-lock preference", options = listOf(1, 5, 15, 30),
            selected = preferences.autoLockMinutes, unit = "minutes",
            onDismiss = { dialog = null },
            onSelect = { update { repository.setAutoLockMinutes(it) }; dialog = null },
        )
        "clipboard" -> DurationDialog(
            title = "Clipboard clearing preference", options = listOf(15, 30, 60, 120),
            selected = preferences.clipboardClearSeconds, unit = "seconds",
            onDismiss = { dialog = null },
            onSelect = { update { repository.setClipboardClearSeconds(it) }; dialog = null },
        )
        "about", "privacy", "demo" -> {
            val (title, body) = when (dialog) {
                "about" -> "About LegenDDRRY" to "LegenDDRRY helps students organize their digital security. These tools include local login approval demonstrations, email alias records, vault health summaries and appearance preferences."
                "privacy" -> "Privacy and data storage" to "Storage is supplied by the host application. The standalone demo stores records and preferences only in memory; they reset when its process ends. This feature module does not implement encryption or a password database. No network requests are made by its demo repositories."
                else -> "Demo feature limitations" to "Demo login requests are local simulations and do not authorize third-party logins. Demo aliases use reserved example addresses and cannot receive or forward email. Demonstration dashboard counts are simulated; no breach detection or external scanning is performed. Security preferences require host-app enforcement."
            }
            ToolDialog(
                title = title, onDismiss = { dialog = null },
                confirmButton = { ToolActionButton("Close", { dialog = null }, kind = ToolButtonKind.TEXT) },
            ) {
                ToolIconTile(if (dialog == "privacy") "shield" else if (dialog == "demo") "help" else "info")
                Text(body, style = ToolsType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreferenceRow(title: String, value: String, icon: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolIconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ToolsType.title)
            Text(value, style = ToolsType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolIcon("chevron")
    }
}

@Composable
private fun InformationRow(
    title: String, supporting: String, icon: String, tag: String,
    privacy: Boolean = false, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (privacy) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                ToolIcon(icon, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        } else ToolIconTile(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = ToolsType.title)
            Text(supporting, style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolIcon("chevron")
    }
}

@Composable
private fun DurationDialog(
    title: String, options: List<Int>, selected: Int, unit: String,
    onDismiss: () -> Unit, onSelect: (Int) -> Unit,
) {
    ToolDialog(
        title = title, onDismiss = onDismiss,
        confirmButton = { ToolActionButton("Cancel", onDismiss, kind = ToolButtonKind.TEXT) },
    ) {
        Text("Preference only; host-app enforcement is required.", style = ToolsType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (options + selected).distinct().sorted().forEach { duration ->
                val isSelected = selected == duration
                val container by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    tween(150), label = "durationSelection",
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(container)
                        .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(duration) })
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text("$duration $unit", style = ToolsType.label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
