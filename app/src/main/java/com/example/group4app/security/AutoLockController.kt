package com.example.group4app.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.tools.contracts.SettingsRepository

/**
 * Locks the vault when the app comes back to the foreground after being in the background for at
 * least `autoLockMinutes` (from Settings).
 *
 * Observe the activity lifecycle with it ([onStop] = went to background, [onStart] = came back).
 * Keep ONE instance per process (see [SecurityServices]) so the background timestamp survives
 * activity re-creation. Process death needs no handling: the vault key only lives in memory.
 *
 * [clock] must be monotonic and keep counting while the device sleeps (default: elapsedRealtime).
 */
class AutoLockController(
    private val auth: AuthManager,
    private val settings: SettingsRepository,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : DefaultLifecycleObserver {

    /** Clock reading when the app left the foreground while unlocked; null when not tracking. */
    @Volatile
    private var backgroundedAt: Long? = null

    override fun onStop(owner: LifecycleOwner) = onAppBackgrounded()

    override fun onStart(owner: LifecycleOwner) {
        onAppForegrounded()
    }

    fun onAppBackgrounded() {
        backgroundedAt = if (auth.state.value == AuthState.UNLOCKED) clock() else null
    }

    /** @return true when this call locked the vault. */
    fun onAppForegrounded(): Boolean {
        val since = backgroundedAt ?: return false
        backgroundedAt = null
        if (auth.state.value != AuthState.UNLOCKED) return false
        val timeoutMs = settings.preferences.value.autoLockMinutes.coerceAtLeast(1) * 60_000L
        val elapsed = clock() - since
        // A negative elapsed time means the clock is not what we expect: fail safe and lock.
        if (elapsed >= timeoutMs || elapsed < 0) {
            auth.lock()
            return true
        }
        return false
    }
}
