package com.example.group4app

import com.example.group4app.navigation.MoreSubScreen
import com.example.group4app.navigation.ShellDestination
import com.example.group4app.navigation.ShellNavigator
import com.example.group4app.navigation.ShellTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ShellNavigatorTest {

    @Test
    fun startsOnVault() {
        assertEquals(ShellDestination(ShellTab.VAULT), ShellNavigator.Start)
    }

    @Test
    fun backOnVaultIsNotHandled() {
        assertNull(ShellNavigator.back(ShellNavigator.Start))
    }

    @Test
    fun backFromAnyOtherTabGoesToVault() {
        for (tab in ShellTab.entries - ShellTab.VAULT) {
            assertEquals(ShellNavigator.Start, ShellNavigator.back(ShellDestination(tab)))
        }
    }

    @Test
    fun backFromSubScreenGoesToMoreHubThenVault() {
        var d = ShellNavigator.openSubScreen(ShellDestination(ShellTab.MORE), MoreSubScreen.SETTINGS)
        assertEquals(ShellDestination(ShellTab.MORE, MoreSubScreen.SETTINGS), d)
        d = ShellNavigator.back(d)!!
        assertEquals(ShellDestination(ShellTab.MORE), d)
        d = ShellNavigator.back(d)!!
        assertEquals(ShellNavigator.Start, d)
        assertNull(ShellNavigator.back(d))
    }

    @Test
    fun selectingAnotherTabReplacesTheDestination() {
        val onSub = ShellDestination(ShellTab.MORE, MoreSubScreen.ABOUT)
        assertEquals(ShellDestination(ShellTab.ALIASES), ShellNavigator.selectTab(onSub, ShellTab.ALIASES))
        assertEquals(
            ShellDestination(ShellTab.GENERATE),
            ShellNavigator.selectTab(ShellNavigator.Start, ShellTab.GENERATE),
        )
    }

    @Test
    fun reselectingTheCurrentTabIsANoOp() {
        for (tab in ShellTab.entries) {
            val d = ShellDestination(tab)
            assertSame(d, ShellNavigator.selectTab(d, tab))
        }
    }

    @Test
    fun reselectingMoreOnASubScreenReturnsToTheHub() {
        val d = ShellDestination(ShellTab.MORE, MoreSubScreen.SECURITY)
        assertEquals(ShellDestination(ShellTab.MORE), ShellNavigator.selectTab(d, ShellTab.MORE))
    }

    @Test
    fun repeatedTabSwitchesNeverStackBackEntries() {
        var d = ShellNavigator.Start
        repeat(3) {
            d = ShellNavigator.selectTab(d, ShellTab.APPROVALS)
            d = ShellNavigator.selectTab(d, ShellTab.ALIASES)
            d = ShellNavigator.selectTab(d, ShellTab.ALIASES)
        }
        // One back press always reaches Vault; one more leaves the shell.
        d = ShellNavigator.back(d)!!
        assertEquals(ShellNavigator.Start, d)
        assertNull(ShellNavigator.back(d))
    }

    @Test(expected = IllegalArgumentException::class)
    fun subScreensOnlyExistUnderMore() {
        ShellDestination(ShellTab.VAULT, MoreSubScreen.ABOUT)
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val all = ShellTab.entries.map { ShellDestination(it) } +
            MoreSubScreen.entries.map { ShellDestination(ShellTab.MORE, it) }
        for (d in all) assertEquals(d, ShellNavigator.decode(ShellNavigator.encode(d)))
    }

    @Test
    fun decodeFallsBackToVaultOnBadInput() {
        assertEquals(ShellNavigator.Start, ShellNavigator.decode(null))
        assertEquals(ShellNavigator.Start, ShellNavigator.decode(""))
        assertEquals(ShellNavigator.Start, ShellNavigator.decode("NOPE"))
        assertEquals(ShellDestination(ShellTab.ALIASES), ShellNavigator.decode("ALIASES/SETTINGS"))
        assertEquals(ShellDestination(ShellTab.MORE), ShellNavigator.decode("MORE/NOPE"))
    }

    @Test
    fun stateKeysAreDistinctPerDestination() {
        val all = ShellTab.entries.map { ShellDestination(it) } +
            MoreSubScreen.entries.map { ShellDestination(ShellTab.MORE, it) }
        assertEquals(all.size, all.map { it.stateKey }.toSet().size)
    }
}
