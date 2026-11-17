// OWNER: worker "logic". LOCKED API — keep signature. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.legenddrry.feature.vault.strength.PasswordStrengthEvaluator
import com.legenddrry.feature.vault.ui.common.VaultTestTags

/** Coloured bar + label (VaultTestTags.STRENGTH_METER / STRENGTH_LABEL). */
@Composable
fun StrengthMeter(password: String, modifier: Modifier = Modifier, showSuggestions: Boolean = false) {
    val strength = remember(password) { PasswordStrengthEvaluator.evaluate(password) }
    Column(modifier.testTag(VaultTestTags.STRENGTH_METER)) {
        LinearProgressIndicator(progress = { strength.fraction }, modifier = Modifier.fillMaxWidth())
        Text(strength.level.label, modifier = Modifier.testTag(VaultTestTags.STRENGTH_LABEL))
    }
}
