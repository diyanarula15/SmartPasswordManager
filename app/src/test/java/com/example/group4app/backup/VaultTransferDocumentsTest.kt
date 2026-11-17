package com.example.group4app.backup

import com.legenddrry.core.transfer.VaultTransferLimits
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class VaultTransferDocumentsTest {
    @Test fun rejectsEmptyFile() {
        expectInvalid { readVaultFileBounded(ByteArrayInputStream(byteArrayOf())) }
    }

    @Test fun acceptsExactLimitAndDoesNotModifyInput() {
        val source = ByteArray(VaultTransferLimits.MAX_FILE_BYTES) { 17 }
        val read = readVaultFileBounded(ByteArrayInputStream(source))
        assertEquals(VaultTransferLimits.MAX_FILE_BYTES, read.size)
        assertEquals(17, read.first().toInt())
        assertEquals(17, read.last().toInt())
        assertEquals(17, source.last().toInt())
        read.fill(0)
        source.fill(0)
    }

    @Test fun rejectsOversizeWithoutReadingPastLimitPlusOne() {
        val source = object : InputStream() {
            var consumed = 0
            override fun read(): Int { consumed++; return 1 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill(1, offset, offset + length)
                consumed += length
                return length
            }
        }
        expectInvalid { readVaultFileBounded(source) }
        assertEquals(VaultTransferLimits.MAX_FILE_BYTES + 1, source.consumed)
    }

    @Test fun handlesStreamReturningZeroWithoutAnInfiniteLoop() {
        val expected = byteArrayOf(3, 4, 5, 6)
        val source = object : InputStream() {
            var position = 0
            override fun read(): Int = if (position == expected.size) -1 else expected[position++].toInt()
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int = if (position == expected.size) -1 else 0
        }
        assertArrayEquals(expected, readVaultFileBounded(source))
    }

    @Test fun enforcesTransferStrengthAndLength() {
        assertNotNull(transferPasswordError("Short1!"))
        assertNotNull(transferPasswordError("abcdefghijkl"))
        assertNotNull(transferPasswordError("abcdefghij12"))
        assertNull(transferPasswordError("CorrectHorse12"))
        assertNull(transferPasswordError("abcdefghij1!"))
        assertNotNull(transferPasswordError("A1!" + "x".repeat(VaultTransferLimits.MAX_PASSWORD_CHARS)))
    }

    @Test fun confirmationRequiresEqualNonemptyPasswords() {
        assertFalse(transferPasswordsMatch("", ""))
        assertFalse(transferPasswordsMatch("CorrectHorse12", "CorrectHorse13"))
        assertTrue(transferPasswordsMatch("CorrectHorse12", "CorrectHorse12"))
    }

    private fun expectInvalid(action: () -> Unit) {
        try { action(); fail("Expected a bounded-input rejection") }
        catch (_: IllegalArgumentException) { }
    }
}
