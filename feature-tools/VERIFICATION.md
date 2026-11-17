# Verification record — 17 November 2026

All four routes compiled and their interactive workflows were exercised on Android
API 35. All production source is inside `feature-tools/`. The standalone Android
application remains separately in the ignored `verification/` directory and is
excluded from the integration source archive.

Environment: JDK 17, Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, SDK 35, min SDK 26.
The local SDK was `/opt/homebrew/share/android-commandlinetools`.

## Executed checks

| Check | Result |
| --- | --- |
| Agent-owned independent Kotlin/JUnit runs | Approvals 18, aliases 10, settings/security 17 passed |
| Integrated `:feature-tools:testDebugUnitTest` | 45 tests; zero failures/errors/skips |
| `:app:assembleDebug` | Passed; standalone demo APK generated |
| `:feature-tools:assembleDebugAndroidTest` | Passed |
| `:feature-tools:connectedDebugAndroidTest` | 7 tests; zero failures/errors/skips |
| `:app:connectedDebugAndroidTest` | 1 test; zero failures/errors/skips |
| `:feature-tools:lintDebug` | Passed: zero errors, 4 dependency-version advisories |
| `:feature-tools:assembleRelease` | Passed; release AAR generated |
| Final configuration/build check | Passed after moving test target SDK into `testOptions` |

The dependency advisories concern newer lifecycle and Android test library versions.
The tested versions remain pinned for reproducibility and host compatibility.

The first device run found the library test APK's implicit legacy target SDK caused
Android to show a system dialog, stealing focus and denying clipboard reads. The
module now explicitly sets `testOptions.targetSdk = 35`, and the clipboard test waits
for activity window focus before reading the real OS clipboard. The final seven-test
feature suite passed, including clipboard readback. No clipboard mock was substituted.

## Coverage

- Approvals: demo creation, approve, deny, expiry, repeated-transition prevention,
  pending/history changes, request details, concurrency and unique identities.
- Aliases: validation, creation, search, real Android clipboard copying, enable/disable,
  details, cancel/confirm deletion, empty lists and duplicate-ID prevention.
- Dashboard: loading, empty counts, live provider updates, exact counts, provider
  error/retry and recommendations. No stored password is displayed or logged.
- Settings: defaults, every preference, light/dark/system selection, actual theme color
  changes, information dialogs, atomic updates and custom injected duration values.
- Host app: launch, all four public routes, repeated navigation, theme propagation,
  and shared session data retained across route changes.

## Reproduce

From the USS workspace, with the separate harness present:

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/Android/sdk
export ANDROID_SERIAL=emulator-5554 # Choose the intended device explicitly.
./verification/gradlew -p verification :feature-tools:testDebugUnitTest :app:assembleDebug
./verification/gradlew -p verification :feature-tools:connectedDebugAndroidTest :app:connectedDebugAndroidTest
./verification/gradlew -p verification :feature-tools:lintDebug :feature-tools:assembleRelease
```

Local executed build logs and reports are packaged under `artifacts/tools/`. The
independent harness is packaged separately; unpack it alongside `feature-tools/`
and set `ANDROID_HOME` (or create its own untracked `local.properties`).

The device-test task uninstalls its test applications on completion. To explore
manually, install the debug APK again. Use a separate emulator per developer to avoid
foreground-window and resource contention during simultaneous manual checks.

Only the library and its documented repository interfaces are intended for the main
application. Main authentication, encryption/storage, vault CRUD, and global navigation
remain under their existing developers' ownership.
