# LegenDDRRY feature-tools

Integration-ready Android library for Login Approvals, Demo Email Aliases,
Security Dashboard and Settings. Kotlin / Compose Material 3, min SDK 26.
All production code resides in this module. No global navigation, authentication,
vault database, encryption implementation, network permission or backend is added.

## Add to the main app

Copy `feature-tools/` into the Android project. In `settings.gradle.kts`:

```kotlin
include(":feature-tools")
```

The production build file follows Developer 1's current version catalog:
`libs.plugins.android.library` and `libs.plugins.kotlin.compose`. The main project
currently uses AGP 9.5.1, Gradle 9.6.0, Compose compiler plugin 2.2.10 and SDK 37.
AGP 9 supplies Kotlin itself; do not add a competing Kotlin Android plugin.
Keep the existing application catalog aliases and plugin setup when integrating.
Repositories are `google()` and `mavenCentral()`.

The app adds `implementation(project(":feature-tools"))` and its existing
Compose/Material dependencies. This module's dependency list is in
`build.gradle.kts`: Compose BOM 2024.12.01, Material 3, Foundation, UI,
lifecycle-runtime-compose and lifecycle-viewmodel-ktx 2.8.7, coroutines-core/Android 1.9.0.
Coroutines core is an API dependency because the public contracts expose Flow/StateFlow.
JUnit and Android Compose test dependencies are test-only.

For independent verification, the separate `verification/` harness uses an isolated
AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.10.2 / SDK 35 build script for the **same real
module source directory**. `verification/settings.gradle.kts` selects that script
with `buildFileName`; it never replaces the production build file or copies/mock-
implements feature code. This keeps testing independent of main-app setup.

Call these exact public functions from the host's existing navigation:

```kotlin
import com.legenddrry.feature.tools.*

ApprovalsRoute()
AliasesRoute()
SecurityRoute()
SettingsRoute()
```

## Inject the host repositories

Create dependencies **once per authenticated session**, outside recomposition.
Recreate/discard that object at logout so one user's state cannot be reused for
another user. Use the host's lifecycle/session owner to scope repository work.

```kotlin
val tools = ToolsDependencies(
    approvals = hostApprovalsRepository,
    aliases = hostAliasesRepository,
    security = hostVaultSummaryProvider,
    settings = hostSettingsRepository,
)

// Inside the existing host application's MaterialTheme:
ProvideTools(tools) {
    // Existing app navigation invokes one of the four routes.
    SettingsRoute()
}
```

`ProvideTools` does not impose a theme or navigation. Without injection, the four
routes share one lazily initialized in-memory demo session. Use
`ToolsDependencies.demo()` explicitly for an isolated test/demo session.

The public repository/data contracts live in `contracts/ToolContracts.kt`:

| Interface | Observable state | Host integration |
| --- | --- | --- |
| `ApprovalsRepository` | `StateFlow<List<LoginRequest>>` | Atomically authorize/deny with a real backend; keep demo creation explicitly local |
| `AliasesRepository` | `StateFlow<List<EmailAlias>>` | Durable records or a genuine alias service |
| `SecuritySummaryProvider` | `Flow<SecuritySummary>` | Map Developer 2's existing vault emissions to counts; never expose plaintext in UI |
| `SettingsRepository` | `StateFlow<ToolPreferences>` | DataStore adapter plus host control enforcement |

Mutations are suspend functions. Repositories publish fresh immutable snapshots;
failures throw an exception with a user-safe message. UI displays mutation failures
and preserves coroutine cancellation. Enforce approval expiry atomically during
approve/deny, even before any UI refresh; finished/expired requests cannot transition
again. The UI additionally calls `expirePendingRequests()` while visible. Production
repositories must not perform blocking disk or network work on the main thread.

The dashboard starts loading, follows provider emissions, supports empty values,
and offers Retry on a provider failure. Emit `SecuritySummary(0, 0, 0)` for an empty
vault. Counts must be nonnegative and weak/reused counts cannot exceed total. Reused
count means the number of entries participating in reuse, **not duplicate groups**;
weak and reused sets may overlap. Set `isDemo = false` for real vault statistics.
There is no extra vault database or independently invented health score.

`ToolPreferences` defaults: SYSTEM theme, auto-lock 5 minutes, clipboard clearing
30 seconds, show passwords false. Durations are positive integers; custom injected
durations are supported by the settings UI. Theme controls update preferences immediately. The host must observe those
preferences in its existing theme; the isolated verification app demonstrates this. The host must
actually implement auto-lock, clipboard clearing, and vault password visibility.

## Sage & Clay styling and host theme ownership

The production module installs **no MaterialTheme** and contains **no palette hex
values**. All screen colours use `MaterialTheme.colorScheme` semantic roles. The
application must supply the guide's Sage & Clay light/dark palette in its existing
app theme, with dynamic colour disabled. No startup animation or global navigation
is added by this module.

`LegenDDRRYToolsTheme` keeps its old signature for source compatibility but is now a
**deprecated pass-through**. It intentionally does not override app colours or apply
a theme preference. Replace calls with the existing host app theme, and collect
`tools.settings.preferences` there to apply light/dark/system mode. The complete
reference palettes live only in the isolated preview/test files:

- `src/androidTest/java/com/legenddrry/feature/tools/preview/SageClayPreviewTheme.kt`
- `verification/app/src/main/java/com/legenddrry/tools/demo/preview/SageClayPreviewTheme.kt` (separate harness)

Required semantic roles: `primary` sage, `primaryContainer` mist,
`secondary` clay, `secondaryContainer` clay blush, `background` ground,
`surface` cards, `surfaceVariant` sunken fields, `onSurfaceVariant` muted text,
and `error/errorContainer` status red. Supply their matching `on*` colours. Clay
is reserved for one focal element per screen. Tonal buttons explicitly use sage
`primaryContainer`, because Material 3's default tonal secondary colour is clay.

Manrope 400/500/600/700 and a small subset of genuine Material Symbols Rounded are
bundled offline, with licenses in `src/main/assets/font-licenses/`. No downloadable
font service, new network permission, or extra runtime dependency is needed.
`components.ToolsType` exposes the guide's text styles (and optional
`materialTypography` value for the host); it does not create a theme.

Shared UI: 20dp cards/dialogs, 14dp fields, 28dp hero/FAB, pill-shaped 52dp action buttons,
48dp minimum interactive targets, 16dp phone/24dp tablet page margins, 12dp card gaps,
24dp section gaps. Colour/status selection animates for 150ms; local dialog content for 275ms.
Cards open their details with ripple, and nested actions remain independent.

## In-memory implementations

- `approvals.InMemoryApprovalsRepository(initialRequests, clock, demoRequestLifetimeMs)`
- `aliases.InMemoryAliasesRepository(initialAliases, clock)`
- `security.InMemorySecuritySummaryProvider(initial)`; `update(summary)` publishes live data
- `settings.InMemorySettingsRepository(initial)`

All support no-argument construction. Approvals and aliases start empty; demo
requests expire after five minutes. Default security statistics are explicitly
simulated (12 total, 3 weak, 4 reused). Use constructor values and clocks in tests.
Mutation locks/atomic StateFlow updates prevent lost concurrent writes.

## Limitations

- Demo records/preferences are session-only; process death resets them. The module
  supplies no persistent encryption, DataStore implementation or second master password.
- Demo requests are local simulations. Approving them authorizes no external login.
- Demo aliases use reserved `example.com` addresses. They cannot receive or forward
  mail. Enable/disable changes the local record only.
- Dashboard values come entirely from the injected provider. No breach lookup or
  external scanning is implemented.
- Security duration/visibility controls are preferences until the host enforces them.
- No external integration is silently connected. Persistent repositories and real
  service providers must be supplied by the application owners.

## Verification

Unit tests: `./gradlew :feature-tools:testDebugUnitTest` in the host Android root.
With an API 26+ emulator/device:
`./gradlew :feature-tools:connectedDebugAndroidTest`.

`src/androidTest` exercises the exact public routes with injected local repositories:
approval creation/decisions/expiry/history, alias validation/search/clipboard/toggle/
details/confirmed deletion, dashboard loading/live/empty/error/retry, settings/theme,
and repeated route switching. Three additional mutation-failure/retry checks protect error states.
A gallery test captures and validates all four screens plus local dialogs/history in light and dark mode. JVM tests cover data invariants and concurrent writes.

The independent verification app is kept separately in the workspace's ignored
`verification/` directory; it is not part of this library or the main application.
See `VERIFICATION.md` for commands actually executed and the resulting evidence.
