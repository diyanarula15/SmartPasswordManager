package com.legenddrry.core.vault

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.VaultRepository
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.core.crypto.KeyWrapper
import com.legenddrry.core.crypto.VaultCrypto
import com.legenddrry.core.crypto.wipe
import com.legenddrry.core.storage.VaultFiles
import com.legenddrry.core.storage.VaultMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.BadPaddingException

/**
 * Encrypted, file-backed vault: implements both [AuthManager] and [VaultRepository] so the
 * in-memory data key and the decrypted entry cache live in exactly one place.
 *
 * Concurrency: setUp/unlock/add/update/delete are serialized by [mutex]. [lock] is
 * synchronous and only touches the session under [sessionLock]; a mutation that is already
 * running keeps its own copy of the key, finishes its (correct) write, and does not
 * re-publish entries once the vault has been locked.
 */
internal class EncryptedVault internal constructor(
    dir: File,
    keyWrapper: KeyWrapper,
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cpuDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : AuthManager, VaultRepository {

    init {
        require(iterations > 0) { "iterations must be positive" }
    }

    private val files = VaultFiles(dir, keyWrapper)
    private val mutex = Mutex()
    private val sessionLock = Any()

    // Guarded by sessionLock.
    private var dek: ByteArray? = null
    private var cache: List<VaultEntry> = emptyList()
    private var generation = 0L

    // Guarded by mutex (writes); volatile for lockoutRemainingSeconds().
    private var consecutiveFailures = 0
    @Volatile private var lockoutUntilMs = 0L

    private val _state = MutableStateFlow(AuthState.LOADING)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _entries = MutableSharedFlow<List<VaultEntry>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(emptyList()) }
    override val entries: Flow<List<VaultEntry>> = _entries.asSharedFlow()

    init {
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            val initial = try {
                if (files.metaExists()) AuthState.LOCKED else AuthState.NEEDS_SETUP
            } catch (_: Exception) {
                AuthState.LOCKED // unlock() will report ERROR; never treat as "no vault".
            }
            _state.compareAndSet(AuthState.LOADING, initial)
        }
    }

    // ---------------------------------------------------------------- AuthManager

    override suspend fun setUp(masterPassword: CharArray): AuthResult = mutex.withLock {
        if (masterPassword.size < AuthManager.MIN_MASTER_PASSWORD_LENGTH ||
            masterPassword.all { it.isWhitespace() }
        ) {
            return AuthResult.PASSWORD_TOO_SHORT
        }
        val exists = try {
            withContext(ioDispatcher) { files.metaExists() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AuthResult.ERROR
        }
        if (exists) {
            _state.compareAndSet(AuthState.LOADING, AuthState.LOCKED)
            _state.compareAndSet(AuthState.NEEDS_SETUP, AuthState.LOCKED)
            return AuthResult.ALREADY_SET_UP
        }

        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val newDek = VaultCrypto.randomBytes(VaultCrypto.KEY_BYTES)
        var success = false
        try {
            val meta = withContext(cpuDispatcher) {
                val kek = VaultCrypto.deriveKek(masterPassword, salt, iterations)
                try {
                    val iv = VaultCrypto.randomBytes(VaultCrypto.IV_BYTES)
                    val wrapped = VaultCrypto.encrypt(kek, iv, newDek, VaultMeta.dekAad(iterations, salt))
                    VaultMeta(iterations, salt, iv, wrapped)
                } finally {
                    kek.wipe()
                }
            }
            withContext(ioDispatcher) {
                // Data file first: meta.bin existing is the marker of a completed setup.
                files.writeVault(newDek, emptyList())
                files.writeMeta(meta)
            }
            success = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Roll back a half-finished setup (no meta.bin means no vault existed yet).
            runCatching { if (!files.metaFile.exists()) files.vaultFile.delete() }
            return AuthResult.ERROR
        } finally {
            if (!success) newDek.wipe()
        }

        consecutiveFailures = 0
        lockoutUntilMs = 0L
        openSession(newDek, emptyList())
        AuthResult.SUCCESS
    }

    override suspend fun unlock(masterPassword: CharArray): AuthResult = mutex.withLock {
        if (lockoutRemainingMs() > 0) return AuthResult.TOO_MANY_ATTEMPTS

        val meta = try {
            withContext(ioDispatcher) {
                if (!files.metaExists()) null else files.readMeta()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AuthResult.ERROR
        }
        if (meta == null) {
            _state.compareAndSet(AuthState.LOADING, AuthState.NEEDS_SETUP)
            return AuthResult.NOT_SET_UP
        }

        val unwrapped: ByteArray? = try {
            withContext(cpuDispatcher) {
                val kek = VaultCrypto.deriveKek(masterPassword, meta.salt, meta.iterations)
                try {
                    VaultCrypto.decrypt(kek, meta.dekIv, meta.wrappedDek, 0, meta.wrappedDek.size, meta.dekAad())
                } catch (_: BadPaddingException) {
                    null // GCM tag mismatch: wrong master password.
                } finally {
                    kek.wipe()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return AuthResult.ERROR
        }

        if (unwrapped == null) {
            registerFailure()
            return AuthResult.WRONG_PASSWORD
        }
        if (unwrapped.size != VaultCrypto.KEY_BYTES) {
            unwrapped.wipe()
            return AuthResult.ERROR
        }

        val loaded = try {
            withContext(ioDispatcher) { files.readVault(unwrapped) }
        } catch (e: CancellationException) {
            unwrapped.wipe()
            throw e
        } catch (_: Exception) {
            unwrapped.wipe()
            return AuthResult.ERROR // corrupt/missing data file: never reset, never unlock.
        }

        consecutiveFailures = 0
        lockoutUntilMs = 0L
        openSession(unwrapped, loaded)
        AuthResult.SUCCESS
    }

    override fun lock() {
        synchronized(sessionLock) {
            dek.wipe()
            dek = null
            cache = emptyList()
            generation++
            _entries.tryEmit(emptyList())
            _state.compareAndSet(AuthState.UNLOCKED, AuthState.LOCKED)
        }
    }

    override fun lockoutRemainingSeconds(): Int {
        val ms = lockoutRemainingMs()
        return if (ms <= 0) 0 else ((ms + 999) / 1000).toInt()
    }

    private fun lockoutRemainingMs(): Long {
        val until = lockoutUntilMs
        return if (until == 0L) 0 else (until - clockMs()).coerceAtLeast(0)
    }

    private fun registerFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= MAX_FAILED_ATTEMPTS) {
            consecutiveFailures = 0
            lockoutUntilMs = clockMs() + LOCKOUT_MS
        }
    }

    private fun openSession(newDek: ByteArray, loaded: List<VaultEntry>) {
        synchronized(sessionLock) {
            if (dek !== newDek) dek.wipe()
            dek = newDek
            cache = loaded
            generation++
            _entries.tryEmit(loaded)
            _state.value = AuthState.UNLOCKED
        }
    }

    // ---------------------------------------------------------------- VaultRepository

    override suspend fun add(entry: VaultEntry) = mutate { current ->
        require(current.none { it.id == entry.id }) { "Entry with this id already exists" }
        current + entry
    }

    override suspend fun update(entry: VaultEntry) = mutate { current ->
        val index = current.indexOfFirst { it.id == entry.id }
        require(index >= 0) { "No entry with this id" }
        current.toMutableList().also { it[index] = entry }
    }

    override suspend fun delete(id: String) = mutate { current ->
        if (current.none { it.id == id }) null else current.filterNot { it.id == id }
    }

    /** [transform] returns the new list, or null for a no-op. */
    private suspend fun mutate(transform: (List<VaultEntry>) -> List<VaultEntry>?) {
        checkUnlocked()
        mutex.withLock {
            var gen: Long
            var key: ByteArray
            var current: List<VaultEntry>
            synchronized(sessionLock) {
                val d = dek ?: throw IllegalStateException("Vault is locked")
                gen = generation
                key = d.copyOf() // private copy: lock() may wipe the session key concurrently.
                current = cache
            }
            try {
                val updated = transform(current) ?: return
                withContext(ioDispatcher) { files.writeVault(key, updated) } // persist BEFORE emitting
                synchronized(sessionLock) {
                    if (generation == gen && dek != null) {
                        cache = updated
                        _entries.tryEmit(updated)
                    }
                }
            } finally {
                key.wipe()
            }
        }
    }

    private fun checkUnlocked() {
        synchronized(sessionLock) { check(dek != null) { "Vault is locked" } }
    }

    companion object {
        /** PBKDF2-HMAC-SHA256 iterations for new vaults (stored per vault, so it can change). */
        const val DEFAULT_ITERATIONS = 310_000
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
    }
}
