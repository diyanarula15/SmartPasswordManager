package com.example.group4app.security

import android.content.Context
import com.example.group4app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-scoped security behaviours. They must outlive a single Activity instance (rotation,
 * "don't keep activities"), so they are memoised per [AppContainer] (one per process).
 */
class SecurityServices private constructor(context: Context, container: AppContainer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val autoLock = AutoLockController(container.auth, container.settings)
    val clipboardGuard = ClipboardGuard(context.applicationContext, container.settings, scope)

    companion object {
        private var instance: SecurityServices? = null
        private var owner: AppContainer? = null

        @Synchronized
        fun get(context: Context, container: AppContainer): SecurityServices {
            val existing = instance
            if (existing != null && owner === container) return existing
            existing?.clipboardGuard?.stop()
            return SecurityServices(context.applicationContext, container).also {
                instance = it
                owner = container
            }
        }
    }
}
