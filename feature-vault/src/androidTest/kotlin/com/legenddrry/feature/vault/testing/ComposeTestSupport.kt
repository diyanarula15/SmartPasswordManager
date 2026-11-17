// Shared helpers for the instrumented Compose tests.
package com.legenddrry.feature.vault.testing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.test.platform.app.InstrumentationRegistry

const val WAIT_MS = 5_000L

/** Exactly 8 bullets (U+2022): the contract text of DETAIL_PASSWORD while hidden. */
const val MASKED_PASSWORD = "••••••••"

fun ComposeContentTestRule.exists(tag: String): Boolean =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

fun ComposeContentTestRule.textExists(text: String): Boolean =
    onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/** waitUntil that fails with a readable message instead of a bare ComposeTimeoutException. */
fun ComposeContentTestRule.await(what: String, timeoutMs: Long = WAIT_MS, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMs) { condition() }
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $what", e)
    }
}

fun ComposeContentTestRule.awaitTag(tag: String, timeoutMs: Long = WAIT_MS) =
    await("node tagged '$tag' to exist", timeoutMs) { exists(tag) }

fun ComposeContentTestRule.awaitTagGone(tag: String, timeoutMs: Long = WAIT_MS) =
    await("node tagged '$tag' to disappear", timeoutMs) { !exists(tag) }

fun ComposeContentTestRule.awaitText(text: String, timeoutMs: Long = WAIT_MS) =
    await("text '$text' to be shown", timeoutMs) { textExists(text) }

/** First node with [tag] in the unmerged tree (tags may sit on inner nodes). */
fun ComposeContentTestRule.node(tag: String): SemanticsNodeInteraction {
    awaitTag(tag)
    return onAllNodesWithTag(tag, useUnmergedTree = true)[0]
}

/** Waits for [tag] and clicks it. */
fun ComposeContentTestRule.click(tag: String) {
    node(tag).performClick()
    waitForIdle()
}

/**
 * Visible text of the node tagged [tag]: its own Text / EditableText if present,
 * otherwise the concatenated Text of its (unmerged) descendants.
 */
fun ComposeContentTestRule.textOf(tag: String): String {
    awaitTag(tag)
    val n = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
    val own = n.ownText()
    return own.ifEmpty { n.descendantText() }
}

/** Like [textOf] but returns null instead of failing when the node is absent. */
fun ComposeContentTestRule.textOrNull(tag: String): String? {
    val nodes = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
    val n = nodes.firstOrNull() ?: return null
    return n.ownText().ifEmpty { n.descendantText() }
}

private fun SemanticsNode.ownText(): String {
    val editable = config.getOrNull(SemanticsProperties.EditableText)?.text
    if (!editable.isNullOrEmpty()) return editable
    return config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
}

private fun SemanticsNode.descendantText(): String =
    children.joinToString("") { c -> c.ownText().ifEmpty { c.descendantText() } }

/** ToggleableState of the tagged node, or of its first toggleable descendant (Switch inside a Row). */
fun ComposeContentTestRule.toggleStateOf(tag: String): ToggleableState? {
    awaitTag(tag)
    val n = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
    return n.findToggleState()
}

private fun SemanticsNode.findToggleState(): ToggleableState? =
    config.getOrNull(SemanticsProperties.ToggleableState)
        ?: children.firstNotNullOfOrNull { it.findToggleState() }

fun ComposeContentTestRule.isOn(tag: String): Boolean = toggleStateOf(tag) == ToggleableState.On

// ---- Clipboard (read/written on the main thread; the test activity has window focus) ----

private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

/** Last text our code wrote via copyToClipboard (captured through the test seam). */
@Volatile
var lastCopiedText: String? = null

/** Reads the system clipboard; null when Android denies access (another window has focus). */
fun readClipboardText(): String? {
    var result: String? = null
    instrumentation.runOnMainSync {
        val ctx = instrumentation.targetContext
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = runCatching { cm.primaryClip }.getOrNull()
        result = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(ctx)?.toString() else null
    }
    return result
}

fun clearClipboard() {
    lastCopiedText = null
    com.legenddrry.feature.vault.ui.common.copyObserverForTest = { _, text -> lastCopiedText = text }
    instrumentation.runOnMainSync {
        val cm = instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
            else cm.setPrimaryClip(ClipData.newPlainText("cleared", ""))
        }
    }
}

/**
 * Asserts the app copied exactly [expected]. The write is observed through the test seam;
 * the system clipboard is also checked whenever the OS lets the test read it.
 */
fun ComposeContentTestRule.awaitClipboard(expected: String, timeoutMs: Long = WAIT_MS) {
    await("clipboard write of '$expected' (was '$lastCopiedText')", timeoutMs) { lastCopiedText == expected }
    val system = readClipboardText()
    if (system != null) {
        org.junit.Assert.assertEquals("system clipboard content", expected, system)
    }
}
