// LOCKED API (owner: core worker). Keep `CoreContainer.create(context)`, `auth`, `vaultRepository`.
package com.legenddrry.core

import android.content.Context
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Application-scoped core services. Create ONCE (Application.onCreate).
 * [vaultRepository] emits an empty list while locked and throws IllegalStateException
 * from add/update/delete while locked.
 */
class CoreContainer private constructor(
    val auth: AuthManager,
    val vaultRepository: VaultRepository,
) {
    companion object {
        fun create(context: Context): CoreContainer {
            // SKELETON STUB (in-memory, NOT encrypted, NOT persisted). Replaced by the core worker.
            val auth = StubAuth()
            return CoreContainer(auth, StubVault(auth))
        }
    }
}

private class StubAuth : AuthManager {
    private var secret: String? = null
    private val _state = MutableStateFlow(AuthState.NEEDS_SETUP)
    override val state: StateFlow<AuthState> = _state.asStateFlow()
    override suspend fun setUp(masterPassword: CharArray): AuthResult {
        if (secret != null) return AuthResult.ALREADY_SET_UP
        if (masterPassword.size < AuthManager.MIN_MASTER_PASSWORD_LENGTH) return AuthResult.PASSWORD_TOO_SHORT
        secret = String(masterPassword); _state.value = AuthState.UNLOCKED; return AuthResult.SUCCESS
    }
    override suspend fun unlock(masterPassword: CharArray): AuthResult {
        val s = secret ?: return AuthResult.NOT_SET_UP
        if (s != String(masterPassword)) return AuthResult.WRONG_PASSWORD
        _state.value = AuthState.UNLOCKED; return AuthResult.SUCCESS
    }
    override fun lock() { if (secret != null) _state.value = AuthState.LOCKED }
}

private class StubVault(private val auth: StubAuth) : VaultRepository {
    private val data = MutableStateFlow<List<VaultEntry>>(emptyList())
    override val entries: Flow<List<VaultEntry>> =
        combine(auth.state, data) { s, list -> if (s == AuthState.UNLOCKED) list else emptyList() }
    private fun check() = check(auth.state.value == AuthState.UNLOCKED) { "Vault is locked" }
    override suspend fun add(entry: VaultEntry) { check(); data.update { it + entry } }
    override suspend fun update(entry: VaultEntry) { check(); data.update { l -> l.map { if (it.id == entry.id) entry else it } } }
    override suspend fun delete(id: String) { check(); data.update { l -> l.filterNot { it.id == id } } }
}
