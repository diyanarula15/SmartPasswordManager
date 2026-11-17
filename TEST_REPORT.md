# LegenDDRRY test report (2026-11-17)

Environment: macOS, JDK 21 (Gradle daemon JDK 25), Gradle 9.6.0, AGP 9.5.1, emulator `emulator-5554` (API 35). The
emulator was shared with other work; an unrelated demo app produced ANR dialogs during some runs.
Only tests that were actually executed are marked Pass.

## Build
| Test | Expected | Actual | Result | Evidence |
|---|---|---|---|---|
| `./gradlew :app:assembleDebug` (all 4 modules) | APK built | BUILD SUCCESSFUL | Pass | `app/build/outputs/apk/debug/app-debug.apk` (~18.8 MB) |
| Module resolution / imports / manifests | No errors | No compile errors, no manifest conflicts | Pass | same build |

## Unit tests (`./gradlew testDebugUnitTest`)
| Module | Tests | Failures | Result | Covers |
|---|---|---|---|---|
| core | 22 | 0 | Pass | setup, correct/wrong unlock, too-short password, lockout, locked vault access prevented, add/edit/delete, emissions, persistence across new instances, no plaintext on disk, corrupt-file handling |
| feature-vault | 68 | 0 | Pass | generator length/categories/randomness, strength, validation, search/filter, duplicates, ViewModels |
| feature-tools | 45 | 0 | Pass | approval transitions and expiry, alias CRUD, security state, settings |
| app | 23 + 21 | 0 | Pass | settings persistence, persistent aliases, vault-backed dashboard stats; shell navigator back rules, auto-lock controller |

## Instrumented tests (integrated app on emulator)
| Test | Expected | Actual | Result |
|---|---|---|---|
| test01 setup: short/mismatched rejected, then lands in vault | errors shown, then vault | as expected | Pass |
| test02 lock, wrong password rejected, Back doesn't reveal vault, correct password unlocks | access denied, then restored | as expected | Pass |
| test03 vault: add "Demo Account", search, detail, edit username, show/hide, copy | all work | as expected | Pass |
| test04 generator: length 20, all classes, copy, save, appears in Vault tab (shared repository) | entry appears | as expected | Pass |
| test05 security dashboard: real total, reused count updates after adding a reused password | real counts | as expected | Pass |
| test06 approvals: create, approve, create, deny, history, approved can't be re-approved | correct transitions | as expected | Pass |
| test07 aliases: create, search, copy, disable/enable, delete | all work | Pass after fixes (see below) | Pass |
| test08 settings: dark/light theme, auto-lock, clipboard, show passwords, Back | values stored in settings repository | as expected | Pass |
| test09a/b/c navigation: all tabs repeatedly, single NavigationBar, Back to Vault, More sub-screens | correct | as expected | Pass |
| core `KeystoreVaultInstrumentedTest` (2) | real Keystore round trip, real-file persistence | passed (core worker run) | Pass |
| feature-vault instrumented suite (34) | | not re-run on the integrated build | Not executed |
| feature-tools `FeatureRoutesTest` / gallery | | not re-run on the integrated build | Not executed |

Failures found and fixed along the way:
1. test07 crashed with `NoSuchMethodError: FlowRow`. feature-tools compiled against Compose BOM 2024.12.01 while the
   app ships 2026.02.01. Fixed by using the catalog BOM in `feature-tools/build.gradle.kts`.
2. test07 timed out re-enabling or deleting an alias. The test's coordinate taps landed on the floating "create alias"
   button that overlaps the bottom of the scrolled list. Fixed in the test, which now uses semantic click actions. No
   product code was changed.

## Manual on-device checks
| Test | Result | Notes |
|---|---|---|
| Fresh install → master password setup → empty vault | Pass | Screens rendered in Sage & Clay |
| Add credential "Demo Account" through the editor | Pass | appears in list |
| Process kill → relaunch → unlock → credentials still present (Test H) | Not executed on device | Stopped on request. Persistence across new instances over the same encrypted files is covered by the core unit tests and the core instrumented test. |
| Rotation, small screen, keyboard-heavy forms | Not executed | |

## Known gaps
- On-device restart persistence (Test H) and rotation weren't run manually. See above for the automated coverage.
- The "show passwords" setting is stored but not applied by the vault module (it always opens entries hidden).
