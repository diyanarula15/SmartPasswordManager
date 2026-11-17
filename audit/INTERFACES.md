# Application audit boundaries

This audit uses the existing four-module application skeleton. Do not replace it.
The current user-authorized audit supersedes old worktree-only instructions in
TRANSFER_CONTRACT.md; those describe a completed implementation session.

## Frozen interfaces

- `core/VaultContract.kt`: preserve all eight VaultEntry fields and VaultRepository signatures.
- `core/auth/AuthManager.kt`: preserve auth states/results and public signatures.
- `core/transfer/TransferContract.kt`: preserve portable schema, limits, modes and store signatures.
- `feature-tools/.../contracts/ToolContracts.kt`: preserve all repository and preference APIs.
- Preserve public feature routes, ToolsDependencies, composition providers and transfer-manager API.
- Keep dependency direction app -> core/features and feature-vault -> core; no cross-feature dependency.
- No disk-format migration, algorithm/parameter change, or new third-party dependency without root review.
- Use local fakes/mocks for other owners' repositories; never modify another owner's files.

## Exclusive ownership

1. core-storage: core auth/crypto/storage/vault implementation and their tests; excludes transfer/.
2. transfer: core transfer/ implementation/tests plus app backup/ implementation/tests.
3. vault-feature: entire feature-vault/ except build.gradle.kts.
4. tools-feature: entire feature-tools/ except build.gradle.kts and frozen contracts.
5. persistence: app integration/ implementation and tests.
6. shell-security: app security/, navigation/, ui/auth/, MainActivity, their tests.
7. root: shared contracts, build files, DI, MainShell/theme, manifests, app E2E tests, audit report.

Only three child agents may run concurrently. Owners 4-6 start as slots free.
Owners report concrete defects, fixes, tests actually executed, and unresolved limits.
Use audit/gradle-isolation.init.gradle with -Daudit.owner=<owner> and
--project-cache-dir /tmp/uss-audit-<owner>/project-cache for independent Gradle builds.
Do not run device tests concurrently. Root owns emulator lifecycle and end-to-end testing.
Do not commit, push, delete branches, or alter other worktrees.
