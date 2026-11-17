// LOCKED API (owner: shell worker): `LegenDDRRYApp(container: AppContainer)`.
package com.example.group4app.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.group4app.di.AppContainer
import com.example.group4app.ui.auth.SetupScreen
import com.example.group4app.ui.auth.UnlockScreen
import com.example.group4app.ui.shell.AppTestTags
import com.example.group4app.ui.shell.MainShell
import com.example.group4app.ui.theme.SmartPasswordManagerTheme
import com.legenddrry.core.auth.AuthState
import com.legenddrry.feature.tools.ProvideTools
import com.legenddrry.feature.vault.LocalVaultDisplayOptions
import com.legenddrry.feature.vault.VaultDisplayOptions
import com.legenddrry.feature.tools.contracts.ThemeMode

/**
 * Root composable: applies the app theme (Settings → Theme), provides the tools dependencies and
 * gates everything on the auth state. Only UNLOCKED shows the shell; any other state replaces it
 * entirely, so its saved navigation/UI state is discarded and back can never return to the vault.
 */
@Composable
fun LegenDDRRYApp(container: AppContainer) {
    val preferences by container.settings.preferences.collectAsStateWithLifecycle()
    val darkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    SystemBarAppearance(darkTheme)

    SmartPasswordManagerTheme(darkTheme = darkTheme) {
        ProvideTools(container.tools) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                val authState by container.auth.state.collectAsStateWithLifecycle()
                val session = rememberSessionStoreHolder()
                // Leaving UNLOCKED (lock, auto-lock) destroys all feature ViewModels of the session.
                LaunchedEffect(authState) {
                    if (authState != AuthState.UNLOCKED) session.endSession()
                }
                when (authState) {
                    AuthState.LOADING -> AuthLoading()
                    AuthState.NEEDS_SETUP -> SetupScreen(container.auth)
                    AuthState.LOCKED -> UnlockScreen(container.auth)
                    AuthState.UNLOCKED -> SessionViewModelScope(session) {
                        CompositionLocalProvider(
                            LocalVaultDisplayOptions provides VaultDisplayOptions(preferences.showPasswords),
                        ) { MainShell(container) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AppTestTags.AUTH_LOADING)
            .semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Status/navigation bar icons follow the APP theme (which may differ from the system one). */
@Composable
private fun SystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findActivity() as? ComponentActivity ?: return
    DisposableEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
        )
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
