package com.example.group4app

import com.example.group4app.ui.auth.callAuth
import com.example.group4app.ui.auth.validateNewMasterPassword
import com.legenddrry.core.auth.AuthResult
import com.legenddrry.core.transfer.VaultTransferLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuthCredentialTest {
    @Test fun clearsCredentialWhenAuthenticationIsCancelled() = runTest {
        val credential = "Secret master password".toCharArray()
        val started = CompletableDeferred<Unit>()
        val auth = async {
            callAuth(credential) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        auth.cancel()
        try { auth.await(); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertTrue(credential.all { it == '\u0000' })
    }

    @Test fun clearsCredentialOnSuccessAndFailure() = runTest {
        for (fails in listOf(false, true)) {
            val credential = "Secret master password".toCharArray()
            val result = callAuth(credential) {
                if (fails) throw IllegalStateException("Synthetic repository failure")
                AuthResult.SUCCESS
            }
            assertEquals(if (fails) AuthResult.ERROR else AuthResult.SUCCESS, result)
            assertTrue(credential.all { it == '\u0000' })
        }
    }

    @Test fun newMasterPasswordCannotExceedPortableBackupCredentialLimit() {
        val boundary = "A".repeat(VaultTransferLimits.MAX_PASSWORD_CHARS)
        assertNull(validateNewMasterPassword(boundary, boundary))
        assertNotNull(validateNewMasterPassword(boundary + "x", boundary + "x"))
    }
}
