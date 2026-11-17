package com.example.group4app.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.legenddrry.core.transfer.VaultTransferLimits
import java.io.InputStream

/** Bounded ciphertext read; neither this helper nor SAF ever writes plaintext. */
internal fun readVaultFileBounded(input: InputStream): ByteArray {
    val buffer = ByteArray(VaultTransferLimits.MAX_FILE_BYTES + 1)
    try {
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, minOf(8192, buffer.size - count))
            if (read < 0) break
            if (read == 0) {
                val next = input.read()
                if (next < 0) break
                buffer[count++] = next.toByte()
            } else count += read
        }
        require(count in 1..VaultTransferLimits.MAX_FILE_BYTES) { "Choose a non-empty .vault file up to 16 MiB." }
        return buffer.copyOf(count)
    } finally {
        buffer.fill(0)
    }
}

internal fun removeIncompleteDocument(resolver: ContentResolver, uri: Uri): Boolean {
    return try {
        DocumentsContract.deleteDocument(resolver, uri) || resolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        try { resolver.delete(uri, null, null) > 0 } catch (_: Exception) { false }
    }
}
