package com.legenddrry.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Device-bound authenticated encryption used as an outer layer around the vault metadata.
 * Production: [AndroidKeystoreKeyWrapper]. JVM tests use a software fake.
 * Implementations must throw (never return garbage) when a blob cannot be authenticated.
 */
interface KeyWrapper {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a non-exportable Android Keystore key (no user-authentication requirement).
 * Blob layout: [version:1][iv:12][ciphertext+tag].
 */
class AndroidKeystoreKeyWrapper(
    private val alias: String = DEFAULT_ALIAS,
) : KeyWrapper {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        if (iv == null || iv.size != IV_BYTES) throw GeneralSecurityException("Unexpected Keystore IV")
        val ct = cipher.doFinal(plaintext)
        return byteArrayOf(VERSION) + iv + ct
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size < 1 + IV_BYTES + TAG_BYTES || blob[0] != VERSION) {
            throw GeneralSecurityException("Malformed Keystore blob")
        }
        // Never create a key on the decrypt path: a missing key means the data is unrecoverable.
        val key = existingKey() ?: throw GeneralSecurityException("Keystore key missing")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, blob, 1, IV_BYTES))
        return cipher.doFinal(blob, 1 + IV_BYTES, blob.size - 1 - IV_BYTES)
    }

    /** Removes the Keystore key (tests / explicit vault destruction only). */
    fun deleteKey() {
        synchronized(LOCK) { keyStore().deleteEntry(alias) }
    }

    private fun existingKey(): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey = synchronized(LOCK) {
        existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val DEFAULT_ALIAS = "legenddrry_meta_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
        private const val VERSION: Byte = 1
        private val LOCK = Any()
    }
}
