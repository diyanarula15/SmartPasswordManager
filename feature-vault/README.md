# feature-vault — Password Vault + Password Generator (Developer 2)

Android library module, Kotlin + Jetpack Compose Material 3, `minSdk 26`, `compileSdk 35`.
Namespace / package: `com.legenddrry.feature.vault`.

## Public API (frozen)

```kotlin
package com.legenddrry.feature.vault

@Composable fun EntriesRoute(repository: VaultRepository)
@Composable fun GenerateRoute(repository: VaultRepository)
```

Both take Developer 1's `com.legenddrry.core.VaultRepository`. Every create/update/delete goes
through that interface. The module stores nothing itself: no database, no files, no prefs, no logging
of secrets. Encryption is up to the core implementation.

## Integration steps (Developer 1's project)

1. Copy the `feature-vault/` folder to the project root.
2. In the root `settings.gradle.kts`, add:
   ```kotlin
   include(":feature-vault")
   ```
3. Make sure the root `build.gradle.kts` declares these plugins (versions as in your project):
   `com.android.library`, `org.jetbrains.kotlin.android`, `org.jetbrains.kotlin.plugin.compose`.
   Built and tested with AGP 8.7.3, Kotlin 2.0.21 and Gradle 8.10.2.
4. `feature-vault/build.gradle.kts` depends on `implementation(project(":core"))`. If your core
   module has a different Gradle path, change only that line.
5. In `:app`, add `implementation(project(":feature-vault"))` and call the routes from your
   navigation:
   ```kotlin
   composable("entries") { EntriesRoute(repository = vaultRepository) }
   composable("generate") { GenerateRoute(repository = vaultRepository) }
   ```
   Pass the same `VaultRepository` instance to both routes, so a password saved from the
   generator shows up in the vault list.
6. The routes don't apply a theme. They use `MaterialTheme` colours from the host app, which works
   in both light and dark mode. They also don't draw a global bottom bar. Navigation inside the
   module (detail, editor, confirmations) uses internal state, dialogs and sheets.

Each route keeps its ViewModel (`VaultViewModel` / `GeneratorViewModel`) under a fixed key in the
nearest `ViewModelStoreOwner`. With Navigation Compose that owner is the back-stack entry.

## Dependencies the module adds
Compose BOM 2024.12.01 (ui, material3, material-icons-extended, ui-tooling-preview),
lifecycle-viewmodel-compose and lifecycle-runtime-compose 2.8.7, kotlinx-coroutines-android 1.8.1.
For tests: junit 4.13.2, kotlinx-coroutines-test 1.8.1, compose ui-test-junit4, androidx.test runner and ext-junit.

## Features
- **Vault:** list, search (title / website / username / notes / category), category filter chips,
  add, edit, delete with confirmation, detail view, show/hide password, copy username/password
  (the clip is marked sensitive on Android 13+), strength indicator, duplicate-password warning
  (badge in the list, card in the detail view, inline in the editor), loading / empty / no-results /
  error + retry states, and form validation.
- **Generator:** `SecureRandom`, length 12–32, upper/lower/digits/symbols toggles, every enabled
  category guaranteed to appear (one character from each, then a Fisher–Yates shuffle), regenerate,
  copy, strength meter, and save as a new vault entry through the shared editor.

## Tests
- JVM unit tests: `./gradlew :feature-vault:testDebugUnitTest` cover the generator, the strength
  evaluator, validation, filtering and duplicates, and both ViewModels against an in-memory fake
  repository.
- Instrumented Compose UI tests: `./gradlew :feature-vault:connectedDebugAndroidTest` cover the
  end-to-end flows on a device or emulator against an in-memory fake repository.

The fake repositories exist only in the test source sets. They are plain in-memory lists that are
neither encrypted nor persisted.

## Verified results (2026-11-17, harness: AGP 8.7.3, Kotlin 2.0.21, emulator API 35)
- `:feature-vault:testDebugUnitTest`: **68 tests, 0 failures**
  - PasswordGeneratorTest: 11
  - PasswordStrengthEvaluatorTest: 11
  - EntryDraftTest: 11
  - VaultLogicTest: 13
  - VaultViewModelTest: 12
  - GeneratorViewModelTest: 10
- `:feature-vault:connectedDebugAndroidTest`: **34 tests, 0 failures**
  - EntriesRouteTest: 22, covering add/edit/delete, validation, search, category filter, show/hide,
    copy, duplicates, and the loading, empty and error states with retry
  - GenerateRouteTest: 12, covering default 16 characters, regenerate, category toggles, the
    last-category guard, slider 12/32, copy, and save to vault
- `:feature-vault:assembleRelease` builds `feature-vault-release.aar`.

Note on the clipboard tests: on Android 10+ an app can't read the clipboard while another window
has focus, and the Android 13+ clipboard overlay is one such window. So the instrumented tests check
what was written through `copyObserverForTest` in `ui/common/Clipboard.kt`, which is null in
production. They also compare against the system clipboard whenever the OS allows the read.
