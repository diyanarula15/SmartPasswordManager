// OWNER: orchestrator (shared). Do not edit in worker tasks.
package com.legenddrry.feature.vault.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

/**
 * Copies [text] to the system clipboard. When [sensitive] is true the clip is flagged
 * so Android 13+ hides it from the clipboard preview overlay.
 */
fun copyToClipboard(context: Context, label: String, text: String, sensitive: Boolean = true) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            // ClipDescription.EXTRA_IS_SENSITIVE (API 33); harmless on older versions.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
