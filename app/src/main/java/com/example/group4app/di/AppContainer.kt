package com.example.group4app.di

import android.content.Context
import com.example.group4app.integration.FileAliasesRepository
import com.example.group4app.integration.PreferencesSettingsRepository
import com.example.group4app.integration.VaultSecuritySummaryProvider
import com.legenddrry.core.CoreContainer
import com.legenddrry.core.VaultRepository
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.feature.tools.ToolsDependencies
import com.legenddrry.feature.tools.approvals.InMemoryApprovalsRepository
import com.legenddrry.feature.tools.contracts.AliasesRepository
import com.legenddrry.feature.tools.contracts.ApprovalsRepository
import com.legenddrry.feature.tools.contracts.SecuritySummaryProvider
import com.legenddrry.feature.tools.contracts.SettingsRepository
import java.io.File

/**
 * Single application-scoped object graph. Every screen gets the SAME repository instances:
 * the vault list, the generator and the security dashboard all share [vaultRepository].
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val core: CoreContainer = CoreContainer.create(appContext)
    val auth: AuthManager get() = core.auth
    val vaultRepository: VaultRepository get() = core.vaultRepository

    val settings: SettingsRepository = PreferencesSettingsRepository(appContext)
    val aliases: AliasesRepository = FileAliasesRepository(File(appContext.filesDir, "aliases.json"))
    /** Demo only: no real login-approval backend exists, so requests live in memory. */
    val approvals: ApprovalsRepository = InMemoryApprovalsRepository()
    val security: SecuritySummaryProvider = VaultSecuritySummaryProvider(vaultRepository)

    val tools: ToolsDependencies = ToolsDependencies(
        approvals = approvals,
        aliases = aliases,
        security = security,
        settings = settings,
    )
}
