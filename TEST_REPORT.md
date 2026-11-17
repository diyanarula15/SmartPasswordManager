# LegenDDRRY combined application audit

18 November 2026 (Asia/Kolkata). Branch: `feature/offline-vault-transfer`.

**Final build succeeded. All 222 JVM tests and 70 distinct ordinary device tests passed, plus both externally coordinated process-restart phases.**

## Environment and evidence

Android 15/API 35 private emulator `emulator-5570`; Gradle 9.6.0; AGP 9.5.1; Compose BOM 2026.02.01. The emulator was offline during testing. Build outputs and project cache were isolated under `/tmp/legenddrry-transfer-build/`. The other developer's build and emulator were not modified.

The original `emulator-5554` was inspected read-only: physical1080×2400/density420, override540×1200/density210, rotation0. Its override preserves the same logical screen dimensions. No reset was applied to that shared device.

Evidence is copied to `artifacts/offline-vault-transfer-audit/` in the original workspace, including APK, XML results, initial/final logs, restart logs and checksums. Original temporary logs:

- `/tmp/legenddrry-combined-audit-initial.log`
- `/tmp/legenddrry-rotation-rerun.log`
- `/tmp/legenddrry-restart-seed.log`, `/tmp/legenddrry-restart-launch.log`, `/tmp/legenddrry-restart-verify.log`

## JVM tests

| Module | Passed | Failures/errors/skips |
|---|---:|---:|
| core | 56 | 0 |
| feature-vault | 68 | 0 |
| feature-tools | 45 | 0 |
| app | 53 | 0 |
| **Total** | **222** | **0** |

Coverage includes auth/lockout, encrypted CRUD, persistence/corruption, backup codec/manager and all import modes, atomic rollback, cancellation/lock races, generator/validation, approval expiry/decisions, aliases, settings and live security statistics. JVM crypto uses software key-wrapper doubles; device tests below use real Android Keystore.

## Device tests

| Module | Distinct ordinary tests passed | Scope |
|---|---:|---|
| core | 3 | Two Keystore tests; one 20-record portable transfer test |
| feature-vault | 39 | Entries22, generator12, show-password options5 |
| feature-tools | 11 | Routes10, light/dark gallery1 |
| app | 17 | Main flows7, tools4, lifecycle/layout4, native SAF1, context1 |
| **Total** | **70** | **No unresolved failures** |

The initial combined run recorded72 cases:69 passed,1 failed,2 intentionally skipped. The sole failure was a new lifecycle assertion that used a generic text helper: it returned the outlined field's label when EditableText was empty. The corrected assertion requires actual EditableText to exist and be empty. Its focused rerun passed, including rotation, generated-draft recreation, saving, locking and draft removal. Production code was not changed to bypass the assertion. The initial failure log/XML are retained separately.

The focused command `:app:connectedDebugAndroidTest :app:assembleDebug` completed **BUILD SUCCESSFUL** (53s). No broad rerun was needed after this assertion-only correction; all other tests had passed against the same production sources.

The two process-phase methods are intentionally skipped in an ordinary suite because they require an external process kill between them. Both were then executed separately and passed; skipped cases are not counted as passes above.

## End-to-end checks

| Check | Genuine result |
|---|---|
| Setup validation; wrong password; lock; Back cannot expose vault | Passed |
| Vault add/search/detail/edit/reveal/copy; generator copy/save; tab/back navigation | Passed |
| Approvals create/approve/deny/history; invalid repeat decisions | Passed; expiry also covered by tools JVM tests |
| Aliases create/search/copy/toggle/delete | Passed using real touch interactions, including previously covered FAB controls |
| Security real counts update when vault changes | Passed |
| Settings light/dark/system controls, auto-lock, clipboard and show passwords | Passed; light/dark gallery also passed |
| Show-password preference reaches actual detail/editor, both directions; manual toggle | Passed in combined app, plus five module device tests |
| Saved entry/settings and unlocked session survive Activity recreation | Passed |
| Actual landscape rotation retains unsaved editor draft; generator draft retains original password across recreation | Passed |
| Lock discards unsaved secret drafts | Passed with strict empty EditableText assertions |
| Actual320dp activity width, settings dialogs/actions, dashboard metrics and navigation | Passed; window size restored in finally |
| Empty/loading/error/retry states | Passed in module device/JVM suites |
| Force-stop, launch, authentication, saved records/edits/deletion/settings/alias survive | Passed, both external phases `OK (1 test)` |
| Native Android DocumentsUI export/import | Passed on local Downloads provider |

### Actual process restart

The seed phase creates three synthetic encrypted records, edits one (password, username, notes, category, timestamp), deletes another, sets DARK/15-minute lock/60-second clipboard/show-passwords, and creates a disabled alias. The external runner executes `am force-stop com.example.group4app`, followed by `am start -W -n com.example.group4app/.MainActivity`. The verify phase requires LOCKED, unlocks through the real UI, checks the exact remaining IDs/edited fields and persisted settings/disabled alias, and verifies show-password behavior in the real detail screen. Both phases passed without resetting storage between them.

### Portable backup and real file picker

The core device test uses20 Unicode records, both backup password methods, different local master passwords and independent real Android Keystore aliases. It deletes the source key before restore. Wrong passwords/tampered ciphertext leave destination data/files unchanged; restored fields persist after reopening the destination vault.

The app SAF test saves a transfer-password-encrypted `.vault` through native DocumentsUI into Downloads, independently checks ciphertext, deletes its synthetic source record, cancels a real import picker, reopens the saved file, previews without writes, rejects a wrong destination master password, then restores every field with the correct password. Its unique synthetic document is removed afterwards.

## Bugs fixed

1. Vault and persistent alias writes could commit to disk but leave stale memory if cancellation occurred while returning from IO. Subsequent writes could lose committed changes. Publication now occurs alongside the successful write before that cancellation boundary. Deterministic regressions passed.
2. Settings published success before durable storage. They now perform a checked SharedPreferences commit on IO before publication; failed-write regression passed.
3. Unsaved vault/generator editor drafts disappeared on rotation. Session-owned ViewModels preserve initial/current drafts, including generated passwords, and clear on cancel/save/lock. Secrets are not saved in Bundles.
4. The alias FAB intercepted touches on scrolled controls. A reserved bottom action strip keeps controls reachable; the prior semantic-click workaround was removed from the integrated regression.
5. Tools gallery test compilation lacked explicit Activity/Core APIs. Test dependencies now match the host catalog. Core/vault standalone test APK targets avoid legacy-target warning dialogs; production app target remains unchanged.

## Integration and limits

The branch includes the tools and show-password changes plus offline backup and this audit. Public tools routes and repository contracts remain compatible. Keep the app's `LocalVaultDisplayOptions` and transfer-section providers, and the existing session ViewModelStore owner that clears on lock. See `OFFLINE_VAULT_TRANSFER.md` and `TRANSFER_CONTRACT.md` for transfer integration/format details. No global navigation or startup animation was added.

Coverage is one API35 emulator. Local Downloads SAF passed; other document providers, provider write-failure cleanup and physical two-device transfer were not exercised. Independent Keystore portability was exercised on device. Backup dialogs cancel on rotation and require restarting that operation; vault editor drafts survive rotation. Approvals remain demonstrations, aliases do not forward email, and security statistics make no breach-detection claim.
