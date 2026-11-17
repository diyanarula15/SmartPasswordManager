package com.legenddrry.feature.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * Source-compatible legacy helper. The application owns its MaterialTheme.
 * Observe SettingsRepository.preferences in the host theme; the verification app
 * supplies an isolated Sage & Clay preview theme. No app palette is installed here.
 */
@Deprecated("The host application owns MaterialTheme. Use ProvideTools and observe settings in the host theme.")
@Composable
fun LegenDDRRYToolsTheme(
    repository: SettingsRepository = LocalToolsDependencies.current.settings,
    content: @Composable () -> Unit,
) {
    // Keep the signature for existing callers; never override the host colour scheme.
    content()
}
