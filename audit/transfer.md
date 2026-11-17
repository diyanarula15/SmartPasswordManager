# Transfer audit

Reviewed portable encryption format, strict JSON parser, bounded header/KDF parameters,
all import planning modes, orchestration, native document reads/writes, preview UI and cancellation.
Existing disk schema, AES-GCM/Argon2id parameters and shared interfaces are preserved.

## Fixes

- Transfers previously checked local unlock state only before lengthy work. Export/preview
  could finish after a lock, and state-only checks missed lock followed by unlock. The manager
  now checks a lock-only epoch after verification/snapshot/crypto. The optional epoch provider
  preserves constructor compatibility; real EncryptedVault instances are detected by default.
  Core storage owns the epoch accessor and root owns container wiring.
- Cancelling while a native document picker was outstanding previously released `busy` and
  allowed another operation to reuse the previous activity-result callback. Picker ownership
  now remains pending until its callback; a cancelled import result is discarded and an export
  destination is cleaned up instead of receiving another operation's buffers.

## Regression coverage

- Fake AuthManager/store tests reject lock during snapshot, lock during successful verification,
  and lock-then-reunlock during either phase; they verify credential wiping and no import write.
- Picker state tests verify that cancellation cannot release callback ownership, cancelled
  results are rejected, and a subsequent operation accepts its own callback.
- Existing codec tests independently seal malicious payloads and cover malformed headers,
  strict JSON, Unicode, altered ciphertext, duplicate IDs, limits, randomization and both methods.

## Execution

Initial isolated `:core:testDebugUnitTest :app:testDebugUnitTest` passed (24 seconds), including
the new state-only session checks and picker tests. A final run with epoch regressions uses:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home bash ./gradlew \
  -I audit/gradle-isolation.init.gradle -Daudit.owner=transfer \
  --project-cache-dir /tmp/uss-audit-transfer/project-cache \
  :core:testDebugUnitTest :app:testDebugUnitTest
```

Final execution result will be recorded below. No device test was run by this owner;
root coordinates whole-app device verification. Native provider cleanup and in-flight picker
cancellation need device/provider coverage; pure picker ownership transitions are tested here.
Plaintext model fields use immutable JVM Strings and cannot be reliably zeroed in place;
the implementation discards previews and wipes owned byte/character buffers.
