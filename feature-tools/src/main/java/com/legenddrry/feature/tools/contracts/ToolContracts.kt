package com.legenddrry.feature.tools.contracts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ApprovalStatus { PENDING, APPROVED, DENIED, EXPIRED }

data class LoginRequest(
    val id: String,
    val deviceName: String,
    val approximateLocation: String,
    val requestedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val status: ApprovalStatus = ApprovalStatus.PENDING,
    val isDemo: Boolean = true,
    val resolvedAtEpochMs: Long? = null,
)

/** All mutations must publish a new immutable list. Invalid transitions must fail. */
interface ApprovalsRepository {
    val requests: StateFlow<List<LoginRequest>>
    suspend fun createDemoRequest(): LoginRequest
    suspend fun approve(id: String)
    suspend fun deny(id: String)
    /** Marks overdue pending requests EXPIRED; invoked on collection and periodically by the UI. */
    suspend fun expirePendingRequests()
}

data class EmailAlias(
    val id: String,
    val serviceName: String,
    val aliasAddress: String,
    val createdAtEpochMs: Long,
    val isActive: Boolean,
)

interface AliasesRepository {
    val aliases: StateFlow<List<EmailAlias>>
    suspend fun create(serviceName: String): EmailAlias
    suspend fun setActive(id: String, active: Boolean)
    suspend fun delete(id: String)
}

data class SecuritySummary(
    val totalPasswords: Int,
    val weakPasswords: Int,
    /** Number of password entries involved in reuse, not number of duplicate groups. */
    val reusedPasswords: Int,
) {
    init {
        require(totalPasswords >= 0)
        require(weakPasswords in 0..totalPasswords)
        require(reusedPasswords in 0..totalPasswords)
    }
}

interface SecuritySummaryProvider {
    val summary: Flow<SecuritySummary>
    /** True only for simulated statistics. Production adapters should retain the default false. */
    val isDemo: Boolean get() = false
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ToolPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val autoLockMinutes: Int = 5,
    val clipboardClearSeconds: Int = 30,
    val showPasswords: Boolean = false,
) {
    init {
        require(autoLockMinutes > 0)
        require(clipboardClearSeconds > 0)
    }
}

/** Preferences only: host app enforces auto-lock, clipboard clearing and password visibility. */
interface SettingsRepository {
    val preferences: StateFlow<ToolPreferences>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAutoLockMinutes(minutes: Int)
    suspend fun setClipboardClearSeconds(seconds: Int)
    suspend fun setShowPasswords(show: Boolean)
}
