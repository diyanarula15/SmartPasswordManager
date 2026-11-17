package com.legenddrry.feature.vault.ui.common

import kotlinx.coroutines.CancellationException

/** Clipboard access can be refused by Android or an OEM policy. Keep the screen usable. */
internal fun clipboardCopyMessage(successMessage: String, copy: () -> Unit): String = try {
    copy()
    successMessage
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: RuntimeException) {
    "Couldn't copy to clipboard. Please try again."
}
