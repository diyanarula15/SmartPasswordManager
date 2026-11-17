package com.legenddrry.feature.vault.ui.common

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ClipboardFeedbackTest {
    @Test fun successfulCopyReportsSuccessAndRunsOnce() {
        var copies = 0
        assertEquals("Password copied", clipboardCopyMessage("Password copied") { copies++ })
        assertEquals(1, copies)
    }

    @Test fun policyDenialAndOemFailuresReportFailureWithoutExposingDetails() {
        for (failure in listOf(SecurityException("sensitive policy detail"), IllegalStateException("sensitive OEM detail"))) {
            val message = clipboardCopyMessage("Password copied") { throw failure }
            assertEquals("Couldn't copy to clipboard. Please try again.", message)
            assertFalse(message.contains("sensitive"))
        }
    }

    @Test fun cancellationIsPropagated() {
        val cancelled = CancellationException("cancelled")
        try {
            clipboardCopyMessage("Password copied") { throw cancelled }
            throw AssertionError("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancelled, actual)
        }
    }
}
