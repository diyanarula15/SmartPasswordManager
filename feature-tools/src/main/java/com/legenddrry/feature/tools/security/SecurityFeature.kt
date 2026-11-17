package com.legenddrry.feature.tools.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.components.EmptyToolState
import com.legenddrry.feature.tools.components.ToolCard
import com.legenddrry.feature.tools.components.ToolPage
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider

@Composable
internal fun SecurityFeature(provider: SecuritySummaryProvider) {
    var retry by remember(provider) { mutableIntStateOf(0) }
    val states = remember(provider, retry) { securityStates(provider) }
    val state by states.collectAsStateWithLifecycle(initialValue = SecurityUiState.Loading)

    ToolPage("Security dashboard", "Understand password health without displaying your passwords.") {
        if (provider.isDemo) {
            ToolCard {
                Text("Demo password statistics", style = MaterialTheme.typography.titleMedium)
                Text("Simulated counts for this local demonstration. Connect your vault provider to see your saved-password statistics.")
            }
        }
        when (val current = state) {
            SecurityUiState.Loading -> ToolCard {
                CircularProgressIndicator(Modifier.testTag("security_loading"))
                Text("Loading password statistics…")
            }
            is SecurityUiState.Error -> ToolCard {
                Text(current.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("security_error"))
                TextButton(onClick = { retry++ }) { Text("Retry") }
            }
            is SecurityUiState.Ready -> {
                val summary = current.summary
                ToolCard {
                    Text("Password overview", style = MaterialTheme.typography.titleLarge)
                    StatisticRow("Total saved passwords", summary.totalPasswords, "security_total")
                    StatisticRow("Weak passwords", summary.weakPasswords, "security_weak")
                    StatisticRow("Reused passwords", summary.reusedPasswords, "security_reused")
                    Text("Reused count means entries involved in reuse. Weak and reused entries can overlap.", style = MaterialTheme.typography.bodySmall)
                }
                if (summary.totalPasswords == 0) {
                    EmptyToolState("No saved passwords yet", "Add passwords in the vault. This dashboard updates automatically when your provider publishes new counts.")
                } else {
                    ToolCard {
                        Text("Password health", style = MaterialTheme.typography.titleLarge)
                        HealthMetric("Weak entries", summary.weakPasswords, summary.totalPasswords)
                        HealthMetric("Entries involved in reuse", summary.reusedPasswords, summary.totalPasswords)
                    }
                }
                ToolCard {
                    Text("Recommended next steps", style = MaterialTheme.typography.titleLarge)
                    securityRecommendations(summary).forEachIndexed { index, recommendation ->
                        Text("${index + 1}. $recommendation")
                    }
                }
            }
        }
        Text("No breach detection or external security scanning is performed by this dashboard.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatisticRow(label: String, value: Int, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun HealthMetric(label: String, affected: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label: $affected of $total")
        LinearProgressIndicator(
            progress = { affected.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = if (affected == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}
