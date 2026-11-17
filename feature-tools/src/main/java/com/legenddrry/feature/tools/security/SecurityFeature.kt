package com.legenddrry.feature.tools.security

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.components.EmptyToolState
import com.legenddrry.feature.tools.components.ToolActionButton
import com.legenddrry.feature.tools.components.ToolButtonKind
import com.legenddrry.feature.tools.components.ToolCard
import com.legenddrry.feature.tools.components.ToolIcon
import com.legenddrry.feature.tools.components.ToolNotice
import com.legenddrry.feature.tools.components.ToolPage
import com.legenddrry.feature.tools.components.ToolSectionTitle
import com.legenddrry.feature.tools.components.ToolsType
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider

@Composable
internal fun SecurityFeature(provider: SecuritySummaryProvider) {
    var retry by remember(provider) { mutableIntStateOf(0) }
    val states = remember(provider, retry) { securityStates(provider) }
    val state by states.collectAsStateWithLifecycle(initialValue = SecurityUiState.Loading)

    ToolPage("Security dashboard", "A clear view of your password health, one step at a time.") {
        when (val current = state) {
            SecurityUiState.Loading -> ToolCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp).testTag("security_loading"), strokeWidth = 2.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Loading password statistics…", style = ToolsType.title)
                        Text("Waiting for your connected provider.", style = ToolsType.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is SecurityUiState.Error -> ToolCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToolIcon("error", tint = MaterialTheme.colorScheme.error)
                    Text(current.message, style = ToolsType.body, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f).testTag("security_error"))
                }
                ToolActionButton("Retry", { retry++ }, icon = "refresh", kind = ToolButtonKind.TONAL)
            }
            is SecurityUiState.Ready -> {
                val summary = current.summary
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Password overview", style = ToolsType.label)
                            ToolIcon("shield", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(summary.totalPasswords.toString(), style = ToolsType.display,
                            modifier = Modifier.testTag("security_total"))
                        Text("Total saved passwords", style = ToolsType.bodyLarge)
                        Text(if (provider.isDemo) "A local demonstration of your security overview."
                            else "Counts supplied by your connected vault provider.", style = ToolsType.caption)
                    }
                }
                if (provider.isDemo) {
                    ToolNotice("Demo password statistics",
                        "Simulated counts for this local demonstration. Connect your vault provider to see your saved-password statistics.", icon = "info")
                }
                ToolSectionTitle("Password health", "A little attention here can make a difference.")
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HealthMetric("Weak passwords", summary.weakPasswords, summary.totalPasswords, "security_weak", "key")
                            HealthMetric("Reused passwords", summary.reusedPasswords, summary.totalPasswords, "security_reused", "copy")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                HealthMetric("Weak passwords", summary.weakPasswords, summary.totalPasswords, "security_weak", "key")
                            }
                            Column(Modifier.weight(1f)) {
                                HealthMetric("Reused passwords", summary.reusedPasswords, summary.totalPasswords, "security_reused", "copy")
                            }
                        }
                    }
                }
                Text("Reused count means entries involved in reuse. Weak and reused entries can overlap.", style = ToolsType.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.totalPasswords == 0) {
                    EmptyToolState("No saved passwords yet", "Add passwords in the vault. This dashboard updates automatically when your provider publishes new counts.")
                }
                ToolSectionTitle("Recommended next steps")
                ToolCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToolIcon("tune", tint = MaterialTheme.colorScheme.secondary)
                        Text("Small steps, stronger habits", style = ToolsType.title)
                    }
                    securityRecommendations(summary).forEachIndexed { index, recommendation ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            Text("${index + 1}", style = ToolsType.overline, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp))
                            Text(recommendation, style = ToolsType.body, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (provider.isDemo && state !is SecurityUiState.Ready) {
            ToolNotice("Demo password statistics", "This provider supplies simulated counts for local demonstration.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            ToolIcon("info", size = 18.dp)
            Text("No breach detection or external security scanning is performed by this dashboard.", style = ToolsType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HealthMetric(label: String, affected: Int, total: Int, tag: String, icon: String) {
    val ratio by animateFloatAsState(
        if (total == 0) 0f else affected.toFloat() / total.toFloat(),
        tween(150), label = "passwordHealthMetric",
    )
    ToolCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(affected.toString(), style = ToolsType.headline, modifier = Modifier.testTag(tag))
            ToolIcon(icon, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
        }
        Text(label, style = ToolsType.title)
        Text("$affected of $total saved", style = ToolsType.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { ratio }, modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
