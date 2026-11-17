# LegenDDRRY integration report

Branch: `feature/tools` (local). It contains GitHub `origin/main`, Developer 1's base project, merged in. Nothing has been
pushed. See `INTEGRATION_AUDIT.md` for the starting state.

## Modules integrated
| Module | Owner | State |
|---|---|---|
| `app` | Developer 1 base + integration | Template replaced by the auth gate, navigation shell, theme, DI and adapters. Package/applicationId `com.example.group4app` kept. |
| `core` | built during integration (missing from Developer 1's push) | Encrypted vault + AuthManager implementing the frozen `com.legenddrry.core` contract |
| `feature-vault` | Developer 2 | Unchanged source; build file migrated to AGP 9 |
| `feature-tools` | Developer 3 (incl. Sage & Clay redesign commit `7386550`) | Unchanged source; build file migrated to AGP 9 |

## Gradle / build changes
- `settings.gradle.kts`: added `:core`, `:feature-vault`, `:feature-tools`.
- `build.gradle.kts`, `gradle/libs.versions.toml`: declared `com.android.library`, plus catalog entries for coroutines,
  lifecycle compose, icons, test runner and org.json.
- `feature-vault/build.gradle.kts`, `feature-tools/build.gradle.kts`: removed `org.jetbrains.kotlin.android` and
  `kotlinOptions`, which AGP 9's built-in Kotlin rejects. compileSdk is now 37 to match the app.
- `app/build.gradle.kts`: depends on the three modules, Java 11 → 17 (the modules target 17), lifecycle compose,
  coroutines and test dependencies.
- `app/src/main/AndroidManifest.xml`: `android:name=".LegenDDRRYApplication"`, `allowBackup="false"`.
- App label "SmartPasswordManager" → "LegenDDRRY".

## Contract adapters added (app/src/main/java/com/example/group4app/integration)
- `VaultSecuritySummaryProvider`: tools `SecuritySummaryProvider` computed from the real `VaultRepository`
  (total, weak = strength below Strong via Developer 2's `PasswordStrengthEvaluator`, reused = entries sharing an exact
  password). Runs on Dispatchers.Default, `isDemo = false`. Replaces the tools module's fake demo provider.
- `PreferencesSettingsRepository`: tools `SettingsRepository` persisted in SharedPreferences `legenddrry_settings`.
- `FileAliasesRepository`: tools `AliasesRepository` persisted as JSON in `files/aliases.json`. Writes are atomic,
  and a corrupt file is preserved as `.corrupt`.

## Repository wiring
`di/AppContainer` is created once in `LegenDDRRYApplication`. **One** `VaultRepository` instance (from
`CoreContainer`) is passed to `EntriesRoute`, `GenerateRoute` and `VaultSecuritySummaryProvider`, so generator saves
appear in the vault and the dashboard updates immediately. The tools routes get `ToolsDependencies(approvals,
aliases, security, settings)` through `ProvideTools`. Approvals stay `InMemoryApprovalsRepository`, because no real
login-approval backend exists (demo).

## Core security implementation (`core/`)
- Key derivation: PBKDF2WithHmacSHA256, 16-byte random salt, 310,000 iterations (stored per vault), 256-bit key.
- A random 256-bit data key is wrapped with AES-256-GCM. The AAD binds the KDF parameters. A failed GCM tag means
  WRONG_PASSWORD. No password or password hash is stored.
- `meta.bin` is additionally encrypted with a non-exportable Android Keystore AES-256-GCM key.
- `vault.bin` is AES-256-GCM over the JSON entries, with a fresh IV per write, written atomically (temp + fsync +
  rename). Files are in `noBackupFilesDir/legenddrry`.
- The key exists only in memory while unlocked. `lock()` zeroes it, and a process restart always starts LOCKED.
- 5 wrong attempts trigger a 30 s lockout (counted in memory).

## Navigation wiring (app/navigation, app/ui/shell)
- Auth gate on `AuthState`: LOADING → spinner, NEEDS_SETUP → master password setup, LOCKED → unlock,
  UNLOCKED → `MainShell`.
- `MainShell`: one Material 3 NavigationBar (Vault, Generate, Approvals, Aliases, More). More opens Security
  dashboard, Settings, About and "Lock now". State-based navigation (`ShellNavigator`, pure and unit-tested) with
  `rememberSaveableStateHolder` per tab.
- Back: sub-screen → More; other tab → Vault; Vault → exit. Locking removes the shell from composition, so
  Back cannot return to protected screens.
- **Session-scoped ViewModels** (`SessionViewModelScope`): the feature modules' `viewModel()` instances live in a
  store that is cleared whenever the state leaves UNLOCKED. That removes decrypted state (search results, the last
  generated password) from memory on lock, while still surviving rotation.

## App-level security behaviours (app/security)
- `FLAG_SECURE` on MainActivity. A debuggable build can disable it for QA screenshots with
  `files/qa_allow_screenshots`.
- `AutoLockController`: locks on return to the foreground once the configured auto-lock minutes have passed in the
  background.
- `ClipboardGuard`: clips marked `IS_SENSITIVE` (the vault's and generator's password copies) are cleared after the
  configured clipboard seconds.
- Theme: `Settings → Theme` (system/light/dark) drives the app-wide `SmartPasswordManagerTheme` (Sage & Clay light
  and dark schemes, Manrope from feature-tools' fonts) and the system bar icon colours.

## Bugs found and fixed during integration
1. AGP 9 rejected `kotlin-android` and `kotlinOptions` in both feature modules. Fixed in their build files.
2. JVM target 11 vs 17 mismatch. App moved to 17.
3. `com.android.library` plugin was not declared. Added to the catalog and root.
4. Feature ViewModels outlived a lock and kept decrypted data in memory. Fixed with a session ViewModel store.
5. The tools dashboard showed fake demo numbers. Replaced with the vault-backed provider.
6. Settings and aliases were lost on restart. Replaced with persistent adapters.
7. `allowBackup=true` was disabled for the password manager.
8. Vault clipboard tests couldn't read the clipboard on API 33+. Fixed earlier with a test-only observer seam.

## Known limitations
- Login approvals are a local demonstration. Nothing authenticates any real service, and requests reset on app
  restart.
- Email aliases are local demonstration records. No email is received or forwarded.
- The "show passwords" setting is stored, but the vault module always opens entries hidden. The vault module has no
  input for it, and its UI is owned by Developer 2.
- The wrong-password lockout counter resets on process restart. PBKDF2 cost remains the protection in that case.
- The background clipboard clear relies on a coroutine timer. If Android freezes the cached process, the clear
  happens when the process resumes.
- The animated splash screen hasn't been implemented (out of scope for this phase).
