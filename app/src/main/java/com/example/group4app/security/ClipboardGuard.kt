package com.example.group4app.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.legenddrry.feature.tools.contracts.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Clears passwords from the system clipboard after `clipboardClearSeconds` (from Settings).
 *
 * A clip counts as a password when its description carries the extra
 * `android.content.extra.IS_SENSITIVE == true` (how the vault and generator mark password copies).
 * Each new clip cancels the previous pending clear; a clear is skipped when a newer clip arrived
 * in the meantime. Clipboard access can be refused by the OS: SecurityExceptions are swallowed.
 *
 * [scope] must run on the main thread (the listener fires there); keep one instance per process.
 */
class ClipboardGuard(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val clipboard: ClipboardManager? =
        context.applicationContext.getSystemService(ClipboardManager::class.java)

    private var started = false
    private var pendingClear: Job? = null
    /** Increments on every clipboard change so a stale clear never wipes a newer clip. */
    private var generation = 0L

    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onPrimaryClipChanged() }

    fun start() {
        if (started) return
        val cm = clipboard ?: return
        try {
            cm.addPrimaryClipChangedListener(listener)
            started = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard listener refused", e)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        pendingClear?.cancel()
        pendingClear = null
        try {
            clipboard?.removePrimaryClipChangedListener(listener)
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard listener removal refused", e)
        }
    }

    private fun onPrimaryClipChanged() {
        val myGeneration = ++generation
        pendingClear?.cancel()
        pendingClear = null

        val description = readDescription() ?: return
        if (!description.isSensitive()) return
        val copiedAt = description.timestamp

        val delayMs = settings.preferences.value.clipboardClearSeconds.coerceAtLeast(1) * 1_000L
        pendingClear = scope.launch {
            delay(delayMs)
            if (myGeneration != generation) return@launch
            clearIfStillOurs(copiedAt)
        }
    }

    private fun clearIfStillOurs(copiedAt: Long) {
        val cm = clipboard ?: return
        // While the app is in the background Android 10+ hides the clip (null description).
        // In that case clear anyway: leaving a password behind is the worse failure.
        val current = readDescription()
        if (current != null && (current.timestamp != copiedAt || !current.isSensitive())) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Clipboard clear refused", e)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Clipboard clear failed", e)
        }
    }

    private fun readDescription(): ClipDescription? = try {
        clipboard?.primaryClipDescription
    } catch (e: SecurityException) {
        null
    }

    private fun ClipDescription.isSensitive(): Boolean =
        extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true

    private companion object {
        const val TAG = "ClipboardGuard"
        /** ClipDescription.EXTRA_IS_SENSITIVE (API 33 constant; the literal works on every API). */
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
