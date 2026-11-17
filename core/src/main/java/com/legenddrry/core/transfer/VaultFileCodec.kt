package com.legenddrry.core.transfer

import com.legenddrry.core.VaultEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Portable format v1, big endian: [0..7] ASCII LDDRRYBK, [8] version=1,
 * [9] method (0=master,1=transfer), [10] KDF=1 (Argon2id v0x13), [11] cipher=1
 * (AES-256-GCM/tag128), [12..15] memoryKiB=32768, [16..19] iterations=3,
 * [20..23] lanes=1, [24..39] random salt, [40..51] random nonce,
 * [52..55] ciphertext byte count including tag, then ciphertext.
 * Every header byte is authenticated as AAD. KDF parameters are fixed and checked
 * before allocation; files cannot select expensive or weakened parameters.
 * The encrypted UTF-8 JSON object has schemaVersion=1, createdAtEpochMs, entries.
 * Every entry has exactly the eight current VaultEntry fields. No plaintext file
 * is produced. Caller owns credential/input/output buffers and must clear them.
 */
class VaultFileCodec {
    fun encrypt(vault: PortableVault, credential: CharArray): ByteArray {
        var plaintext: ByteArray? = null
        var key: ByteArray? = null
        try {
            validateCredential(credential)
            plaintext = encode(vault)
            require(plaintext.size <= VaultTransferLimits.MAX_FILE_BYTES - HEADER_SIZE - TAG_SIZE)
            val salt = ByteArray(16).also(random::nextBytes)
            val nonce = ByteArray(12).also(random::nextBytes)
            val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.BIG_ENDIAN)
                .put(MAGIC).put(1.toByte()).put(vault.unlockMethod.code()).put(1.toByte()).put(1.toByte())
                .putInt(MEMORY_KIB).putInt(ITERATIONS).putInt(1).put(salt).put(nonce)
                .putInt(plaintext.size + TAG_SIZE).array()
            key = derive(credential, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plaintext)
        } catch (_: Exception) {
            throw IllegalArgumentException("Could not create the encrypted backup. Check the transfer password and vault limits.")
        } finally {
            plaintext?.fill(0)
            key?.fill(0)
        }
    }

    /** Validates bounded structure only; metadata is authenticated only after decrypt succeeds. */
    fun inspect(file: ByteArray): BackupUnlockMethod = try {
        parseHeader(file).method
    } catch (_: Exception) {
        throw IllegalArgumentException("This is not a supported encrypted vault backup.")
    }

    fun decrypt(file: ByteArray, credential: CharArray): PortableVault {
        var key: ByteArray? = null
        var plaintext: ByteArray? = null
        try {
            validateCredential(credential)
            val header = parseHeader(file)
            key = derive(credential, header.salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.nonce))
            cipher.updateAAD(file, 0, HEADER_SIZE)
            plaintext = cipher.doFinal(file, HEADER_SIZE, file.size - HEADER_SIZE)
            val decoded = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(plaintext)).toString()
            return Parser(decoded).parse(header.method)
        } catch (_: Exception) {
            throw IllegalArgumentException("Could not open this backup. Check your password and that the file is complete and unmodified.")
        } finally {
            key?.fill(0)
            plaintext?.fill(0)
        }
    }

    private data class Header(val method: BackupUnlockMethod, val salt: ByteArray, val nonce: ByteArray)

    private fun parseHeader(file: ByteArray): Header {
        require(file.size in (HEADER_SIZE + TAG_SIZE)..VaultTransferLimits.MAX_FILE_BYTES)
        val b = ByteBuffer.wrap(file).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(8).also(b::get)
        require(magic.contentEquals(MAGIC))
        require(b.get().toInt() == 1)
        val method = when (b.get().toInt()) {
            0 -> BackupUnlockMethod.MASTER_PASSWORD
            1 -> BackupUnlockMethod.TRANSFER_PASSWORD
            else -> error("Unsupported method")
        }
        require(b.get().toInt() == 1 && b.get().toInt() == 1)
        require(b.int == MEMORY_KIB && b.int == ITERATIONS && b.int == 1)
        val salt = ByteArray(16).also(b::get)
        val nonce = ByteArray(12).also(b::get)
        require(b.int == file.size - HEADER_SIZE)
        return Header(method, salt, nonce)
    }

    private fun derive(credential: CharArray, salt: ByteArray): ByteArray {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(credential))
        val password = ByteArray(encoded.remaining()).also(encoded::get)
        if (encoded.hasArray()) encoded.array().fill(0)
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(MEMORY_KIB)
            .withIterations(ITERATIONS).withParallelism(1).withSalt(salt).build()
        val key = ByteArray(32)
        try {
            val generator = Argon2BytesGenerator()
            generator.init(parameters)
            generator.generateBytes(password, key)
            return key
        } catch (error: Exception) {
            key.fill(0)
            throw error
        } finally {
            password.fill(0)
            parameters.clear()
        }
    }

    private fun validateCredential(credential: CharArray) {
        require(credential.size in 1..VaultTransferLimits.MAX_PASSWORD_CHARS)
    }

    private fun encode(vault: PortableVault): ByteArray {
        ImportPlanner.validate(vault.entries)
        require(vault.entries.size <= VaultTransferLimits.MAX_RECORDS)
        require(vault.entries.map { it.id }.toSet().size == vault.entries.size)
        val out = StringBuilder()
        fun append(part: String) {
            require(out.length.toLong() + part.length <= VaultTransferLimits.MAX_FILE_BYTES - HEADER_SIZE - TAG_SIZE)
            out.append(part)
        }
        fun string(value: String) {
            require(value.length <= VaultTransferLimits.MAX_FIELD_CHARS)
            validUnicode(value)
            append("\"")
            value.forEach { c ->
                append(when (c) {
                    '"' -> "\\\""
                    '\\' -> "\\\\"
                    '\b' -> "\\b"
                    '\u000c' -> "\\f"
                    '\n' -> "\\n"
                    '\r' -> "\\r"
                    '\t' -> "\\t"
                    else -> if (c < ' ') "\\u" + c.code.toString(16).padStart(4, '0') else c.toString()
                })
            }
            append("\"")
        }
        append("{\"schemaVersion\":1,\"createdAtEpochMs\":${vault.createdAtEpochMs},\"entries\":[")
        vault.entries.forEachIndexed { index, entry ->
            require(entry.id.isNotBlank())
            if (index > 0) append(",")
            append("{")
            listOf("id" to entry.id, "title" to entry.title, "website" to entry.website,
                "username" to entry.username, "password" to entry.password, "notes" to entry.notes,
                "category" to entry.category).forEachIndexed { fieldIndex, (name, value) ->
                if (fieldIndex > 0) append(",")
                append("\"$name\":")
                string(value)
            }
            append(",\"updatedAtEpochMs\":${entry.updatedAtEpochMs}}")
        }
        append("]}")
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    /** Purpose-built strict JSON reader: no coercions, unknown/duplicate fields or trailing data. */
    private class Parser(private val source: String) {
        private var position = 0
        fun parse(method: BackupUnlockMethod): PortableVault {
            var created: Long? = null
            var entries: List<VaultEntry>? = null
            var version: Long? = null
            objectFields { key ->
                when (key) {
                    "schemaVersion" -> version = number()
                    "createdAtEpochMs" -> created = number()
                    "entries" -> entries = entries()
                    else -> error("Unknown field")
                }
            }
            whitespace()
            require(position == source.length && version == 1L)
            return PortableVault(requireNotNull(entries), requireNotNull(created), method)
        }

        private fun entries(): List<VaultEntry> {
            expect('[')
            val entries = ArrayList<VaultEntry>()
            val ids = HashSet<String>()
            if (take(']')) return entries
            do {
                require(entries.size < VaultTransferLimits.MAX_RECORDS)
                val fields = HashMap<String, String>()
                var updated: Long? = null
                objectFields { name ->
                    if (name == "updatedAtEpochMs") updated = number()
                    else {
                        require(name in STRING_FIELDS)
                        fields[name] = string(VaultTransferLimits.MAX_FIELD_CHARS)
                    }
                }
                require(fields.keys == STRING_FIELDS)
                val id = fields.getValue("id")
                require(id.isNotBlank() && ids.add(id))
                entries += VaultEntry(id, fields.getValue("title"), fields.getValue("website"),
                    fields.getValue("username"), fields.getValue("password"), fields.getValue("notes"),
                    fields.getValue("category"), requireNotNull(updated))
            } while (take(','))
            expect(']')
            return entries
        }

        private fun objectFields(field: (String) -> Unit) {
            expect('{')
            val names = HashSet<String>()
            if (take('}')) return
            do {
                val name = string(32)
                require(names.add(name))
                expect(':')
                field(name)
            } while (take(','))
            expect('}')
        }

        private fun string(limit: Int): String {
            expect('"')
            val out = StringBuilder()
            while (position < source.length) {
                val c = source[position++]
                if (c == '"') return out.toString().also(::validUnicode)
                require(c >= ' ')
                if (c != '\\') out.append(c)
                else {
                    require(position < source.length)
                    out.append(when (val escaped = source[position++]) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            require(position + 4 <= source.length)
                            val code = source.substring(position, position + 4)
                            require(code.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                            position += 4
                            code.toInt(16).toChar()
                        }
                        else -> error("Invalid escape")
                    })
                }
                require(out.length <= limit)
            }
            error("Incomplete string")
        }

        private fun number(): Long {
            whitespace()
            val start = position
            if (position < source.length && source[position] == '-') position++
            require(position < source.length && source[position] in '0'..'9')
            if (source[position] == '0') position++
            else while (position < source.length && source[position] in '0'..'9') position++
            return source.substring(start, position).toLong()
        }

        private fun whitespace() { while (position < source.length && source[position] in " \t\r\n") position++ }
        private fun take(c: Char): Boolean {
            whitespace()
            return if (position < source.length && source[position] == c) { position++; true } else false
        }
        private fun expect(c: Char) { require(take(c)) }
    }

    companion object {
        private const val HEADER_SIZE = 56
        private const val TAG_SIZE = 16
        private const val MEMORY_KIB = 32768
        private const val ITERATIONS = 3
        private val MAGIC = "LDDRRYBK".toByteArray(Charsets.US_ASCII)
        private val random = SecureRandom()
        private val STRING_FIELDS = setOf("id", "title", "website", "username", "password", "notes", "category")
        private fun BackupUnlockMethod.code(): Byte = when (this) {
            BackupUnlockMethod.MASTER_PASSWORD -> 0.toByte()
            BackupUnlockMethod.TRANSFER_PASSWORD -> 1.toByte()
        }
        private fun validUnicode(value: String) {
            var index = 0
            while (index < value.length) {
                val c = value[index++]
                if (c.isHighSurrogate()) require(index < value.length && value[index++].isLowSurrogate())
                else require(!c.isLowSurrogate())
            }
        }
    }
}
