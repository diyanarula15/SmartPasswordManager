# Offline encrypted vault backup and transfer

## User flow

Settings → Vault Backup & Transfer → Export vault. Enter the local master password
for real reauthentication. Choose the recommended separate transfer password
(12+ characters, three character types, six distinct characters, matching confirmation),
or use the verified current master password. Save the `.vault` file in on-device
storage. Transfer that file by USB, SD card, Bluetooth or another offline method.

On the destination, first set up/unlock its own local vault. Choose Import vault,
select the `.vault` file, enter its backup password, review counts/categories/date,
select Merge, Only new, or Replace, then enter the DESTINATION master password.
Replace additionally requires an explicit acknowledgement. The source and destination
master passwords and device encryption keys may be completely different.

Android SAF requests EXTRA_LOCAL_ONLY for both CREATE_DOCUMENT and OPEN_DOCUMENT.
No Internet permission, account, server, cloud dependency or telemetry is added.
Providers are Android components; choose a genuinely on-device provider.

## Architecture and data coverage

- `VaultFileCodec`: bounded portable format, AES-GCM, Argon2id, strict UTF-8/JSON.
- `VaultTransferManager`: existing master-password verification, portable operations,
  credential cleanup and destination import authorization.
- `VaultTransferStore` implemented by the existing `EncryptedVault`: serialized
  snapshots and atomic encrypted import using its existing DEK/VaultFiles writer.
- `ImportPlanner`: explicit duplicate/conflict policy; no silent overwrite.
- `VaultTransferSection`/`VaultTransferDocuments`: real SAF UI, secure password inputs,
  bounded ciphertext IO, progress/cancellation and partial-document cleanup.
- Existing Settings receives a host composition slot; all existing routes remain unchanged.

The existing vault is an encrypted file store, not a SQL/Room database. Its eight
persisted fields are preserved: id,title,website,username,password,notes,category,
updatedAtEpochMs. Attached notes/categories and relationships represented by these
fields are retained. The app has no standalone note/folder/custom-field/favorite
entities or persistent generator options; these are not invented or falsely claimed.
No local data key, Keystore material, auth token or diagnostic data is exported.

## Cryptography and format

Real Bouncy Castle1.80.2 Argon2id v0x13,32MiB memory,3iterations,1lane derives a32-byte
portable key. AES-256-GCM uses a fresh SecureRandom16-byte salt,12-byte nonce and
128-bit tag. The Argon2 profile is fixed and validated before KDF allocation, preventing
attacker-selected memory/time costs. No weaker fallback is used. Recovery keys are
not offered; a lost backup password means the file cannot be recovered by the app.

Version1 binary format (big endian),56-byte authenticated header:

| Offset | Value |
| --- | --- |
|0..7|ASCII `LDDRRYBK`|
|8|format version1|
|9|unlock method:0 master /1 transfer|
|10|KDF1:Argon2id v0x13|
|11|cipher1:AES-256-GCM|
|12..15|memoryKiB32768|
|16..19|iterations3|
|20..23|lanes1|
|24..39|random salt|
|40..51|random nonce|
|52..55|ciphertext length including tag|
|56..end|authenticated ciphertext|

The ENTIRE header is AES-GCM associated data. Creation time and all records are
inside the encrypted payload. Payload schemaVersion1 contains creation time and the
exact eight-field records. Unknown/duplicate/missing fields, invalid Unicode/types,
unsupported versions/algorithms/parameters and malformed lengths are rejected.
Limits:16MiB file,10,000 records,131,072 UTF-16 code units per field,1,024 per unlock
credential, plus aggregate encoded-size limits. No compression/zip processing exists.

## Atomicity and duplicate semantics

MERGE skips identical six-field user content (excluding id/timestamp). A conflicting
ID with different content is imported under a fresh UUID while preserving both
records and the incoming timestamp. ONLY_NEW skips existing IDs or identical content.
REPLACE preserves imported IDs and replaces the complete list only after verification.

Every mode commits through one fsynced, encrypted temporary file and atomic rename,
then publishes cache/state. Disk failures leave original ciphertext/cache intact.
Lock/cancellation before the commit aborts. Once the synchronous atomic write starts,
it may complete; the UI therefore does not promise rollback of an already committed
operation. Local keys remain destination-owned. Only ciphertext uses temporary files.
SAF write failures/cancellations attempt to delete the incomplete destination and
warn if deletion cannot be confirmed. Process death can leave an incomplete encrypted
or empty document; delete that file and export again.

Secret inputs use Material secure fields (copy/cut disabled even when visible),
unsaved state, and clearing on dismissal/disposal. Key/plaintext byte buffers and
credential character arrays are cleared where practical. Immutable JVM/Compose
strings cannot be guaranteed physically erased; no plaintext temp files or logs
are introduced.

## Verification and limits of evidence

Focused tests and app assembly are run from the isolated feature worktree. All module
outputs/project caches go to `/tmp/legenddrry-transfer-build`; the pre-existing build
and its emulator processes are not stopped, reused or modified.

Tests include real AES-GCM/Argon2id correctness/randomness/tampering, malformed file
and strict payload rejection, bounded IO, transfer policy, duplicate planning, local
at-rest persistence, actual filesystem rollback, and deterministic pre-commit
lock/cancellation races. A20-entry end-to-end test copies an actual encrypted `.vault`
file between two independent vault directories with independent local wrapping keys
and different master passwords, restores every field, locks/reopens the destination,
and repeats both backup password methods. Unicode, notes, categories and long
passwords are included.

This automated cross-vault test is JVM-based and uses software AES-GCM stand-ins for
Android Keystore ONLY in tests. Physical two-device/airplane-mode SAF interaction was
not executed in order to avoid disturbing the user's concurrent device/build test.
The production UI uses the real Android SAF and existing Android Keystore-backed core.
Do not present the JVM test as a completed physical-device acceptance test.

Build commands:
```
bash ./gradlew :core:testDebugUnitTest :app:testDebugUnitTest \
  --tests com.example.group4app.backup.VaultTransferDocumentsTest :app:assembleDebug \
  --max-workers=2 --no-configuration-cache \
  --project-cache-dir /tmp/legenddrry-transfer-build/project-cache \
  -I /tmp/legenddrry-transfer.init.gradle
```

The init script redirects EVERY project's buildDirectory to its own directory under
`/tmp/legenddrry-transfer-build`. See delivered XML reports/log for actual results.

## Executed result

55 core tests and6 focused app tests passed (61 total),zero failures/errors/skips.
The integrated application debug APK built successfully. The original parallel
build and its existing emulator/device state were not touched.
