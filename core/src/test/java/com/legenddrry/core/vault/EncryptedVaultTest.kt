package com.legenddrry.core.vault

import com.legenddrry.core.VaultEntry
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.auth.AuthState
import com.legenddrry.core.crypto.KeyWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.SecureRandom
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Software stand-in for the Android Keystore wrapper (AES-256-GCM, key held in memory). */
class FakeKeyWrapper : KeyWrapper {
    private val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return iv + c.doFinal(plaintext)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }
}

class EncryptedVaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private val password = "correct horse battery".toCharArray()
    private val wrapper = FakeKeyWrapper()
    private var nowMs = 1_000_000L
    private lateinit var dir: File

    private fun newVault(
        keyWrapper: KeyWrapper = wrapper,
        iterations: Int = 1_000,
    ): EncryptedVault {
        if (!::dir.isInitialized) dir = File(tmp.root, "vault")
        return EncryptedVault(dir, keyWrapper, iterations, clockMs = { nowMs })
    }

    private suspend fun EncryptedVault.settled(): AuthState =
        withContext(Dispatchers.Default) { withTimeout(10_000) { state.first { it != AuthState.LOADING } } }

    private suspend fun setUpVault(): EncryptedVault = newVault().also {
        it.settled()
        assertEquals(AuthResult.SUCCESS, it.setUp(password.copyOf()))
    }

    private fun entry(id: String, title: String = "Title $id", pw: String = "pw-$id") = VaultEntry(
        id = id, title = title, website = "https://$id.example", username = "user-$id",
        password = pw, notes = "notes $id", category = "Login", updatedAtEpochMs = 42L,
    )

    private suspend fun EncryptedVault.current() = entries.first()

    private suspend inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return t
            throw AssertionError("Expected ${T::class.simpleName} but got $t", t)
        }
        fail("Expected ${T::class.simpleName}")
        throw IllegalStateException()
    }

    // ---------------------------------------------------------------- setup

    @Test fun emptyDirectory_leavesLoadingForNeedsSetup() = runTest {
        val v = newVault()
        assertEquals(AuthState.NEEDS_SETUP, v.settled())
        assertEquals(emptyList<VaultEntry>(), v.current())
    }

    @Test fun firstTimeSetup_unlocksAndCreatesEncryptedFiles() = runTest {
        val v = setUpVault()
        assertEquals(AuthState.UNLOCKED, v.state.value)
        assertEquals(emptyList<VaultEntry>(), v.current())
        assertTrue(File(dir, "meta.bin").isFile)
        assertTrue(File(dir, "vault.bin").isFile)
        assertFalse(File(dir, "vault.bin.tmp").exists())
    }

    @Test fun setUp_rejectsShortAndBlankPasswords() = runTest {
        val v = newVault()
        v.settled()
        assertEquals(AuthResult.PASSWORD_TOO_SHORT, v.setUp("short7!".toCharArray()))
        assertEquals(AuthResult.PASSWORD_TOO_SHORT, v.setUp(CharArray(0)))
        assertEquals(AuthResult.PASSWORD_TOO_SHORT, v.setUp("        ".toCharArray()))
        assertEquals(AuthResult.PASSWORD_TOO_SHORT, v.setUp(" \t \n    ".toCharArray()))
        assertEquals(AuthState.NEEDS_SETUP, v.state.value)
        assertFalse(File(dir, "meta.bin").exists())
        assertEquals(AuthResult.SUCCESS, v.setUp("exactly8".toCharArray()))
    }

    @Test fun setUp_twice_returnsAlreadySetUp_andDoesNotReset() = runTest {
        val v = setUpVault()
        v.add(entry("a"))
        assertEquals(AuthResult.ALREADY_SET_UP, v.setUp("another password".toCharArray()))
        val reopened = newVault()
        assertEquals(AuthState.LOCKED, reopened.settled())
        assertEquals(AuthResult.ALREADY_SET_UP, reopened.setUp("another password".toCharArray()))
        assertEquals(AuthResult.WRONG_PASSWORD, reopened.unlock("another password".toCharArray()))
        assertEquals(AuthResult.SUCCESS, reopened.unlock(password.copyOf()))
        assertEquals(listOf(entry("a")), reopened.current())
    }

    @Test fun setUp_doesNotWipeCallersArray() = runTest {
        val v = newVault()
        v.settled()
        val input = password.copyOf()
        assertEquals(AuthResult.SUCCESS, v.setUp(input))
        assertTrue(input.contentEquals(password))
    }

    // ---------------------------------------------------------------- unlock

    @Test fun unlock_beforeSetup_returnsNotSetUp() = runTest {
        val v = newVault()
        v.settled()
        assertEquals(AuthResult.NOT_SET_UP, v.unlock(password.copyOf()))
        assertEquals(AuthState.NEEDS_SETUP, v.state.value)
    }

    @Test fun correctPassword_unlocks_wrongPassword_rejected() = runTest {
        setUpVault().apply { add(entry("a")); lock() }
        val v = newVault()
        assertEquals(AuthState.LOCKED, v.settled())
        assertEquals(AuthResult.WRONG_PASSWORD, v.unlock("correct horse battery!".toCharArray()))
        assertEquals(AuthState.LOCKED, v.state.value)
        assertEquals(AuthResult.SUCCESS, v.unlock(password.copyOf()))
        assertEquals(AuthState.UNLOCKED, v.state.value)
        assertEquals(listOf(entry("a")), v.current())
    }

    @Test fun wrongPassword_neverYieldsEntries() = runTest {
        val v = setUpVault()
        v.add(entry("secret"))
        v.lock()
        repeat(3) {
            assertEquals(AuthResult.WRONG_PASSWORD, v.unlock("wrong-password-$it".toCharArray()))
            assertEquals(AuthState.LOCKED, v.state.value)
            assertEquals(emptyList<VaultEntry>(), v.current())
        }
        assertThrows<IllegalStateException> { v.add(entry("x")) }
    }

    @Test fun lockout_afterFiveFailures_thenExpires() = runTest {
        val v = setUpVault()
        v.lock()
        repeat(5) { assertEquals(AuthResult.WRONG_PASSWORD, v.unlock("nope-nope-$it".toCharArray())) }
        // Even the correct password is refused during the lockout.
        assertEquals(AuthResult.TOO_MANY_ATTEMPTS, v.unlock(password.copyOf()))
        assertEquals(30, v.lockoutRemainingSeconds())
        assertEquals(AuthState.LOCKED, v.state.value)
        nowMs += 29_001
        assertEquals(1, v.lockoutRemainingSeconds())
        assertEquals(AuthResult.TOO_MANY_ATTEMPTS, v.unlock(password.copyOf()))
        nowMs += 1_000
        assertEquals(0, v.lockoutRemainingSeconds())
        assertEquals(AuthResult.SUCCESS, v.unlock(password.copyOf()))
    }

    @Test fun lockoutCounter_resetsOnSuccess() = runTest {
        val v = setUpVault()
        v.lock()
        repeat(4) { assertEquals(AuthResult.WRONG_PASSWORD, v.unlock("nope-nope-$it".toCharArray())) }
        assertEquals(AuthResult.SUCCESS, v.unlock(password.copyOf()))
        v.lock()
        repeat(4) { assertEquals(AuthResult.WRONG_PASSWORD, v.unlock("nope-nope-$it".toCharArray())) }
        assertEquals(0, v.lockoutRemainingSeconds())
        assertEquals(AuthResult.SUCCESS, v.unlock(password.copyOf()))
    }

    @Test fun storedIterationCount_isUsedOnUnlock() = runTest {
        setUpVault().apply { add(entry("a")); lock() }
        val v = newVault(iterations = 2_000) // new default must not break existing vaults
        v.settled()
        assertEquals(AuthResult.SUCCESS, v.unlock(password.copyOf()))
        assertEquals(listOf(entry("a")), v.current())
    }

    // ---------------------------------------------------------------- locked behaviour

    @Test fun lockedVault_emitsEmpty_andMutationsThrow() = runTest {
        val v = setUpVault()
        v.add(entry("a"))
        v.lock()
        assertEquals(AuthState.LOCKED, v.state.value)
        assertEquals(emptyList<VaultEntry>(), v.current())
        for (op in listOf<suspend () -> Unit>(
            { v.add(entry("b")) }, { v.update(entry("a", title = "x")) }, { v.delete("a") },
        )) {
            val e = assertThrows<IllegalStateException> { op() }
            assertEquals("Vault is locked", e.message)
        }
        assertEquals(AuthResult.SUCCESS, v.unlock(password.copyOf()))
        assertEquals(listOf(entry("a")), v.current())
    }

    @Test fun needsSetupVault_mutationsThrow_andLockIsNoOp() = runTest {
        val v = newVault()
        v.settled()
        assertThrows<IllegalStateException> { v.add(entry("a")) }
        v.lock()
        assertEquals(AuthState.NEEDS_SETUP, v.state.value)
    }

    // ---------------------------------------------------------------- CRUD + emissions

    @Test fun addUpdateDelete_emitAfterEachMutation() = runTest {
        val v = setUpVault()
        val seen = Collections.synchronizedList(mutableListOf<List<VaultEntry>>())
        val job = backgroundScope.launch(Dispatchers.Unconfined) { v.entries.collect { seen += it } }

        val a = entry("a"); val b = entry("b")
        val a2 = a.copy(title = "Renamed", password = "new-pw", updatedAtEpochMs = 99L)
        v.add(a)
        v.add(b)
        v.update(a2)
        v.delete("b")
        v.delete("missing") // no-op
        v.lock()

        withContext(Dispatchers.Default) { withTimeout(5_000) { while (seen.size < 6) delay(10) } }
        assertEquals(
            listOf(emptyList(), listOf(a), listOf(a, b), listOf(a2, b), listOf(a2), emptyList()),
            seen.toList(),
        )
        job.cancel()
    }

    @Test fun duplicateAdd_and_unknownUpdate_throwIllegalArgument() = runTest {
        val v = setUpVault()
        v.add(entry("a"))
        assertThrows<IllegalArgumentException> { v.add(entry("a", title = "dup")) }
        assertThrows<IllegalArgumentException> { v.update(entry("zzz")) }
        v.delete("zzz")
        assertEquals(listOf(entry("a")), v.current())
    }

    @Test fun concurrentAdds_areSerialized() = runTest {
        val v = setUpVault()
        (1..40).map { i -> async(Dispatchers.Default) { v.add(entry("e$i")) } }.awaitAll()
        assertEquals(40, v.current().size)
        v.lock()
        val reopened = newVault()
        reopened.settled()
        assertEquals(AuthResult.SUCCESS, reopened.unlock(password.copyOf()))
        assertEquals((1..40).map { "e$it" }.toSet(), reopened.current().map { it.id }.toSet())
    }

    // ---------------------------------------------------------------- persistence

    private suspend fun reopenAndUnlock(): EncryptedVault = newVault().also {
        assertEquals(AuthState.LOCKED, it.settled())
        assertEquals(AuthResult.SUCCESS, it.unlock(password.copyOf()))
    }

    @Test fun persistence_acrossNewInstances() = runTest {
        setUpVault().add(entry("a"))
        assertEquals(listOf(entry("a")), reopenAndUnlock().current())

        val updated = entry("a", title = "Updated title", pw = "rotated")
        reopenAndUnlock().update(updated)
        assertEquals(listOf(updated), reopenAndUnlock().current())

        reopenAndUnlock().delete("a")
        assertEquals(emptyList<VaultEntry>(), reopenAndUnlock().current())
    }

    @Test fun diskContents_doNotContainPlaintext() = runTest {
        val v = setUpVault()
        v.add(entry("a", title = "MyBankTitleMarker", pw = "EntryPasswordMarker"))
        v.lock()
        val needles = listOf(String(password), "MyBankTitleMarker", "EntryPasswordMarker", "user-a", "https://a.example")
        val files = dir.listFiles()!!.filter { it.isFile }
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val raw = f.readBytes()
            val asLatin1 = String(raw, Charsets.ISO_8859_1)
            for (n in needles) {
                assertFalse("${f.name} leaks '$n'", asLatin1.contains(n))
                assertFalse("${f.name} leaks UTF-16 '$n'", String(raw, Charsets.UTF_16LE).contains(n))
            }
        }
    }

    @Test fun eachWrite_usesFreshIv() = runTest {
        val v = setUpVault()
        v.add(entry("a"))
        val first = File(dir, "vault.bin").readBytes()
        v.update(entry("a")) // same content
        val second = File(dir, "vault.bin").readBytes()
        assertNotEquals(first.copyOfRange(4, 16).toList(), second.copyOfRange(4, 16).toList())
    }

    // ---------------------------------------------------------------- corruption

    @Test fun corruptVaultFile_unlockReturnsError_andNothingIsReset() = runTest {
        setUpVault().apply { add(entry("a")); lock() }
        val vaultFile = File(dir, "vault.bin")
        val bytes = vaultFile.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        vaultFile.writeBytes(bytes)

        val v = newVault()
        assertEquals(AuthState.LOCKED, v.settled())
        assertEquals(AuthResult.ERROR, v.unlock(password.copyOf()))
        assertEquals(AuthState.LOCKED, v.state.value)
        assertEquals(emptyList<VaultEntry>(), v.current())
        assertEquals(AuthResult.ALREADY_SET_UP, v.setUp(password.copyOf()))
        assertTrue(vaultFile.readBytes().contentEquals(bytes))
    }

    @Test fun corruptMetaFile_unlockReturnsError_notCountedAsFailure() = runTest {
        setUpVault().lock()
        File(dir, "meta.bin").writeBytes(ByteArray(10))
        val v = newVault()
        assertEquals(AuthState.LOCKED, v.settled())
        repeat(6) { assertEquals(AuthResult.ERROR, v.unlock(password.copyOf())) }
        assertEquals(0, v.lockoutRemainingSeconds())
    }

    @Test fun lostDeviceKey_unlockReturnsError() = runTest {
        setUpVault().lock()
        val v = newVault(keyWrapper = FakeKeyWrapper())
        v.settled()
        assertEquals(AuthResult.ERROR, v.unlock(password.copyOf()))
        assertEquals(AuthState.LOCKED, v.state.value)
    }
}
