package com.legenddrry.feature.tools

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.legenddrry.feature.tools.approvals.InMemoryApprovalsRepository
import com.legenddrry.feature.tools.contracts.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the exact public host API against injected repositories on a real Android device. */
@RunWith(AndroidJUnit4::class)
class FeatureRoutesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(dependencies: ToolsDependencies = ToolsDependencies.demo(), content: @Composable () -> Unit) {
        compose.setContent {
            ProvideTools(dependencies) {
                LegenDDRRYToolsTheme {
                    Surface { content() }
                }
            }
        }
    }

    @Test fun approvalsCreateApproveDenyDetailsAndHistory() {
        val dependencies = ToolsDependencies.demo()
        show(dependencies) { ApprovalsRoute() }
        compose.onNodeWithText("No pending requests").assertExists()
        compose.onNodeWithTag("create_demo_request").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.approvals.requests.value.size == 1 }
        val first = dependencies.approvals.requests.value.single().id
        compose.onNodeWithTag("request_details_$first").performScrollTo().performClick()
        compose.onNodeWithText("Login request details").assertIsDisplayed()
        compose.onNodeWithText("Local simulation — no external authorization.").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("approve_$first").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.approvals.requests.value.single().status == ApprovalStatus.APPROVED }
        compose.onNodeWithTag("approve_$first").assertDoesNotExist()
        compose.onNodeWithText("Recent history (1)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("create_demo_request").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.approvals.requests.value.size == 2 }
        val second = dependencies.approvals.requests.value.first { it.status == ApprovalStatus.PENDING }.id
        compose.onNodeWithTag("deny_$second").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.approvals.requests.value.first { it.id == second }.status == ApprovalStatus.DENIED }
        compose.onNodeWithTag("deny_$second").assertDoesNotExist()
        compose.onNodeWithText("Recent history (2)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Demo request • Approved").assertExists()
        compose.onNodeWithText("Demo request • Denied").assertExists()
    }

    @Test fun overdueRequestsExpireAndCannotBeApproved() {
        val repository = InMemoryApprovalsRepository(
            initialRequests = listOf(LoginRequest("expired", "Demo device", "Sample location", 1, 2)),
            clock = { 3L },
        )
        val dependencies = ToolsDependencies.demo().copy(approvals = repository)
        show(dependencies) { ApprovalsRoute() }
        compose.waitUntil(timeoutMillis = 10_000) { repository.requests.value.single().status == ApprovalStatus.EXPIRED }
        compose.onNodeWithTag("approve_expired").assertDoesNotExist()
        compose.onNodeWithText("Demo request • Expired").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recent history (1)").assertExists()
    }

    @Test fun aliasesCreateValidateSearchCopyToggleDetailsAndConfirmedDelete() {
        val dependencies = ToolsDependencies.demo()
        show(dependencies) { AliasesRoute() }
        compose.onNodeWithText("No aliases yet").assertExists()
        compose.onNodeWithTag("aliases-create").performClick()
        compose.onNodeWithTag("aliases-confirm-create").performClick()
        compose.onNodeWithText("Enter a website or service name.").assertExists()
        compose.onNodeWithTag("aliases-service").performTextInput("Student portal")
        compose.onNodeWithTag("aliases-confirm-create").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.aliases.aliases.value.size == 1 }
        val alias = dependencies.aliases.aliases.value.single()
        compose.onNodeWithTag("alias-copy-${alias.id}").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(alias.aliasAddress, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        compose.onNodeWithTag("alias-details-${alias.id}").performScrollTo().performClick()
        compose.onNodeWithText("Alias details").assertIsDisplayed()
        compose.onNodeWithText("Demo address — email forwarding is not enabled.").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("alias-toggle-${alias.id}").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { !dependencies.aliases.aliases.value.single().isActive }
        compose.onNodeWithTag("alias-toggle-${alias.id}").assertIsOff()
        compose.onNodeWithTag("aliases-search").performScrollTo().performTextInput("unmatched")
        compose.onNodeWithText("No matching aliases").assertExists()
        compose.onNodeWithTag("aliases-search").performTextReplacement("PORTAL")
        compose.onNodeWithText("Student portal").assertExists()
        compose.onNodeWithTag("alias-delete-${alias.id}").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(1, dependencies.aliases.aliases.value.size) }
        compose.onNodeWithTag("alias-delete-${alias.id}").performClick()
        compose.onNodeWithTag("aliases-confirm-delete").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.aliases.aliases.value.isEmpty() }
        compose.onNodeWithText("No aliases yet").assertExists()
    }

    @Test fun securityLoadingThenLiveCountsThenEmpty() {
        val summaries = MutableSharedFlow<SecuritySummary>(replay = 1)
        val provider = object : SecuritySummaryProvider { override val summary = summaries }
        show(ToolsDependencies.demo().copy(security = provider)) { SecurityRoute() }
        compose.onNodeWithTag("security_loading").assertIsDisplayed()
        runBlocking { summaries.emit(SecuritySummary(10, 2, 4)) }
        compose.onNodeWithTag("security_total").assertTextEquals("10")
        compose.onNodeWithTag("security_weak").assertTextEquals("2")
        compose.onNodeWithTag("security_reused").assertTextEquals("4")
        runBlocking { summaries.emit(SecuritySummary(7, 1, 0)) }
        compose.onNodeWithTag("security_total").assertTextEquals("7")
        compose.onNodeWithTag("security_weak").assertTextEquals("1")
        compose.onNodeWithTag("security_reused").assertTextEquals("0")
        runBlocking { summaries.emit(SecuritySummary(0, 0, 0)) }
        compose.onNodeWithText("No saved passwords yet").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("security_total").assertTextEquals("0")
    }

    @Test fun securityProviderFailureHasWorkingRetry() {
        var attempts = 0
        val provider = object : SecuritySummaryProvider {
            override val summary = flow {
                if (++attempts == 1) throw IllegalStateException("Unavailable")
                emit(SecuritySummary(2, 0, 0))
            }
        }
        show(ToolsDependencies.demo().copy(security = provider)) { SecurityRoute() }
        compose.onNodeWithTag("security_error").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag("security_total").assertTextEquals("2")
        compose.runOnIdle { assertEquals(2, attempts) }
    }

    @Test fun settingsUpdateThemePreferencesAndAboutDialogs() {
        val dependencies = ToolsDependencies.demo()
        show(dependencies) {
            Column {
                Text("${MaterialTheme.colorScheme.background.value}", Modifier.testTag("theme_color"))
                SettingsRoute()
            }
        }
        compose.onNodeWithTag("theme_system").assertIsSelected()
        compose.onNodeWithTag("theme_light").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.settings.preferences.value.themeMode == ThemeMode.LIGHT }
        val light = compose.onNodeWithTag("theme_color").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        compose.onNodeWithTag("theme_dark").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.settings.preferences.value.themeMode == ThemeMode.DARK }
        compose.onNodeWithTag("theme_dark").assertIsSelected()
        val dark = compose.onNodeWithTag("theme_color").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        assertNotEquals(light, dark)
        compose.onNodeWithTag("theme_system").performClick()
        compose.onNodeWithTag("theme_system").assertIsSelected()
        compose.onNodeWithTag("auto_lock_preference").performScrollTo().performClick()
        compose.onNodeWithText("15 minutes").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.settings.preferences.value.autoLockMinutes == 15 }
        compose.onNodeWithTag("clipboard_preference").performScrollTo().performClick()
        compose.onNodeWithText("60 seconds").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.settings.preferences.value.clipboardClearSeconds == 60 }
        compose.onNodeWithTag("show_passwords_preference").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.settings.preferences.value.showPasswords }
        compose.onNodeWithTag("show_passwords_preference").assertIsOn()
        listOf("about_button", "privacy_button", "limitations_button").forEach {
            compose.onNodeWithTag(it).performScrollTo().performClick()
            compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        }
    }

    @Test fun publicRoutesCanSwitchRepeatedlyWithoutLosingRepositoryData() {
        val dependencies = ToolsDependencies.demo()
        val section = mutableIntStateOf(0)
        show(dependencies) {
            when (section.intValue) {
                0 -> ApprovalsRoute()
                1 -> AliasesRoute()
                2 -> SecurityRoute()
                else -> SettingsRoute()
            }
        }
        compose.onNodeWithTag("create_demo_request").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { dependencies.approvals.requests.value.size == 1 }
        repeat(3) {
            compose.runOnIdle { section.intValue = 1 }
            compose.onNodeWithText("Hide My Email").assertExists()
            compose.runOnIdle { section.intValue = 2 }
            compose.onNodeWithText("Security dashboard").assertExists()
            compose.runOnIdle { section.intValue = 3 }
            compose.onNodeWithText("Settings").assertExists()
            compose.runOnIdle { section.intValue = 0 }
            compose.onNodeWithText("Pending requests (1)").assertExists()
        }
    }
}
