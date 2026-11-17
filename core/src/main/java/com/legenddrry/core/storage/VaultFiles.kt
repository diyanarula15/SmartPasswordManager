package com.legenddrry.core.storage

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.crypto.KeyWrapper
import com.legenddrry.core.crypto.VaultCrypto
import com.legenddrry.core.crypto.wipe
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Key material needed to turn the master password into the data key.
 * Contains no password and no password hash: only the PBKDF2 salt/iterations and the
 * AES-GCM wrapped data key (whose tag is the password check).
 */
internal class VaultMeta(
    val iterations: Int,
    val salt: ByteArray,
    val dekIv: ByteArray,
    val wrappedDek: ByteArray,
) {
    /** AAD binding the wrapped DEK to its KDF parameters. */
    fun dekAad(): ByteArray = dekAad(iterations, salt)

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeInt(KDF_PBKDF2_HMAC_SHA256)
            out.writeInt(iterations)
            out.writeShort(salt.size); out.write(salt)
            out.writeShort(dekIv.size); out.write(dekIv)
            out.writeShort(wrappedDek.size); out.write(wrappedDek)
        }
        return bytes.toByteArray()
    }

    companion object {
        private val MAGIC = "LDM1".toByteArray(Charsets.US_ASCII)
        private const val FORMAT_VERSION = 1
        private const val KDF_PBKDF2_HMAC_SHA256 = 1
        private const val MAX_ITERATIONS = 100_000_000

        fun dekAad(iterations: Int, salt: ByteArray): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).use { out ->
                out.write("legenddrry-dek-v1".toByteArray(Charsets.US_ASCII))
                out.writeInt(iterations)
                out.write(salt)
            }
            return bytes.toByteArray()
        }

        fun decode(data: ByteArray): VaultMeta {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
                if (!magic.contentEquals(MAGIC)) throw IOException("Bad metadata magic")
                if (input.readInt() != FORMAT_VERSION) throw IOException("Unsupported metadata version")
                if (input.readInt() != KDF_PBKDF2_HMAC_SHA256) throw IOException("Unsupported KDF")
                val iterations = input.readInt()
                if (iterations !in 1..MAX_ITERATIONS) throw IOException("Bad iteration count")
                val salt = input.readBlock(VaultCrypto.SALT_BYTES)
                val iv = input.readBlock(VaultCrypto.IV_BYTES)
                val wrapped = input.readBlock(VaultCrypto.KEY_BYTES + VaultCrypto.TAG_BYTES)
                if (input.read() != -1) throw IOException("Trailing metadata bytes")
                return VaultMeta(iterations, salt, iv, wrapped)
            }
        }

        private fun DataInputStream.readBlock(expected: Int): ByteArray {
            val size = readUnsignedShort()
            if (size != expected) throw IOException("Bad metadata field length")
            return ByteArray(size).also { readFully(it) }
        }
    }
}

/**
 * On-disk layout (directory = noBackupFilesDir/legenddrry):
 *  - meta.bin : KeyWrapper.encrypt(VaultMeta.encode())         (Android Keystore AES-GCM)
 *  - vault.bin: "LDV1" | iv(12) | AES-GCM(DEK, entries JSON)    (AAD = "LDV1")
 * Every write goes to a temp file, is fsync'ed, then renamed over the target.
 */
internal class VaultFiles(
    val dir: File,
    private val keyWrapper: KeyWrapper,
) {
    val metaFile = File(dir, "meta.bin")
    val vaultFile = File(dir, "vault.bin")

    fun metaExists(): Boolean = metaFile.exists()

    fun readMeta(): VaultMeta {
        val plain = keyWrapper.decrypt(metaFile.readBytes())
        try {
            return VaultMeta.decode(plain)
        } finally {
            plain.wipe()
        }
    }

    fun writeMeta(meta: VaultMeta) {
        val plain = meta.encode()
        try {
            atomicWrite(metaFile, keyWrapper.encrypt(plain))
        } finally {
            plain.wipe()
        }
    }

    fun readVault(dek: ByteArray): List<VaultEntry> {
        val data = vaultFile.readBytes()
        val headerSize = VAULT_MAGIC.size + VaultCrypto.IV_BYTES
        if (data.size < headerSize + VaultCrypto.TAG_BYTES) throw IOException("Vault file truncated")
        if (!data.copyOfRange(0, VAULT_MAGIC.size).contentEquals(VAULT_MAGIC)) {
            throw IOException("Bad vault magic")
        }
        val iv = data.copyOfRange(VAULT_MAGIC.size, headerSize)
        val plain = VaultCrypto.decrypt(dek, iv, data, headerSize, data.size - headerSize, VAULT_MAGIC)
        try {
            return EntryCodec.decode(plain)
        } finally {
            plain.wipe()
        }
    }

    fun writeVault(dek: ByteArray, entries: List<VaultEntry>) {
        val plain = EntryCodec.encode(entries)
        try {
            val iv = VaultCrypto.randomBytes(VaultCrypto.IV_BYTES)
            val ct = VaultCrypto.encrypt(dek, iv, plain, VAULT_MAGIC)
            atomicWrite(vaultFile, VAULT_MAGIC + iv + ct)
        } finally {
            plain.wipe()
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create vault directory")
        val tmp = File(dir, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            if (!tmp.renameTo(target)) throw IOException("Atomic rename failed")
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    private companion object {
        val VAULT_MAGIC = "LDV1".toByteArray(Charsets.US_ASCII)
    }
}

internal object EntryCodec {
    private const val VERSION = 1

    fun encode(entries: List<VaultEntry>): ByteArray {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("website", e.website)
                    .put("username", e.username)
                    .put("password", e.password)
                    .put("notes", e.notes)
                    .put("category", e.category)
                    .put("updatedAtEpochMs", e.updatedAtEpochMs),
            )
        }
        return JSONObject().put("version", VERSION).put("entries", array)
            .toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): List<VaultEntry> {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.getInt("version") != VERSION) throw IOException("Unsupported vault version")
        val array = root.getJSONArray("entries")
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            VaultEntry(
                id = o.getString("id"),
                title = o.getString("title"),
                website = o.getString("website"),
                username = o.getString("username"),
                password = o.getString("password"),
                notes = o.getString("notes"),
                category = o.getString("category"),
                updatedAtEpochMs = o.getLong("updatedAtEpochMs"),
            )
        }
    }
}
