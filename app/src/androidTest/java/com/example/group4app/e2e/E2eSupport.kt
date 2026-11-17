// Helpers for the end-to-end instrumented tests of the integrated LegenDDRRY app.
// Never put passwords into assertion messages or logs.
package com.example.group4app.e2e

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.group4app.LegenDDRRYApplication
import com.example.group4app.di.AppContainer
import java.io.File

/** Known e2e master password (test value only, never a real secret). */
internal const val TEST_MASTER_PASSWORD = "Correct-Horse-9!"

internal const val WAIT_MS = 10_000L

/** PBKDF2 key derivation may take a few seconds on an emulator. */
internal const val AUTH_WAIT_MS = 30_000L

/** Exactly 8 bullets (U+2022): VaultTestTags.DETAIL_PASSWORD text while hidden. */
internal const val MASKED_PASSWORD = "••••••••"

internal val app: LegenDDRRYApplication
    get() = ApplicationProvider.getApplicationContext()

// ---------------------------------------------------------------------------------------------
// Clean-state handling
// ---------------------------------------------------------------------------------------------

/**
 * The Application (and its AppContainer) is created once per instrumentation process, before
 * any test runs. To give every test a clean, freshly-installed app we:
 *  1. wipe app-private storage while no activity is running, then
 *  2. build a brand-new real [AppContainer] and install it into the Application object, so the
 *     next MainActivity reads it (MainActivity takes `application.container` in onCreate).
 *
 * Returns false (storage untouched) when the container cannot be replaced; tests then fall back
 * to state-based handling (set up or unlock with [TEST_MASTER_PASSWORD]).
 */
internal object AppStateReset {
    private const val SETTINGS_PREFS = "legenddrry_settings"

    @Volatile
    var lastFailure: Throwable? = null
        private set

    fun resetToFreshInstall(): Boolean {
        val field = runCatching {
            LegenDDRRYApplication::class.java.getDeclaredField("container").apply { isAccessible = true }
        }.getOrElse {
            lastFailure = it
            return false
        }
        val application = app
        wipeStorage(application)

        var created: AppContainer? = null
        var error: Throwable? = null
        // Application.onCreate builds the container on the main thread; do the same.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                created = AppContainer(application)
            } catch (t: Throwable) {
                error = t
            }
        }
        error?.let { throw AssertionError("Building a fresh AppContainer after wiping storage failed", it) }
        field.set(application, created)
        lastFailure = null
        return true
    }

    fun wipeStorage(context: Context) {
        context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.noBackupFilesDir.listFiles()?.forEach { it.deleteRecursively() }
        context.databaseList().forEach { context.deleteDatabase(it) }
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        prefsDir.listFiles()
            ?.filter { it.name.endsWith(".xml") }
            ?.forEach { context.deleteSharedPreferences(it.name.removeSuffix(".xml")) }
        context.deleteSharedPreferences(SETTINGS_PREFS)
    }
}

// ---------------------------------------------------------------------------------------------
// Compose lookups (unmerged tree: tags may sit on inner nodes)
// ---------------------------------------------------------------------------------------------

private fun SemanticsNodeInteractionCollection.safeNodes(): List<SemanticsNode> =
    try {
        fetchSemanticsNodes()
    } catch (e: IllegalStateException) {
        emptyList() // no compose hierarchy (activity finishing / not yet started)
    }

internal fun ComposeTestRule.nodesWithTag(tag: String): List<SemanticsNode> =
    onAllNodesWithTag(tag, useUnmergedTree = true).safeNodes()

internal fun ComposeTestRule.exists(tag: String): Boolean = nodesWithTag(tag).isNotEmpty()

/** Non-editable nodes showing [text] (search/text fields are excluded on purpose). */
internal fun ComposeTestRule.displayedText(
    text: String,
    substring: Boolean = false,
    ignoreCase: Boolean = false,
): SemanticsNodeInteractionCollection =
    onAllNodes(hasText(text, substring = substring, ignoreCase = ignoreCase) and !hasSetTextAction(), useUnmergedTree = true)

internal fun ComposeTestRule.textShown(text: String, substring: Boolean = false, ignoreCase: Boolean = false): Boolean =
    displayedText(text, substring, ignoreCase).safeNodes().isNotEmpty()

/** waitUntil with a readable failure message. */
internal fun ComposeTestRule.await(what: String, timeoutMs: Long = WAIT_MS, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMs) { condition() }
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("Timed out after ${timeoutMs}ms waiting for: $what", e)
    }
}

/** Asserts [condition] holds continuously for [durationMs] (e.g. "still locked"). */
internal fun ComposeTestRule.assertStays(what: String, durationMs: Long = 2_500, condition: () -> Boolean) {
    val end = android.os.SystemClock.uptimeMillis() + durationMs
    while (android.os.SystemClock.uptimeMillis() < end) {
        waitForIdle()
        if (!condition()) throw AssertionError("Expected to stay true: $what")
        Thread.sleep(100)
    }
}

internal fun ComposeTestRule.awaitTag(tag: String, timeoutMs: Long = WAIT_MS) =
    await("node tagged '$tag' to exist", timeoutMs) { exists(tag) }

internal fun ComposeTestRule.awaitTagGone(tag: String, timeoutMs: Long = WAIT_MS) =
    await("node tagged '$tag' to disappear", timeoutMs) { !exists(tag) }

internal fun ComposeTestRule.awaitText(text: String, timeoutMs: Long = WAIT_MS, substring: Boolean = false, ignoreCase: Boolean = false) =
    await("text '$text' to be shown", timeoutMs) { textShown(text, substring, ignoreCase) }

internal fun ComposeTestRule.awaitTextGone(text: String, timeoutMs: Long = WAIT_MS, substring: Boolean = false, ignoreCase: Boolean = false) =
    await("text '$text' to disappear", timeoutMs) { !textShown(text, substring, ignoreCase) }

internal fun ComposeTestRule.node(tag: String, timeoutMs: Long = WAIT_MS): SemanticsNodeInteraction {
    awaitTag(tag, timeoutMs)
    return onAllNodesWithTag(tag, useUnmergedTree = true)[0]
}

private fun SemanticsNodeInteraction.tryScrollTo(): SemanticsNodeInteraction {
    runCatching { performScrollTo() }
    return this
}

/** Waits for [tag], scrolls it into view when inside a scrollable parent, and clicks it. */
internal fun ComposeTestRule.click(tag: String, timeoutMs: Long = WAIT_MS) {
    node(tag, timeoutMs).tryScrollTo().performClick()
    waitForIdle()
}

/** Clicks the first non-editable node showing exactly [text]. */
internal fun ComposeTestRule.clickText(text: String, timeoutMs: Long = WAIT_MS) {
    awaitText(text, timeoutMs)
    displayedText(text)[0].tryScrollTo().performClick()
    waitForIdle()
}

/** Replaces the content of the text field tagged [tag]. */
internal fun ComposeTestRule.typeInto(tag: String, value: String) {
    node(tag).tryScrollTo().performTextReplacement(value)
    waitForIdle()
}

/** Own Text/EditableText of the node tagged [tag], else concatenated text of its descendants. */
internal fun ComposeTestRule.textOrNull(tag: String): String? {
    val n = nodesWithTag(tag).firstOrNull() ?: return null
    return n.ownText().ifEmpty { n.descendantText() }
}

internal fun ComposeTestRule.textOf(tag: String): String {
    awaitTag(tag)
    return textOrNull(tag).orEmpty()
}

internal fun ComposeTestRule.intOf(tag: String): Int? = textOrNull(tag)?.trim()?.toIntOrNull()

private fun SemanticsNode.ownText(): String {
    val editable = config.getOrNull(SemanticsProperties.EditableText)?.text
    if (!editable.isNullOrEmpty()) return editable
    return config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
}

private fun SemanticsNode.descendantText(): String =
    children.joinToString("") { c -> c.ownText().ifEmpty { c.descendantText() } }

private fun SemanticsNode.findToggleState(): ToggleableState? =
    config.getOrNull(SemanticsProperties.ToggleableState)
        ?: children.firstNotNullOfOrNull { it.findToggleState() }

internal fun ComposeTestRule.toggleState(tag: String): ToggleableState? =
    nodesWithTag(tag).firstOrNull()?.findToggleState()

private fun SemanticsNode.findSelected(): Boolean? =
    config.getOrNull(SemanticsProperties.Selected)
        ?: children.firstNotNullOfOrNull { it.findSelected() }

internal fun ComposeTestRule.isSelected(tag: String): Boolean =
    nodesWithTag(tag).firstOrNull()?.findSelected() == true
