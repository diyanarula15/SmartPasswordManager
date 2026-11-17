# Core storage/security audit

Reviewed encrypted storage, metadata and entry encoding, key wrapping, authentication,
lockout, CRUD serialization, transfer storage hooks, cancellation and lock races.
Existing public APIs, schema, cipher format and KDF parameters are preserved.

Fixed:

- An in-flight setup/unlock could reopen the session after `lock()` returned. Authentication
  now captures a session generation and opens only when that generation still matches;
  rejected keys are wiped and a completed setup remains safely locked.
- Unlock's derived data key could escape wiping when prompt cancellation discarded the
  `withContext` return value. Capture now occurs inside that dispatcher block with outer
  `finally` cleanup until session ownership is transferred.
- Queued CRUD now binds to the generation in which it was requested, matching transfer
  session guards and refusing stale requests.
- Added an internal lock epoch probe for transfer integration; it changes on lock, remains
  stable during password reauthentication, and exposes no secret data.

Added four deterministic authentication regressions covering lock during setup, lock
while unlocking, cancellation after key derivation before the caller resumes, and the
lock-only epoch remaining stable across reauthentication.

Executed:

`JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home bash ./gradlew :core:testDebugUnitTest -I audit/gradle-isolation.init.gradle -Daudit.owner=core-storage --project-cache-dir /tmp/uss-audit-core-storage/project-cache --console=plain`

Latest complete run: BUILD SUCCESSFUL, 62 tests, zero failures/errors/skips (includes
concurrently added transfer regressions). Log: `/tmp/uss-audit-core-storage-tests.log`.
The final internal lock-epoch accessor and its fourth regression are subject to root's integrated build.
Device/Keystore tests are reserved for the root agent; this owner did not run device tests.
