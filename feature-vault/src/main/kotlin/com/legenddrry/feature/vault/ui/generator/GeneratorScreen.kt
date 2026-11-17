// OWNER: worker "generator-ui".
package com.legenddrry.feature.vault.ui.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legenddrry.feature.vault.generator.GeneratorOptions
import com.legenddrry.feature.vault.ui.common.VaultTestTags
import com.legenddrry.feature.vault.ui.components.StrengthMeter
import kotlin.math.roundToInt

/** Stateless generator screen content. Options are the single source of truth (switches snap back). */
@Composable
fun GeneratorContent(
    password: String,
    options: GeneratorOptions,
    onOptionsChange: (GeneratorOptions) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PasswordHeroCard(password, onRegenerate, onCopy, onSave)
        LengthCard(options, onOptionsChange)
        CharacterSetCard(options, onOptionsChange)
    }
}

@Composable
private fun PasswordHeroCard(
    password: String,
    onRegenerate: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Generated password",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = colorizedPassword(password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag(VaultTestTags.GENERATOR_PASSWORD),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StrengthMeter(password, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRegenerate,
                    modifier = Modifier.weight(1f).testTag(VaultTestTags.GENERATOR_REGENERATE),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Regenerate password", Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New")
                }
                FilledTonalButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f).testTag(VaultTestTags.GENERATOR_COPY),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy password", Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
            }
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.GENERATOR_SAVE),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Save to vault", Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save to vault")
            }
        }
    }
}

@Composable
private fun colorizedPassword(password: String): AnnotatedString {
    val digitColor = MaterialTheme.colorScheme.primary
    val symbolColor = MaterialTheme.colorScheme.tertiary
    return remember(password, digitColor, symbolColor) {
        buildAnnotatedString {
            password.forEach { c ->
                val color: Color? = when {
                    c.isDigit() -> digitColor
                    !c.isLetterOrDigit() -> symbolColor
                    else -> null
                }
                if (color != null) withStyle(SpanStyle(color = color)) { append(c) } else append(c)
            }
        }
    }
}

@Composable
private fun LengthCard(options: GeneratorOptions, onOptionsChange: (GeneratorOptions) -> Unit) {
    val min = GeneratorOptions.MIN_LENGTH
    val max = GeneratorOptions.MAX_LENGTH
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Length: ${options.length}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag(VaultTestTags.GENERATOR_LENGTH_LABEL),
                )
                IconButton(
                    onClick = { onOptionsChange(options.copy(length = (options.length - 1).coerceAtLeast(min))) },
                    enabled = options.length > min,
                ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease length") }
                IconButton(
                    onClick = { onOptionsChange(options.copy(length = (options.length + 1).coerceAtMost(max))) },
                    enabled = options.length < max,
                ) { Icon(Icons.Filled.Add, contentDescription = "Increase length") }
            }
            Slider(
                value = options.length.toFloat(),
                onValueChange = {
                    val newLength = it.roundToInt().coerceIn(min, max)
                    if (newLength != options.length) onOptionsChange(options.copy(length = newLength))
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = max - min - 1,
                modifier = Modifier.fillMaxWidth().testTag(VaultTestTags.GENERATOR_LENGTH_SLIDER),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("$min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("$max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CharacterSetCard(options: GeneratorOptions, onOptionsChange: (GeneratorOptions) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Characters",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ToggleRow("Uppercase (A-Z)", options.uppercase, VaultTestTags.GENERATOR_TOGGLE_UPPER) {
                onOptionsChange(options.copy(uppercase = it))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ToggleRow("Lowercase (a-z)", options.lowercase, VaultTestTags.GENERATOR_TOGGLE_LOWER) {
                onOptionsChange(options.copy(lowercase = it))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ToggleRow("Numbers (0-9)", options.digits, VaultTestTags.GENERATOR_TOGGLE_DIGITS) {
                onOptionsChange(options.copy(digits = it))
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ToggleRow("Symbols (!@#)", options.symbols, VaultTestTags.GENERATOR_TOGGLE_SYMBOLS) {
                onOptionsChange(options.copy(symbols = it))
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Always enabled so the VM's "last category" rejection + message can surface; checked mirrors state.
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(tag))
    }
}
