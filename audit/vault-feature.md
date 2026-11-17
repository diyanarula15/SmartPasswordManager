# Vault feature audit

Reviewed the complete feature-vault production source: repository state holders,
CRUD routes, editor drafts, generator, strength estimator, list/detail UI and clipboard integration.
Public routes and shared core contracts remain unchanged.

## Fixed defects

1. Unicode uppercase dotted I (U+0130) expands under whole-string lowercase.
   Pattern matching then indexed beyond the original password's score array and
   crashed the editor/detail screen (and host security statistics). Pattern folds
   now preserve UTF-16 offsets. Regression inputs cover prefixes, suffixes,
   multiple dotted I characters, common words, sequences and emoji.
2. Repeated Save taps during suspended repository writes launched duplicate adds
   or competing updates. Both state holders now admit one save at a time, reset
   the guard in finally on success/error/cancellation, and expose saving state.
   The editor shows Saving… and disables editing, dismissing and saving during
   the write. Deterministic gated fake-repository regressions cover duplicate
   submission and retry after failure/cancellation for both state holders.
3. Invalid websites and overlong notes failed validation without displaying the
   corresponding error, so Save appeared inert. Both fields now expose their
   inline error state and supporting message. Two device regressions were added.

## Verification actually executed

```
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home bash ./gradlew --offline --no-configuration-cache -I audit/gradle-isolation.init.gradle -Daudit.owner=vault-feature --project-cache-dir /tmp/uss-audit-vault-feature/project-cache :feature-vault:testDebugUnitTest :feature-vault:compileDebugAndroidTestKotlin
```

BUILD SUCCESSFUL; 73 JVM tests, zero failures/errors/skips. All feature-vault
instrumentation test Kotlin compiled. Log: `/tmp/uss-audit-vault-feature.log`.
Device tests are delegated to the root's integrated device run; compilation alone
does not establish that the two added UI regressions passed on device.

## Limits

Strength remains a documented heuristic rather than breach detection. Strings
are immutable, so session draft clearing releases references without promising
physical memory erasure. Device layout, touch, clipboard, recreation and host
session enforcement require the integrated root run.
