# LegenDDRRY: Smart Password Manager (Android)

A native Android password manager built with Kotlin, Jetpack Compose and Material 3, using the Sage & Clay design
language.

## Project structure

```
.
├── app/            Developer 1 base app (com.example.group4app): MainActivity, auth gate, navigation shell,
│                   Sage & Clay theme, security behaviours, integration adapters, DI container
├── core/           Shared contract + security: VaultEntry/VaultRepository, AuthManager, encrypted vault
├── feature-vault/  Developer 2: password vault (list/search/filter/detail/add/edit/delete) + generator
├── feature-tools/  Developer 3: login approvals (demo), email aliases (demo), security dashboard, settings
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml   (root build, version catalog)
├── INTEGRATION_AUDIT.md / INTEGRATION_REPORT.md / TEST_REPORT.md
└── dev-harness/, verification/, artifacts/   temporary standalone test scaffolding (not part of the app build)
```

Module dependencies: `app → core, feature-vault, feature-tools`; `feature-vault → core`. `core` depends on no
feature module, and the two feature modules don't depend on each other.

## Requirements
- Android Studio with AGP 9.5 support, JDK 17 or newer (the Gradle daemon toolchain is JDK 25, which the foojay
  resolver can download).
- Android SDK platform 37, minSdk 26.
- Gradle 9.6.0 (provided by the wrapper).

## Build and install

```bash
./gradlew :app:assembleDebug
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tests:

```bash
./gradlew testDebugUnitTest
```

```bash
./gradlew connectedDebugAndroidTest
```

In Android Studio: **Open** the repository root, let Gradle sync, then run the `app` configuration.

## Supported functionality
- **Master password:** first-time setup, unlock, wrong-password rejection, a 30-second lockout after 5 failures,
  "Lock now", and auto-lock after the configured time in the background. A process restart always requires unlocking.
- **Encrypted vault:** the key is derived with PBKDF2-HMAC-SHA256 (310,000 iterations, random salt) and wraps a random
  AES-256 data key. Entries are encrypted with AES-256-GCM. The key metadata is also encrypted with a
  non-exportable Android Keystore key. Files live in `noBackupFilesDir`, and app backup is disabled. Neither the
  master password nor a hash of it is stored.
- **Vault:** add, edit, delete (with confirmation), search, category filters, a detail view, show/hide password,
  copy username/password, a strength indicator, and duplicate-password warnings.
- **Generator:** `SecureRandom`, length 12–32, four character classes (each enabled class is guaranteed), copy, and
  save to the vault. Saved entries go into the same encrypted repository.
- **Security dashboard:** live statistics computed from the real vault: total, weak (strength below "Strong") and
  reused passwords.
- **Settings:** theme (system / light / dark, applied app-wide), auto-lock minutes, clipboard-clear seconds, and a
  password-visibility preference. Stored in app-private SharedPreferences and kept across restarts.
- **Clipboard:** password copies are marked sensitive and cleared after the configured number of seconds.
- **Screen security:** `FLAG_SECURE` blocks screenshots and the recents preview.

## Demonstration-only features
- **Login approvals** are local demo requests. They don't authenticate any real website or device, and they live
  only in memory, so they reset when the app restarts.
- **Email aliases** are demo records (`demo.<id>@example.com`), saved locally. No email is received or forwarded.

## Known limitations
See `INTEGRATION_REPORT.md` and `TEST_REPORT.md`.
