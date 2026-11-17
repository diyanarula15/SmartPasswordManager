package com.legenddrry.feature.tools

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.legenddrry.feature.tools.aliases.InMemoryAliasesRepository
import com.legenddrry.feature.tools.approvals.InMemoryApprovalsRepository
import com.legenddrry.feature.tools.contracts.*
import com.legenddrry.feature.tools.preview.SageClayPreviewTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Genuine emulator captures of production routes, with theme installed ONLY by this test host. */
@RunWith(AndroidJUnit4::class)
class SageClayGalleryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun allScreensAndLocalDialogsRenderInBothAppearances() {
        // Isolate gallery captures from the preceding real clipboard workflow.
        compose.activityRule.scenario.onActivity { activity ->
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).clearPrimaryClip()
            }
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")).use { it.readBytes() }
        val now = System.currentTimeMillis()
        val approvals = InMemoryApprovalsRepository(initialRequests = listOf(
            LoginRequest("gallery-pending", "Simulated browser on demo laptop", "Sample location: Bengaluru, India", now, now + 3_600_000),
            LoginRequest("gallery-approved", "Demo tablet", "Sample location: Campus", now - 86_400_000, now + 3_600_000, ApprovalStatus.APPROVED, resolvedAtEpochMs = now - 86_000_000),
            LoginRequest("gallery-denied", "Demo browser", "Sample location: Campus", now - 80_000_000, now + 3_600_000, ApprovalStatus.DENIED, resolvedAtEpochMs = now - 79_000_000),
        ))
        val aliases = InMemoryAliasesRepository(initialAliases = listOf(
            EmailAlias("gallery-alias", "Campus library", "demo.campus.library@example.com", now - 86_400_000, true),
            EmailAlias("gallery-disabled", "Student society", "demo.student.society@example.com", now - 172_800_000, false),
        ))
        val dependencies = ToolsDependencies.demo().copy(approvals = approvals, aliases = aliases)
        val section = mutableIntStateOf(0)
        compose.activityRule.scenario.onActivity {
            it.actionBar?.hide()
            it.enableEdgeToEdge()
        }
        compose.setContent {
            ProvideTools(dependencies) {
                SageClayPreviewTheme {
                    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    SideEffect {
                        WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).apply {
                            isAppearanceLightStatusBars = !dark
                            isAppearanceLightNavigationBars = !dark
                        }
                    }
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.safeDrawingPadding()) {
                            key(section.intValue) {
                                when(section.intValue) {
                                    0 -> ApprovalsRoute()
                                    1 -> AliasesRoute()
                                    2 -> SecurityRoute()
                                    else -> SettingsRoute()
                                }
                            }
                        }
                    }
                }
            }
        }
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { mode ->
            runBlocking { dependencies.settings.setThemeMode(mode) }
            val suffix = mode.name.lowercase()
            listOf("approvals", "aliases", "security", "settings").forEachIndexed { index, name ->
                compose.runOnIdle { section.intValue = index }
                compose.waitForIdle()
                capture("$name-$suffix")
            }
            compose.runOnIdle { section.intValue = 0 }
            compose.onNodeWithTag("request_details_gallery-pending").performScrollTo().performClick()
            compose.onNodeWithText("Login request details").assertIsDisplayed()
            capture("approval-details-$suffix")
            compose.onNodeWithText("Close").performClick()
            compose.onNodeWithText("Recent history (2)").performScrollTo()
            capture("approval-history-$suffix")
            compose.runOnIdle { section.intValue = 1 }
            compose.onNodeWithTag("aliases-create").performClick()
            compose.onNodeWithTag("aliases-service").performTextInput("Student portal")
            compose.onNodeWithTag("aliases-service").performImeAction()
            capture("create-alias-$suffix")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithTag("alias-details-gallery-alias").performScrollTo().performClick()
            compose.onNodeWithText("Alias details").assertIsDisplayed()
            capture("alias-details-$suffix")
            compose.onNodeWithText("Close").performClick()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Wait for the actual view/compositor and accessibility events, not only Compose state.
        compose.onAllNodes(isRoot()).onLast().captureToImage()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(250, 5_000)
        val path = "/sdcard/Download/legenddrry-sage-clay-v2/$name.png"
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/Download/legenddrry-sage-clay-v2", "screencap -p $path").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        val signature = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("head -c 8 $path")).use { it.readBytes() }
        org.junit.Assert.assertArrayEquals("Screenshot must be a real PNG: $name",
            byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), signature)
    }
}
