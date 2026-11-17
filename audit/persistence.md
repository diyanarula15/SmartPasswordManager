# App persistence and security-summary audit

Reviewed FileAliasesRepository, PreferencesSettingsRepository and its SharedPreferences
adapter, and VaultSecuritySummaryProvider. Public contracts and JSON version remain unchanged.

Fixed alias-storage defects:

- The reader ignored the file's version, allowing an unsupported future format to be
  accepted and overwritten as version 1. Missing/unsupported/mistyped versions now enter
  the existing corruption-preservation flow. Persisted service names are also validated.
- Repeated corrupt files at the same timestamp could overwrite an earlier recovery file.
  Recovery names are now unique, including a numeric suffix when needed, and neither
  rename nor copy requests replacement of existing recovery data.
- An unreadable initial file was treated as an empty successful snapshot, allowing a later
  mutation to replace records that had never been read. Initial read failures and failed
  corruption preservation now block writes until storage is repaired and reopened.

Added regressions for version rejection/preservation, repeated same-timestamp recovery,
and startup-read failure followed by mutation. Existing cancellation-at-commit, concurrent
mutation, persistence, settings write-failure, settings defaults and security-count tests
remain relevant.

Settings already publish only after durable write, within the IO block so prompt
cancellation cannot leave stale memory. No further changes were needed there. The summary
provider correctly counts involved entries in reused-password groups and derives weakness
from the shared evaluator; it does not persist or log passwords.

Verification pending: isolated `:app:testDebugUnitTest` with
`-I audit/gradle-isolation.init.gradle -Daudit.owner=persistence
--project-cache-dir /tmp/uss-audit-persistence/project-cache`.
Log: `/tmp/uss-audit-persistence-tests.log`. Root owns all device/end-to-end verification.
