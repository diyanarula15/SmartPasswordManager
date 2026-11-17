package com.legenddrry.feature.vault

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Display options for the vault screens, provided by the host app (e.g. from its settings).
 *
 * @property showPasswordsByDefault when true, the entry detail and the editor password field
 *   start with the password revealed; the user's visibility toggle still works.
 */
@Immutable
data class VaultDisplayOptions(val showPasswordsByDefault: Boolean = false)

/** Defaults to [VaultDisplayOptions] with passwords hidden. */
val LocalVaultDisplayOptions = staticCompositionLocalOf { VaultDisplayOptions() }
