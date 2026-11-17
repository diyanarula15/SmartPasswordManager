package com.legenddrry.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Standard JCA primitives only: PBKDF2-HMAC-SHA256 and AES-256-GCM. */
internal object VaultCrypto {
    const val KEY_BYTES = 32
    const val SALT_BYTES = 16
    const val IV_BYTES = 12
    const val TAG_BITS = 128
    const val TAG_BYTES = TAG_BITS / 8

    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val AES_GCM = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /** Derives a 256-bit key-encryption key. Caller must [wipe] the result. */
    fun deriveKek(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    /** Throws [javax.crypto.AEADBadTagException] (a BadPaddingException) on wrong key / tampering. */
    fun decrypt(
        key: ByteArray,
        iv: ByteArray,
        input: ByteArray,
        offset: Int,
        length: Int,
        aad: ByteArray,
    ): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(input, offset, length)
    }
}

internal fun ByteArray?.wipe() {
    this?.fill(0)
}

internal fun CharArray?.wipe() {
    this?.fill('\u0000')
}
