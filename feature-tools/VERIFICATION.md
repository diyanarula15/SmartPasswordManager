# Sage & Clay verification — 17 November 2026

All four redesigned production routes were compiled and exercised independently
using a separate Android test app. No main app, authentication, vault, or generator
source was changed.

Environment: JDK 17, Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01,
compile SDK 35, min SDK 26. Device: dedicated API 35 emulator, 540×1200 at 210dpi.

## Final executed results

| Check | Result |
| --- | --- |
| Agent-owned Kotlin/JUnit regressions | Approvals 18, aliases 10, settings/security 17 passed |
| Integrated unit regressions | 45 tests, 0 failures/errors/skips |
| Redesigned feature/device suite | 11 tests, 0 failures/errors/skips |
| Standalone launcher workflow | 1 test, 0 failures/errors/skips |
| Standalone debug APK | Built and packaged |
| Release library AAR | Built on both standalone and current production toolchains |
| Android lint | 0 errors, 16 advisories |
| Light/dark gallery | 16 genuine emulator PNGs generated and signature-validated by the test |
| Exact rendered ground tokens | All four light screenshots match #F3F4EC; all four dark screenshots match #121814 at the sampled background pixel |
| Contract/static review | Four routes and repository contracts unchanged; no production palette constants or MaterialTheme installation |

**57 automated tests passed.** Existing functional assertions were retained; only
intentional sentence-case copy expectations and the test host's theme changed.
New checks cover mutation failures/retry in approvals, aliases and settings.

Lint advisories: 4 newer dependency versions and 12 version-catalog suggestions.
Dependencies remain explicit and self-contained so the module can integrate without
assuming Developer1's catalog aliases. No warnings indicate a runtime/resource/API
error.

Developer 1 subsequently migrated the production build file to AGP 9.5.1 / Gradle
9.6.0 / SDK 37 and the main app version catalog. That setup was preserved. The
standalone harness now explicitly selects its own pinned build script for the same
real source directory; its APK/AAR assembly has also passed after that adaptation.
Host validation also passed against the current production configuration:
`compileDebugKotlin` and 45 unit tests succeeded in 1m 31s; `assembleRelease` succeeded
in 45s. These tasks used a scratch project cache/output directory via an init script,
so no other developer's app/core/vault build outputs were modified.

## What was exercised

- Approvals: demo creation, approve/deny, repeated-transition protection, expiration,
  history, details, error feedback/recovery, concurrent repository writes.
- Aliases: validation, create/search, real OS clipboard write/readback, enable/disable,
  details, cancellation/confirmation of deletion, empty state, failed create/retry,
  unique identities and the form's Done keyboard action.
- Dashboard: loading, empty values, exact counts, live updates, provider error/retry,
  recommendations and no unsupported breach claims.
- Settings: defaults, every preference, actual light/dark/system theme changes,
  save failure/recovery and information dialogs.
- Navigation: all four public routes, retained session data across route switches,
  actual standalone launcher and appearance changes.

The gallery renders the production routes inside a test-only theme using the guide's
exact palettes. It captures four screens, approval details/history and alias
create/details in both appearances. These are original emulator captures; no image
recolouring, compositing or generated mockups were used. Gallery setup clears the
previous clipboard test's system overlay and uses the field's real Done action.

Visual review caught Material 3's default tonal-button use of secondary colours; the
shared buttons now explicitly use sage primary-container roles, preserving one clay
focal element per screen. Shared fields explicitly expose working Done/Search actions.

## Reproduce

Unpack the separate verification harness alongside `feature-tools/`:

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/Android/sdk
export ANDROID_SERIAL=emulator-5570
./verification/gradlew -p verification :feature-tools:testDebugUnitTest :app:assembleDebug
./verification/gradlew -p verification :feature-tools:connectedDebugAndroidTest :app:connectedDebugAndroidTest \
  -Pandroid.injected.device.serial=emulator-5570
./verification/gradlew -p verification :feature-tools:lintDebug :feature-tools:assembleRelease
```

The final combined executed command included unit tests, both device tasks, lint,
and the release AAR. It completed successfully in 1m 31s. Detailed logs, reports,
source/harness archives, APK/AAR and the gallery are in `artifacts/tools-sage-clay/`.

The production module always inherits the host theme. Reference palettes are only
in test/preview sources. See `DESIGN_NOTES.md` for changed files and the legacy theme
helper's source-compatible migration.


Current host-toolchain commands (read production configuration; isolate outputs):

```sh
JAVA_HOME=/path/to/jdk21 bash ./gradlew :feature-tools:compileDebugKotlin :feature-tools:testDebugUnitTest \
  --no-configuration-cache --max-workers=2 \
  --project-cache-dir /tmp/legenddrry-tools-host-check/project-cache \
  -I /tmp/legenddrry-tools-host-check.init.gradle
JAVA_HOME=/path/to/jdk21 bash ./gradlew :feature-tools:assembleRelease \
  --no-configuration-cache --max-workers=2 \
  --project-cache-dir /tmp/legenddrry-tools-host-check/project-cache \
  -I /tmp/legenddrry-tools-host-check.init.gradle
```

The scratch init script only redirects `:feature-tools`'s buildDirectory to
`/tmp/legenddrry-tools-host-check/build`. The main project's daemon toolchain criteria
select its configured JDK; no host app source or global build configuration is changed.
The current-toolchain AAR is included in the delivery, alongside the independently
runnable SDK 35 demo APK and harness.
