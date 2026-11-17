package com.example.group4app.navigation

import androidx.compose.runtime.saveable.Saver

/** The five global NavigationBar destinations. */
enum class ShellTab { VAULT, GENERATE, APPROVALS, ALIASES, MORE }

/** Screens reached from the More hub. */
enum class MoreSubScreen { SECURITY, SETTINGS, ABOUT }

/** Where the unlocked shell currently is. A sub-screen only ever lives under [ShellTab.MORE]. */
data class ShellDestination(
    val tab: ShellTab = ShellTab.VAULT,
    val subScreen: MoreSubScreen? = null,
) {
    init {
        require(subScreen == null || tab == ShellTab.MORE) { "Sub-screens belong to the More tab" }
    }

    /** Key for the per-destination saved UI state (scroll position, text fields, ...). */
    val stateKey: String get() = subScreen?.let { "${tab.name}/${it.name}" } ?: tab.name
}

/**
 * Pure navigation reducer for the main shell (no Android types, unit-tested).
 *
 * Back rules: sub-screen → More hub; any non-Vault tab → Vault; Vault → not handled (system
 * default, i.e. leave the app). Re-selecting the current tab never stacks a duplicate: on a
 * More sub-screen it returns to the hub, otherwise it is a no-op.
 */
object ShellNavigator {
    val Start = ShellDestination()

    fun selectTab(current: ShellDestination, tab: ShellTab): ShellDestination = when {
        tab != current.tab -> ShellDestination(tab)
        current.subScreen != null -> ShellDestination(tab)
        else -> current
    }

    fun openSubScreen(@Suppress("UNUSED_PARAMETER") current: ShellDestination, screen: MoreSubScreen) =
        ShellDestination(ShellTab.MORE, screen)

    /** @return the destination after a back press, or null when the shell should not consume it. */
    fun back(current: ShellDestination): ShellDestination? = when {
        current.subScreen != null -> ShellDestination(current.tab)
        current.tab != ShellTab.VAULT -> Start
        else -> null
    }

    fun encode(destination: ShellDestination): String = destination.stateKey

    /** Inverse of [encode]; unknown or malformed input falls back to [Start]. */
    fun decode(value: String?): ShellDestination {
        if (value.isNullOrBlank()) return Start
        val parts = value.split('/')
        val tab = ShellTab.entries.firstOrNull { it.name == parts[0] } ?: return Start
        val sub = parts.getOrNull(1)?.let { s -> MoreSubScreen.entries.firstOrNull { it.name == s } }
        return if (sub != null && tab == ShellTab.MORE) ShellDestination(tab, sub) else ShellDestination(tab)
    }

    val Saver: Saver<ShellDestination, String> = Saver(
        save = { encode(it) },
        restore = { decode(it) },
    )
}
