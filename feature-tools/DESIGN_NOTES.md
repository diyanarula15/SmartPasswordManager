# Sage & Clay integration notes

The supplied HTML and15-page PDF were read as the visual reference. Feature behavior,
repository interfaces, and the four public routes remain unchanged. This redesign
adds no authentication, vault, global navigation, or splash implementation.

## What changed

| Area | Implementation |
| --- | --- |
| Shared UI |16dp phone/24dp tablet margins,12dp card gaps,24dp section gaps;20dp borderless surface cards/dialogs;14dp filled fields;52dp pill actions |
| Typography/icons | Bundled Manrope400/500/600/700; genuine Material Symbols Rounded subset, offline, with licenses |
| Approvals |44dp device tiles; semantic status icon+words; responsive approve/deny actions; whole-card detail opening; scrolling details/history |
| Aliases | Clay creation FAB; filled pill search; clear service/address hierarchy;48dp copy action; animated status; complete management dialogs |
| Security | Primary/onPrimary hero, provider-owned totals, neutral count cards, restrained animated indicators and useful recommendations |
| Settings | Animated theme selector, grouped icon rows, rounded duration choices, privacy/about/demo information |
| Empty/loading/error | Intentional shared empty cards, restrained loading, icon+text error feedback, working retry paths |
| Motion |150ms status/selection transitions;275ms dialog content; no bounce effects |

Clay is reserved for the approvals demo badge, alias creation FAB, security
recommendation icon, and settings privacy tile. Tonal actions explicitly use
`primaryContainer/onPrimaryContainer` (sage), not Material3's secondary defaults.
Errors use the semantic error roles, with icons and text.

## Theme ownership — important for Developer1

Production code only consumes `MaterialTheme.colorScheme`; it installs no colour
scheme and has no palette hex constants. The existing application theme must map
its semantic roles to the guide's complete light/dark palettes and disable dynamic
colour. Reference palettes are isolated to Android test sources and the separate
verification app.

`LegenDDRRYToolsTheme` remains callable with its original signature for source
compatibility, but is deprecated and passes content through unchanged. It no longer
creates a competing theme. The host should observe `ToolsDependencies.settings.preferences`
in its existing app theme to apply light/dark/system appearance. All four routes
continue to require no arguments.

`ToolsType` supplies feature-local Manrope styles and an optional `materialTypography`
value for the host. Rounded icon/font resources use `tools_` prefixes to avoid host
resource collisions. Font licenses are in `src/main/assets/font-licenses/`.

## Files changed

Production Kotlin:
- `ToolsEnvironment.kt`: theme ownership and source-compatible deprecation.
- `components/ToolsDesign.kt`: Manrope typography tokens.
- `components/SharedToolComponents.kt`: semantic components, icons, fields, actions,
  keyboard actions, dialogs, responsive page layout and motion.
- `approvals/ApprovalScreen.kt`, `approvals/ApprovalStateHolder.kt`: UI and explicit
  feedback error state; repository contracts unchanged.
- `aliases/AliasesFeature.kt`: screen, FAB, cards and all dialogs.
- `security/SecurityFeature.kt`: dashboard and all visual states.
- `settings/SettingsFeature.kt`: grouped controls and all dialogs.
- `src/main/res/font/*`, `src/main/assets/font-licenses/*`: offline assets/licenses.

Verification/docs:
- Existing approval state-holder assertions and all existing route workflow tests retained.
- Three new device checks cover approval/alias/settings mutation failure and recovery.
- `SageClayGalleryTest.kt`: actual light/dark screen/dialog/history captures with PNG validation.
- `src/androidTest/.../preview/SageClayPreviewTheme.kt`: isolated reference palette.
- `README.md`, `CONTRACT.md`, `DESIGN_CONTRACT.md`, `VERIFICATION.md`, this file.

The separate ignored verification harness has its own preview theme and host navigation.
Its files are not part of the production integration source archive. No changes were
made to Developer1's app/authentication or Developer2's vault/generator source.

## Build coordination

Developer 1 updated the production library build to the application's AGP 9 / SDK 37
catalog during the redesign. That configuration was preserved. The temporary
verification harness selects its own pinned AGP 8.7 / SDK 35 build script while
compiling the real `feature-tools/src` tree, without replacing the production build
or touching app/authentication/vault code. The separate harness archive contains
this development-only script; the integration source archive does not.
The current production toolchain was also verified independently: compilation,
45 unit tests, and release AAR assembly passed with outputs isolated to /tmp.

## Existing limitations retained

In-memory demo records reset on process death. Demo login approvals authorize no
external service. Demo aliases do not receive or forward mail. Dashboard statistics
come exclusively from the injected provider. Persistent storage, real services,
and auto-lock/clipboard/password-visibility enforcement belong to the host.
