// LOCKED API (owner: core worker). Keep signatures.
package com.legenddrry.core.auth

import kotlinx.coroutines.flow.StateFlow

enum class AuthState {
    /** Reading stored setup state at startup. */
    LOADING,
    /** No master password yet: show first-time setup. */
    NEEDS_SETUP,
    /** Master password exists, vault key not in memory. */
    LOCKED,
    /** Vault key in memory; VaultRepository usable. */
    UNLOCKED,
}

enum class AuthResult {
    SUCCESS,
    WRONG_PASSWORD,
    /** Shorter than [AuthManager.MIN_MASTER_PASSWORD_LENGTH] (or blank). */
    PASSWORD_TOO_SHORT,
    ALREADY_SET_UP,
    NOT_SET_UP,
    /** Temporarily refused after repeated wrong attempts. */
    TOO_MANY_ATTEMPTS,
    ERROR,
}

interface AuthManager {
    val state: StateFlow<AuthState>

    /** First-time setup. On SUCCESS the vault is created and UNLOCKED. Caller may wipe [masterPassword] afterwards. */
    suspend fun setUp(masterPassword: CharArray): AuthResult

    /** On SUCCESS state becomes UNLOCKED. Wrong password never exposes data. */
    suspend fun unlock(masterPassword: CharArray): AuthResult

    /** Drops the in-memory vault key and cached entries; state becomes LOCKED. */
    fun lock()

    /** Seconds remaining before another unlock attempt is allowed (0 when allowed). */
    fun lockoutRemainingSeconds(): Int = 0

    companion object {
        const val MIN_MASTER_PASSWORD_LENGTH = 8
    }
}
