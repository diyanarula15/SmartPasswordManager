// LOCKED API (owner: shell worker): `LegenDDRRYApp(container: AppContainer)`. SKELETON STUB.
package com.example.group4app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.group4app.di.AppContainer
import com.example.group4app.ui.auth.SetupScreen
import com.example.group4app.ui.auth.UnlockScreen
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.tools.ProvideTools
import com.legenddrry.feature.vault.EntriesRoute

/** Root composable: auth gate + the single global navigation shell. */
@Composable
fun LegenDDRRYApp(container: AppContainer) {
    val state by container.auth.state.collectAsStateWithLifecycle()
    ProvideTools(container.tools) {
        when (state) {
            AuthState.NEEDS_SETUP -> SetupScreen(container.auth)
            AuthState.UNLOCKED -> EntriesRoute(container.vaultRepository)
            else -> UnlockScreen(container.auth)
        }
    }
}
