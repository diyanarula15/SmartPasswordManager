package com.example.group4app.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A native picker cannot be dismissed by cancelling our coroutine. Keep its result owned
 * by the original operation until Android delivers it, including after local cancellation. */
internal class TransferPickerState {
    var awaitingResult by mutableStateOf(false)
        private set
    private var cancelled = false

    fun started() {
        check(!awaitingResult)
        cancelled = false
        awaitingResult = true
    }

    fun cancel() {
        if (awaitingResult) cancelled = true
    }

    /** False means the returned URI must not be used for an import/export. */
    fun complete(): Boolean {
        val accepted = !cancelled
        awaitingResult = false
        cancelled = false
        return accepted
    }
}
