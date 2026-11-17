package com.legenddrry.feature.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.legenddrry.feature.vault.strength.PasswordStrengthEvaluator
import com.legenddrry.feature.vault.strength.StrengthLevel
import com.legenddrry.feature.vault.ui.common.VaultTestTags

/** Mid-saturation tones that keep contrast on both light and dark surfaces. */
private fun StrengthLevel.color(): Color = when (this) {
    StrengthLevel.VERY_WEAK -> Color(0xFFE53935) // red
    StrengthLevel.WEAK -> Color(0xFFFB8C00) // orange
    StrengthLevel.FAIR -> Color(0xFFFFB300) // amber
    StrengthLevel.STRONG -> Color(0xFF43A047) // green
    StrengthLevel.VERY_STRONG -> Color(0xFF2E7D32) // dark green
}

/** Coloured bar + label (VaultTestTags.STRENGTH_METER / STRENGTH_LABEL). */
@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier, showSuggestions: Boolean = false) {
    val strength = remember(password) { PasswordStrengthEvaluator.evaluate(password) }
    val levelColor by animateColorAsState(strength.level.color(), label = "strengthColor")
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val filledSegments = strength.level.ordinal + 1

    Column(modifier.testTag(VaultTestTags.STRENGTH_METER)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Password strength: ${strength.level.label}" },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(StrengthLevel.entries.size) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                color = if (index < filledSegments) levelColor else trackColor,
                                shape = RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
            Text(
                text = strength.level.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(VaultTestTags.STRENGTH_LABEL),
            )
        }
        if (showSuggestions && strength.suggestions.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                strength.suggestions.take(2).forEach { tip ->
                    Text(
                        text = "• $tip",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
