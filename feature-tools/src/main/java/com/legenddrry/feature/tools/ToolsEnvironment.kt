package com.legenddrry.feature.tools

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.aliases.InMemoryAliasesRepository
import com.legenddrry.feature.tools.approvals.InMemoryApprovalsRepository
import com.legenddrry.feature.tools.contracts.*
import com.legenddrry.feature.tools.security.InMemorySecuritySummaryProvider
import com.legenddrry.feature.tools.settings.InMemorySettingsRepository

/** Create once at application/session scope. Replace interfaces with host repositories. */
data class ToolsDependencies(
    val approvals: ApprovalsRepository,
    val aliases: AliasesRepository,
    val security: SecuritySummaryProvider,
    val settings: SettingsRepository,
) {
    companion object {
        fun demo() = ToolsDependencies(
            InMemoryApprovalsRepository(), InMemoryAliasesRepository(),
            InMemorySecuritySummaryProvider(), InMemorySettingsRepository(),
        )
    }
}

private val defaultDemoDependencies by lazy { ToolsDependencies.demo() }
val LocalToolsDependencies = staticCompositionLocalOf { defaultDemoDependencies }

/** Injection does not impose a theme; host apps retain control of their own MaterialTheme. */
@Composable
fun ProvideTools(dependencies: ToolsDependencies, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalToolsDependencies provides dependencies, content = content)
}

/** Optional host theme wrapper. Observes appearance preferences immediately. */
@Composable
fun LegenDDRRYToolsTheme(
    repository: SettingsRepository = LocalToolsDependencies.current.settings,
    content: @Composable () -> Unit,
) {
    val preferences by repository.preferences.collectAsStateWithLifecycle()
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(
            primary = Color(0xFF3C568A), secondary = Color(0xFF565E71),
        ),
        content = content,
    )
}
