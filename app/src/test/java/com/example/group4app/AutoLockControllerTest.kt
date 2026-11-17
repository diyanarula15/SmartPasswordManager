package com.example.group4app

import com.example.group4app.security.AutoLockController
import com.legenddrry.core.auth.AuthManager
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.tools.contracts.SettingsRepository
import com.legenddrry.feature.tools.contracts.ThemeMode
import com.legenddrry.feature.tools.contracts.ToolPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockControllerTest {

    private class FakeClock(var now: Long = 1_000_000L) {
        fun advanceMinutes(minutes: Double) { now += (minutes * 60_000).toLong() }
    }

    private class FakeAuth(initial: AuthState = AuthState.UNLOCKED) : AuthManager {
        val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<AuthState> = mutableState
        var lockCalls = 0
        override suspend fun setUp(masterPassword: CharArray) = AuthResult.SUCCESS
        override suspend fun unlock(masterPassword: CharArray): AuthResult {
            mutableState.value = AuthState.UNLOCKED
            return AuthResult.SUCCESS
        }
        override fun lock() {
            lockCalls++
            mutableState.value = AuthState.LOCKED
        }
    }

    private class FakeSettings(minutes: Int) : SettingsRepository {
        private val flow = MutableStateFlow(ToolPreferences(autoLockMinutes = minutes))
        override val preferences: StateFlow<ToolPreferences> = flow
        override suspend fun setThemeMode(mode: ThemeMode) = flow.update { it.copy(themeMode = mode) }
        override suspend fun setAutoLockMinutes(minutes: Int) = flow.update { it.copy(autoLockMinutes = minutes) }
        override suspend fun setClipboardClearSeconds(seconds: Int) =
            flow.update { it.copy(clipboardClearSeconds = seconds) }
        override suspend fun setShowPasswords(show: Boolean) = flow.update { it.copy(showPasswords = show) }
    }

    private val clock = FakeClock()
    private val auth = FakeAuth()
    private val settings = FakeSettings(minutes = 5)
    private val controller = AutoLockController(auth, settings) { clock.now }

    @Test
    fun locksWhenBackgroundedForAtLeastTheTimeout() {
        controller.onAppBackgrounded()
        clock.advanceMinutes(5.0)
        assertTrue(controller.onAppForegrounded())
        assertEquals(AuthState.LOCKED, auth.state.value)
        assertEquals(1, auth.lockCalls)
    }

    @Test
    fun staysUnlockedWhenBackgroundedForLessThanTheTimeout() {
        controller.onAppBackgrounded()
        clock.advanceMinutes(4.99)
        assertFalse(controller.onAppForegrounded())
        assertEquals(AuthState.UNLOCKED, auth.state.value)
        assertEquals(0, auth.lockCalls)
    }

    @Test
    fun usesTheCurrentSettingAtForegroundTime() = kotlinx.coroutines.test.runTest {
        controller.onAppBackgrounded()
        settings.setAutoLockMinutes(1)
        clock.advanceMinutes(1.0)
        assertTrue(controller.onAppForegrounded())
    }

    @Test
    fun foregroundWithoutPriorBackgroundDoesNothing() {
        clock.advanceMinutes(60.0)
        assertFalse(controller.onAppForegrounded())
        assertEquals(0, auth.lockCalls)
    }

    @Test
    fun eachBackgroundPeriodIsMeasuredSeparately() {
        controller.onAppBackgrounded()
        clock.advanceMinutes(3.0)
        assertFalse(controller.onAppForegrounded())
        // Time spent in the foreground does not count.
        clock.advanceMinutes(10.0)
        controller.onAppBackgrounded()
        clock.advanceMinutes(3.0)
        assertFalse(controller.onAppForegrounded())
        assertEquals(AuthState.UNLOCKED, auth.state.value)
    }

    @Test
    fun doesNotTrackWhenAlreadyLockedAtBackgroundTime() {
        auth.mutableState.value = AuthState.LOCKED
        controller.onAppBackgrounded()
        auth.mutableState.value = AuthState.UNLOCKED // e.g. unlocked again before returning
        clock.advanceMinutes(30.0)
        assertFalse(controller.onAppForegrounded())
        assertEquals(0, auth.lockCalls)
    }

    @Test
    fun doesNotCallLockIfAlreadyLockedOnReturn() {
        controller.onAppBackgrounded()
        auth.lock() // locked manually meanwhile
        clock.advanceMinutes(30.0)
        assertFalse(controller.onAppForegrounded())
        assertEquals(1, auth.lockCalls)
    }

    @Test
    fun ignoresNeedsSetupState() {
        auth.mutableState.value = AuthState.NEEDS_SETUP
        controller.onAppBackgrounded()
        clock.advanceMinutes(30.0)
        assertFalse(controller.onAppForegrounded())
        assertEquals(AuthState.NEEDS_SETUP, auth.state.value)
    }

    @Test
    fun clockGoingBackwardsFailsSafeAndLocks() {
        controller.onAppBackgrounded()
        clock.now -= 1_000
        assertTrue(controller.onAppForegrounded())
        assertEquals(AuthState.LOCKED, auth.state.value)
    }
}
