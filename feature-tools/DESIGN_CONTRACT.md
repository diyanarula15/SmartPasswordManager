# Sage & Clay redesign — locked component contract

Repository contracts, data classes, state holders, public route signatures and existing test tags remain unchanged.
Root owns shared components, fonts, dependency injection, build files, previews/tests and docs.
Feature agents own only their screen packages (and their package tests if needed).

Production inherits the HOST MaterialTheme. No hex colours or nested MaterialTheme wrappers.
`LegenDDRRYToolsTheme` remains source-compatible but becomes a deprecated pass-through;
only isolated verification/test code supplies Sage & Clay light/dark schemes.

Shared components provided by root in `components`:
- `ToolsType`: TextStyles `display`, `headline`, `titleLarge`, `title`, `bodyLarge`, `body`, `label`, `caption`, `overline`, all bundled Manrope at guide sizes/weights.
- `ToolPage(title, subtitle, content: @Composable ColumnScope.() -> Unit)` preserved. Background semantic background, 16dp phone /24dp tablet sides, 12dp child gaps, header 26sp. Features add `ToolSectionTitle` for 24dp section breaks.
- `ToolCard(content)` preserved: surface, radius20, padding16, faint elevation, no border.
- `EmptyToolState(title, description)` preserved with calm icon and whitespace (no clay by default).
- `ToolIcon(name: String, modifier: Modifier = Modifier, tint: Color = onSurfaceVariant, size: Dp = 24.dp, description: String? = null, filled: Boolean = false)`; icon names: shield, devices, laptop, phone, location, schedule, check, close, history, mail, copy, delete, add, search, info, lock, visibility, settings, light, dark, system, chevron, arrow, warning, error, refresh, tune, key, help, pause, check_circle, logout. Genuine Material Symbols Rounded assets; decorative by default.
- `ToolIconTile(name: String)` 44dp rounded14 sage-container tile.
- `ToolSectionTitle(title: String, supporting: String? = null)` 24dp top section gap.
- `ToolLabel(text: String, accent: Boolean = false)` badge, optional single clay badge per screen.
- `ToolNotice(title: String, message: String, icon: String = "info")` sage-container disclosure, no clay; onPrimaryContainer text.
- `ToolFeedback(message: String, isError: Boolean = false, onDismiss: (() -> Unit)? = null)` semantic error/primary feedback with icon+words and 150ms appearance.
- `ToolTextField(value, onValueChange, label, modifier = Modifier, supportingText: String? = null, isError = false, enabled = true, leadingIcon: String? = null, search = false)` filled field, label above, radius14 or pill search, no rest border,2dp focus/error outline; supporting error includes icon; singleLine.
- `ToolActionButton(text, onClick, modifier = Modifier, enabled = true, icon: String? = null, kind: ToolButtonKind = PRIMARY)` kinds PRIMARY / TONAL / OUTLINED / TEXT. 52dp pills, 18dp icon. Preserve test tags on modifier.
- `ToolDialog(title: String, onDismiss: () -> Unit, confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit = {}, content: @Composable ColumnScope.() -> Unit)` rounded20 scrollable AlertDialog, surface, Manrope, 275ms fade-in content.
- `formatToolTimestamp` unchanged.

Palette roles from guide: primary sage; secondary clay; primaryContainer mist; background ground;
surface card; surfaceVariant sunken fields; error red. All on-colour pairs appropriate.
One meaningful clay element per screen: approvals demo badge; aliases creation FAB; security recommendation icon/badge;
settings privacy icon. Do not use clay for errors. No global navigation or splash.

Use 150ms tween for status/selection changes;275ms for local dialog content. Built-in switches retain accessible48dp touch area.
Buttons are sentence case; preserve test selectors/tags and test-visible essential text where possible.
All state/UI actions remain wired. Each agent must execute its existing package unit tests after redesign.
