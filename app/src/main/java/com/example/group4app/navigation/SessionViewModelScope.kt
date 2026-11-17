package com.example.group4app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Holds a ViewModelStore for ONE unlocked session. The feature modules create their
 * ViewModels (vault list, generator) with `viewModel()`, so without this they would be
 * activity-scoped and keep decrypted state (search results, the last generated password)
 * in memory after the vault is locked. The holder itself is activity-scoped so the session
 * survives configuration changes such as rotation.
 */
class SessionStoreHolder : ViewModel() {
    /** Stable per session, so recomposition doesn't change LocalViewModelStoreOwner's identity. */
    var owner: ViewModelStoreOwner = newOwner()
        private set

    private fun newOwner(): ViewModelStoreOwner {
        val store = ViewModelStore()
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }

    /** Destroys every ViewModel of the current session (calls onCleared) and starts a fresh store. */
    fun endSession() {
        owner.viewModelStore.clear()
        owner = newOwner()
    }

    override fun onCleared() {
        owner.viewModelStore.clear()
    }
}

/** Provides the session ViewModelStoreOwner to [content]. */
@Composable
fun SessionViewModelScope(holder: SessionStoreHolder, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalViewModelStoreOwner provides holder.owner, content = content)
}

@Composable
fun rememberSessionStoreHolder(): SessionStoreHolder = viewModel(key = "legenddrry-session-store")
