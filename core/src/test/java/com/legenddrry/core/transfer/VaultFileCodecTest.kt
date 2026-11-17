package com.legenddrry.core.transfer

import com.legenddrry.core.VaultEntry
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.*
import org.junit.Test

class VaultFileCodecTest {
    @Test fun roundTripPreservesAllEightFieldsAndMetadataForBothMethods() {
        BackupUnlockMethod.entries.forEach { method ->
            val vault = vault.copy(unlockMethod = method)
            val file = codec.encrypt(vault, password)
            assertEquals(method, codec.inspect(file))
            assertEquals(vault, codec.decrypt(file, password))
            assertFalse(String(file, Charsets.ISO_8859_1).contains("secret-password"))
        }
    }

    @Test fun encryptIsRandomizedAndDoesNotMutateCallerSecrets() {
        val copy = password.copyOf()
        val file = codec.encrypt(vault, copy)
        assertArrayEquals(password, copy)
        assertFalse(file.contentEquals(fixture))
        val snapshot = file.copyOf()
        codec.decrypt(file, copy)
        assertArrayEquals(snapshot, file)
    }

    @Test fun emptyVaultRoundTripsAndMaxCredentialIsAccepted() {
        val secret = CharArray(VaultTransferLimits.MAX_PASSWORD_CHARS) { 'x' }
        val empty = vault.copy(entries = emptyList())
        assertEquals(empty, codec.decrypt(codec.encrypt(empty, secret), secret))
    }

    @Test fun wrongPasswordAndAuthenticatedHeaderChangesAreRejectedSafely() {
        rejected { codec.decrypt(fixture, "wrong-password".toCharArray()) }
        listOf(9, 24, 40, fixture.lastIndex).forEach { offset ->
            val changed = fixture.copyOf()
            changed[offset] = (changed[offset].toInt() xor 1).toByte()
            rejected { codec.decrypt(changed, password) }
        }
    }

    @Test fun malformedAndUnsupportedHeadersNeverReachKdf() {
        for (length in listOf(0, 8, 55, 56, 71, fixture.size - 1)) {
            rejected { codec.inspect(fixture.copyOf(length)) }
        }
        for (offset in listOf(0, 8, 10, 11, 12, 16, 20, 52, 55)) {
            val changed = fixture.copyOf()
            changed[offset] = (changed[offset].toInt() xor 0x7f).toByte()
            rejected { codec.inspect(changed) }
        }
        rejected { codec.inspect(fixture + byteArrayOf(0)) }
        rejected { codec.inspect(ByteArray(VaultTransferLimits.MAX_FILE_BYTES + 1)) }
    }

    @Test fun duplicateIdsOversizedFieldsAndExcessRecordsAreRejectedBeforeEncryption() {
        rejected { codec.encrypt(vault.copy(entries = listOf(entry, entry)), password) }
        rejected { codec.encrypt(vault.copy(entries = listOf(entry.copy(id = " "))), password) }
        rejected { codec.encrypt(vault.copy(entries = listOf(entry.copy(notes = "x".repeat(VaultTransferLimits.MAX_FIELD_CHARS + 1)))), password) }
        rejected { codec.encrypt(vault.copy(entries = List(VaultTransferLimits.MAX_RECORDS + 1) { entry.copy(id = "$it") }), password) }
        rejected { codec.encrypt(vault.copy(entries = listOf(entry.copy(notes = "\uD800"))), password) }
    }

    @Test fun credentialsAreBoundedAndMalformedUnicodeIsRejected() {
        listOf(charArrayOf(), CharArray(1025) { 'x' }, charArrayOf('\uD800')).forEach { credential ->
            rejected { codec.encrypt(vault, credential) }
            rejected { codec.decrypt(fixture, credential) }
        }
    }

    @Test fun maximumFieldLengthAndUnicodeEscapesRoundTrip() {
        val boundary = vault.copy(entries = listOf(entry.copy(notes = "x".repeat(VaultTransferLimits.MAX_FIELD_CHARS))))
        assertEquals(boundary, codec.decrypt(codec.encrypt(boundary, password), password))
    }

    @Test fun independentlySealedPayloadLoadsAndRejectsStrictJsonViolations() {
        assertEquals(vault.copy(entries = emptyList()), codec.decrypt(seal(validJson.toByteArray()), password))
        val invalid = listOf(
            "{}", validJson + "x", validJson.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            validJson.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            validJson.replace("\"createdAtEpochMs\":42", "\"createdAtEpochMs\":\"42\""),
            validJson.replace("\"createdAtEpochMs\":42", "\"createdAtEpochMs\":01"),
            validJson.replace("\"createdAtEpochMs\":42", "\"createdAtEpochMs\":1e2"),
            validJson.replace("\"createdAtEpochMs\":42", "\"createdAtEpochMs\":9223372036854775808"),
            validJson.replace("\"entries\":[]", "\"entries\":null"),
            validJson.replace("\"entries\":[]", "\"entries\":[],\"extra\":1"),
            validJson.replace("\"entries\":[]", "\"entries\":[{}]"),
            validJson.replace("\"entries\":[]", "\"entries\":[{\"id\":\"x\",\"id\":\"y\"}]"),
            validJson.replace("\"entries\":[]", "\"entries\":[{\"id\":\"\\uD800\"}]"),
            validJson.replace("\"entries\":[]", "\"entries\":[{\"id\":\"a\nraw\"}]"),
        )
        invalid.forEach { rejected { codec.decrypt(seal(it.toByteArray()), password) } }
        rejected { codec.decrypt(seal(byteArrayOf(0xc3.toByte(), 0x28)), password) }
    }

    @Test fun authenticatedRecordSchemaAndDuplicateIdsAreRejected() {
        val record = """{"id":"a","title":"t","website":"w","username":"u","password":"p","notes":"n","category":"c","updatedAtEpochMs":1}"""
        fun document(records: String) = validJson.replace("[]", "[$records]")
        val invalid = listOf(
            document("$record,$record"),
            document(record.replace("\"notes\":\"n\",", "")),
            document(record.replace("\"notes\":\"n\"", "\"notes\":null")),
            document(record.replace("\"notes\":\"n\"", "\"notes\":9")),
            document(record.replace("\"id\":\"a\"", "\"id\":\" \"")),
            document(record.replace("\"updatedAtEpochMs\":1", "\"unexpected\":1,\"updatedAtEpochMs\":1")),
            document(record.replace("\"updatedAtEpochMs\":1", "\"updatedAtEpochMs\":\"1\"")),
        )
        invalid.forEach { rejected { codec.decrypt(seal(it.toByteArray()), password) } }
    }

    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected bounded validation or authentication failure") }
        catch (error: IllegalArgumentException) {
            assertFalse(error.message.orEmpty().contains("secret-password"))
            assertNull(error.cause)
        }
    }

    private fun seal(plaintext: ByteArray): ByteArray {
        val header = ByteBuffer.allocate(56).put("LDDRRYBK".toByteArray())
            .put(1.toByte()).put(1.toByte()).put(1.toByte()).put(1.toByte())
            .putInt(32768).putInt(3).putInt(1).put(salt).put(nonce).putInt(plaintext.size + 16).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(independentKey, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    companion object {
        private val codec = VaultFileCodec()
        private val password = "transfer-secret-π-long".toCharArray()
        private val entry = VaultEntry("entry-1", "Student \"Portal\"", "https://example.test/路径", "user\nname",
            "secret-password-🔒", "line1\nline2\t\\\u0000", "Study", Long.MIN_VALUE)
        private val vault = PortableVault(listOf(entry), 42, BackupUnlockMethod.TRANSFER_PASSWORD)
        private val fixture by lazy { codec.encrypt(vault, password) }
        private val salt = ByteArray(16) { it.toByte() }
        private val nonce = ByteArray(12) { (it + 32).toByte() }
        private const val validJson = "{\"schemaVersion\":1,\"createdAtEpochMs\":42,\"entries\":[]}"
        private val independentKey by lazy {
            val p = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(0x13)
                .withMemoryAsKB(32768).withIterations(3).withParallelism(1).withSalt(salt).build()
            ByteArray(32).also { out -> Argon2BytesGenerator().apply { init(p) }.generateBytes(password, out) }
        }
    }
}
