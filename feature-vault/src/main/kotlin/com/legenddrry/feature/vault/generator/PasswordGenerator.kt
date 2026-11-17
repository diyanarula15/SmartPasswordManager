// OWNER: worker "logic". LOCKED API — keep signatures.
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
        require(options.length in GeneratorOptions.MIN_LENGTH..GeneratorOptions.MAX_LENGTH) {
            "Length must be in ${GeneratorOptions.MIN_LENGTH}..${GeneratorOptions.MAX_LENGTH}, was ${options.length}"
        }

        val sets = enabledSets(options)
        val pool = sets.joinToString(separator = "")
        val chars = CharArray(options.length)

        // One guaranteed character from each enabled category...
        sets.forEachIndexed { i, set -> chars[i] = set[random.nextInt(set.length)] }
        // ...the rest drawn uniformly from the union pool.
        for (i in sets.size until chars.size) chars[i] = pool[random.nextInt(pool.length)]

        // Fisher–Yates shuffle so guaranteed characters are not positionally predictable.
        for (i in chars.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        val result = String(chars)
        chars.fill('\u0000')
        return result
    }

    private fun enabledSets(options: GeneratorOptions): List<String> = buildList {
        if (options.uppercase) add(UPPERCASE)
        if (options.lowercase) add(LOWERCASE)
        if (options.digits) add(DIGITS)
        if (options.symbols) add(SYMBOLS)
    }

    companion object {
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?/~"
    }
}
