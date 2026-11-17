package com.legenddrry.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.core.crypto.AndroidKeystoreKeyWrapper
import com.legenddrry.core.vault.EncryptedVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.GeneralSecurityException

@RunWith(AndroidJUnit4::class)
class KeystoreVaultInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "legenddrry_test_key_" + System.nanoTime()
    private val wrapper = AndroidKeystoreKeyWrapper(alias)
    private lateinit var dir: File

    @Before fun setUp() {
        dir = File(context.noBackupFilesDir, "legenddrry-test-" + System.nanoTime())
    }

    @After fun tearDown() {
        dir.deleteRecursively()
        wrapper.deleteKey()
    }

    @Test fun keystoreWrapper_roundTrip_andTamperDetection() {
        val plain = "metadata payload".toByteArray()
        val blob = wrapper.encrypt(plain)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("metadata payload"))
        assertArrayEquals(plain, wrapper.decrypt(blob))
        // Fresh IV per encryption.
        assertFalse(blob.contentEquals(wrapper.encrypt(plain)))

        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        try {
            wrapper.decrypt(blob)
            fail("tampered blob must not decrypt")
        } catch (_: GeneralSecurityException) {
        }
    }

    @Test fun realFiles_setUp_lock_unlock_persist() = runBlocking {
        val password = "instrumented-pass".toCharArray()
        val entry = VaultEntry("1", "Bank", "https://bank.example", "me", "s3cret!", "", "Finance", 1L)

        val first = EncryptedVault(dir, wrapper)
        assertEquals(AuthState.NEEDS_SETUP, first.awaitSettled())
        assertEquals(AuthResult.SUCCESS, first.setUp(password.copyOf()))
        first.add(entry)
        first.lock()
        assertEquals(emptyList<VaultEntry>(), first.entries.first())

        val second = EncryptedVault(dir, wrapper)
        assertEquals(AuthState.LOCKED, second.awaitSettled())
        assertEquals(AuthResult.WRONG_PASSWORD, second.unlock("not-the-pass".toCharArray()))
        assertEquals(AuthResult.SUCCESS, second.unlock(password.copyOf()))
        assertEquals(listOf(entry), second.entries.first())
        assertTrue(File(dir, "meta.bin").isFile)

        for (f in dir.listFiles()!!) {
            val raw = String(f.readBytes(), Charsets.ISO_8859_1)
            assertFalse(raw.contains("s3cret!"))
            assertFalse(raw.contains("instrumented-pass"))
        }
    }

    private suspend fun EncryptedVault.awaitSettled() =
        withTimeout(10_000) { state.first { it != AuthState.LOADING } }
}
