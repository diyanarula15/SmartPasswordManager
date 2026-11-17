// Shared, LOCKED test tags for the app shell (auth + navigation). Feature modules keep their own tags.
package com.example.group4app.ui.shell

object AppTestTags {
    // Auth
    const val SETUP_SCREEN = "setup_screen"
    const val SETUP_PASSWORD = "setup_password"
    const val SETUP_CONFIRM = "setup_confirm"
    const val SETUP_SUBMIT = "setup_submit"
    const val SETUP_ERROR = "setup_error"
    const val UNLOCK_SCREEN = "unlock_screen"
    const val UNLOCK_PASSWORD = "unlock_password"
    const val UNLOCK_SUBMIT = "unlock_submit"
    const val UNLOCK_ERROR = "unlock_error"
    const val AUTH_LOADING = "auth_loading"

    // Main shell: one global NavigationBar
    const val MAIN_SHELL = "main_shell"
    const val NAV_VAULT = "nav_vault"
    const val NAV_GENERATE = "nav_generate"
    const val NAV_APPROVALS = "nav_approvals"
    const val NAV_ALIASES = "nav_aliases"
    const val NAV_MORE = "nav_more"

    // More hub and its sub-screens
    const val MORE_SCREEN = "more_screen"
    const val MORE_SECURITY = "more_security"
    const val MORE_SETTINGS = "more_settings"
    const val MORE_ABOUT = "more_about"
    const val MORE_LOCK = "more_lock"          // "Lock now"
    const val ABOUT_SCREEN = "about_screen"
    const val SUBSCREEN_BACK = "subscreen_back" // top-bar back arrow on Security/Settings/About
}
