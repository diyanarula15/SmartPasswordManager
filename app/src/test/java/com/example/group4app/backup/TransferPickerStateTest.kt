package com.example.group4app.backup

import org.junit.Assert.*
import org.junit.Test

class TransferPickerStateTest {
    @Test fun cancellationRetainsPickerOwnershipUntilItsCallback() {
        val picker = TransferPickerState()
        picker.started()
        picker.cancel()
        assertTrue(picker.awaitingResult)
        try {
            picker.started()
            fail("A second transfer must not reuse a pending picker callback")
        } catch (_: IllegalStateException) { }
        assertFalse(picker.complete())
        assertFalse(picker.awaitingResult)
        picker.started()
        assertTrue(picker.complete())
    }

    @Test fun cancellationWithoutPendingPickerDoesNotCancelNextTransfer() {
        val picker = TransferPickerState()
        picker.cancel()
        picker.started()
        assertTrue(picker.complete())
        assertFalse(picker.awaitingResult)
    }
}
