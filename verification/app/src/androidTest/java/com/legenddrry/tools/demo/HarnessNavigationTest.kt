package com.legenddrry.tools.demo

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HarnessNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun launcherCanUseAllFourPublicRoutesAndKeepsSharedSession() {
        compose.onNodeWithTag("create_demo_request").performClick()
        compose.onNodeWithText("Pending requests (1)").assertExists()
        compose.onNodeWithText("Aliases").performClick()
        compose.onNodeWithText("Hide My Email").assertExists()
        compose.onNodeWithTag("aliases-create").performClick()
        compose.onNodeWithTag("aliases-service").performTextInput("University")
        compose.onNodeWithTag("aliases-confirm-create").performClick()
        compose.onNodeWithText("Saved aliases (1)").assertExists()
        compose.onNodeWithText("Security").performClick()
        compose.onNodeWithTag("security_total").assertTextEquals("12")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("theme_dark").performClick()
        compose.onNodeWithTag("theme_dark").assertIsSelected()
        compose.onNodeWithText("Approvals").performClick()
        compose.onNodeWithText("Pending requests (1)").assertExists()
        compose.onNodeWithText("Aliases").performClick()
        compose.onNodeWithText("Saved aliases (1)").assertExists()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithTag("theme_dark").assertIsSelected()
    }
}
