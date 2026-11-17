// OWNER: worker "logic". LOCKED API — keep signatures. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.generator

import java.security.SecureRandom

data class GeneratorOptions(
    val length: Int = DEFAULT_LENGTH,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
) {
    val hasAnyCategory: Boolean get() = uppercase || lowercase || digits || symbols

    companion object {
        const val MIN_LENGTH = 12
        const val MAX_LENGTH = 32
        const val DEFAULT_LENGTH = 16
    }
}

/**
 * Generates passwords with [SecureRandom]. Every enabled category appears at least once.
 * @throws IllegalArgumentException if no category is enabled or length is outside MIN..MAX.
 */
class PasswordGenerator(private val random: SecureRandom = SecureRandom()) {

    fun generate(options: GeneratorOptions): String {
        require(options.hasAnyCategory) { "At least one character category must be enabled" }
        require(options.length in GeneratorOptions.MIN_LENGTH..GeneratorOptions.MAX_LENGTH) { "Length out of range" }
        // STUB: no category guarantee yet.
        val pool = buildString {
            if (options.uppercase) append(UPPERCASE)
            if (options.lowercase) append(LOWERCASE)
            if (options.digits) append(DIGITS)
            if (options.symbols) append(SYMBOLS)
        }
        return String(CharArray(options.length) { pool[random.nextInt(pool.length)] })
    }

    companion object {
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?/~"
    }
}
