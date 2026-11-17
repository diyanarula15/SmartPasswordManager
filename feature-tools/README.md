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

The root build must supply these plugins (or compatible versions matching the app):

```kotlin
plugins {
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

Repositories: `google()` and `mavenCentral()`. Build with JDK 17, Gradle 8.10.2,
Android SDK 35. The app adds `implementation(project(":feature-tools"))` and its
existing Compose/Material dependencies. This module's dependency list is in
`build.gradle.kts`: Compose BOM 2024.12.01, Material 3, Foundation, UI,
lifecycle-runtime-compose and lifecycle-viewmodel-ktx 2.8.7, coroutines-android 1.9.0.
JUnit and Android Compose test dependencies are test-only.

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

// Inside the app's composition:
ProvideTools(tools) {
    // Optional: applies theme preference immediately. Or observe the same
    // repository from the existing app theme instead.
    LegenDDRRYToolsTheme {
        // Existing app navigation invokes one of the four routes.
        SettingsRoute()
    }
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
durations are supported by the settings UI. Theme controls work immediately when
the host uses the wrapper or observes preferences in its own theme. The host must
actually implement auto-lock, clipboard clearing, and vault password visibility.

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
and repeated route switching. JVM tests cover data invariants and concurrent writes.

The independent verification app is kept separately in the workspace's ignored
`verification/` directory; it is not part of this library or the main application.
See `VERIFICATION.md` for commands actually executed and the resulting evidence.
