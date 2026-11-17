// Semantics test tags used by the UI and the instrumented tests.
package com.legenddrry.feature.vault.ui.common

object VaultTestTags {
    // Entries list (EntriesRoute)
    const val ENTRIES_SCREEN = "entries_screen"
    const val SEARCH_FIELD = "search_field"
    const val CATEGORY_CHIP_ALL = "category_chip_all"
    const val CATEGORY_CHIP_PREFIX = "category_chip_"          // + category name, e.g. "category_chip_Email"
    const val ENTRY_ROW_PREFIX = "entry_row_"                  // + entry id
    const val ENTRY_DUPLICATE_BADGE_PREFIX = "entry_dup_badge_" // + entry id (shown only when password reused)
    const val ADD_FAB = "add_fab"
    const val EMPTY_STATE = "empty_state"                      // vault has no entries at all
    const val NO_RESULTS_STATE = "no_results_state"            // entries exist but search/filter matches none
    const val LOADING_STATE = "loading_state"
    const val ERROR_STATE = "error_state"
    const val RETRY_BUTTON = "retry_button"

    // Detail (bottom sheet or full screen, internal state)
    const val DETAIL_SCREEN = "detail_screen"
    const val DETAIL_TITLE = "detail_title"
    const val DETAIL_USERNAME = "detail_username"
    const val DETAIL_PASSWORD = "detail_password"              // text shows "••••••••" when hidden, real value when shown
    const val DETAIL_TOGGLE_VISIBILITY = "detail_toggle_visibility"
    const val DETAIL_COPY_USERNAME = "detail_copy_username"
    const val DETAIL_COPY_PASSWORD = "detail_copy_password"
    const val DETAIL_EDIT = "detail_edit"
    const val DETAIL_DELETE = "detail_delete"
    const val DETAIL_CLOSE = "detail_close"
    const val DUPLICATE_WARNING = "duplicate_warning"          // detail screen warning when password reused

    // Delete confirmation
    const val DELETE_DIALOG = "delete_dialog"
    const val DELETE_CONFIRM = "delete_confirm"
    const val DELETE_CANCEL = "delete_cancel"

    // Editor (EntryEditorDialog, used for add, edit and "save generated password")
    const val EDITOR = "editor"
    const val EDITOR_TITLE = "editor_title"
    const val EDITOR_WEBSITE = "editor_website"
    const val EDITOR_USERNAME = "editor_username"
    const val EDITOR_PASSWORD = "editor_password"
    const val EDITOR_PASSWORD_TOGGLE = "editor_password_toggle"
    const val EDITOR_NOTES = "editor_notes"
    const val EDITOR_CATEGORY = "editor_category"              // the dropdown anchor/field
    const val EDITOR_CATEGORY_OPTION_PREFIX = "editor_category_option_" // + category name
    const val EDITOR_SAVE = "editor_save"
    const val EDITOR_CANCEL = "editor_cancel"
    const val EDITOR_ERROR_TITLE = "editor_error_title"        // supporting text node shown when title invalid
    const val EDITOR_ERROR_WEBSITE = "editor_error_website"
    const val EDITOR_ERROR_NOTES = "editor_error_notes"
    const val EDITOR_ERROR_PASSWORD = "editor_error_password"  // supporting text node shown when password invalid
    const val EDITOR_DUPLICATE_WARNING = "editor_duplicate_warning"

    // Strength meter (StrengthMeter composable)
    const val STRENGTH_METER = "strength_meter"
    const val STRENGTH_LABEL = "strength_label"                // Text with StrengthLevel.label

    // Generator (GenerateRoute)
    const val GENERATOR_SCREEN = "generator_screen"
    const val GENERATOR_PASSWORD = "generator_password"        // Text with the generated password (always visible)
    const val GENERATOR_LENGTH_SLIDER = "generator_length_slider"
    const val GENERATOR_LENGTH_LABEL = "generator_length_label" // Text containing the number, e.g. "Length: 16"
    const val GENERATOR_TOGGLE_UPPER = "generator_toggle_upper"   // Switch
    const val GENERATOR_TOGGLE_LOWER = "generator_toggle_lower"
    const val GENERATOR_TOGGLE_DIGITS = "generator_toggle_digits"
    const val GENERATOR_TOGGLE_SYMBOLS = "generator_toggle_symbols"
    const val GENERATOR_REGENERATE = "generator_regenerate"
    const val GENERATOR_COPY = "generator_copy"
    const val GENERATOR_SAVE = "generator_save"                // opens EntryEditorDialog prefilled with the password

    // Snackbar messages are plain text; tests match on these strings.
    const val MSG_PASSWORD_COPIED = "Password copied"
    const val MSG_USERNAME_COPIED = "Username copied"
    const val MSG_ENTRY_SAVED = "Entry saved"
    const val MSG_ENTRY_DELETED = "Entry deleted"
}
