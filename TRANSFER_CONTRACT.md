# Locked offline transfer contract

Worktree ONLY: /tmp/legenddrry-vault-transfer (feature/offline-vault-transfer).
DO NOT edit /Users/apple/Desktop/USS; another build/test is active there.

Existing VaultEntry eight fields are the entire current persisted schema. Preserve all8 exactly.
No custom fields/favorites/folder entities or persistent generator settings exist; don't invent/drop data.

Root owns TransferContract.kt, VaultTransferManager.kt, core/build.gradle.kts, CoreContainer.kt,
app container wiring, MainShell.kt and SettingsRoute hook. Agents own assigned files only.

Crypto owner core/transfer/VaultFileCodec.kt (+its tests): public no-arg class:
- fun encrypt(vault: PortableVault, credential: CharArray): ByteArray
- fun inspect(file: ByteArray): BackupUnlockMethod
- fun decrypt(file: ByteArray, credential: CharArray): PortableVault
Argon2id BC1.80.2,32MiB,3iterations,1lane,version0x13; AES256GCM128bitTag,salt16,nonce12.
Choose/freeze binary format in codec and document in own comment. Authenticate entire header asAAD,
including method,cipher length. Fixed bounded KDF params only. Limit file16MiB,records10k,field131072chars,
credential1024chars. Strict required JSON fields/record IDs uniqueness,UTF8 validity,no plaintext file or log.
Header only needed crypto params; creationdate encrypted. All failures user-safe. No recoverykey option.

Storage owner core/vault/EncryptedVault.kt, core/transfer/ImportPlanner.kt (+storage tests):
Implement VaultTransferStore. snapshot under vault mutex/session lock while unlocked.
importFromTransfer single existing mutate/writeVault atomic encrypted commit. Preserve original disk/cache on failure.
MERGE: preserve existing; identical content duplicates skipped; same-ID different content imports under fresh UUID
without overwriting existing. ONLY_NEW skips sameIDs or identical content; REPLACE replaces all atomically.
Return VaultImportResult counts. Preserve incoming timestamps/IDs except conflictingIDs in MERGE.
Keep existing AuthManager/VaultRepository interfaces unchanged. No new local crypto or database.

UI owner app/src/main/java/com/example/group4app/backup/** (+UI tests if time):
public @Composable fun VaultTransferSection(manager: VaultTransferManager)
Root inserts into existing Settings via feature-tools slot; no navigation replacement.
Manager API (root builds):
- suspend exportVault(masterPassword:CharArray, method:BackupUnlockMethod, transferPassword:CharArray?=null):ByteArray
- fun inspectBackup(file:ByteArray):BackupUnlockMethod
- suspend unlockBackup(file:ByteArray, credential:CharArray):PortableVault
- suspend restoreVault(vault:PortableVault, mode:VaultImportMode, masterPassword:CharArray?=null):VaultImportResult
Root auth manager unlock performs real password verification even already unlocked; no fake auth.
UI uses SAF CreateDocument(application/octet-stream) and OpenDocument; bounded reads (16MiB),.vault suggestedname,
password visibility,transferdefaultconfirmstrongpassword,metadata preview,3modes,replaceexplicit warning+masterpw.
Wipe ByteArrays on cancellation/disposal/completion. Never rememberSaveable secret fields. No plaintext tempfiles,
clipboard secrets,no log. Explain native picker may show cloud providers;chooseon-device foroffline.
Failed document writes: try delete destination using DocumentsContract; warn if incomplete mayremain.
Use shared feature-tools components +host MaterialTheme. Section title 'Vault Backup & Transfer'.

Tests/build run root with isolated projectcache+ALLmodulebuilddirs /tmp/legenddrry-transfer-build.
Do NOT launch emulator or stop any existing Gradle/emulator process. Report tests actually executed.
