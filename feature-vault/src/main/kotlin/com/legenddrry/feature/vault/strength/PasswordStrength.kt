package com.legenddrry.feature.vault.strength

import kotlin.math.log2

enum class StrengthLevel(val label: String) {
    VERY_WEAK("Very weak"),
    WEAK("Weak"),
    FAIR("Fair"),
    STRONG("Strong"),
    VERY_STRONG("Very strong"),
}

data class PasswordStrength(
    val level: StrengthLevel,
    val entropyBits: Double,
    val suggestions: List<String>,
) {
    /** 0.2 .. 1.0, for progress bars. */
    val fraction: Float get() = (level.ordinal + 1) / StrengthLevel.entries.size.toFloat()
}

/**
 * Heuristic strength estimate. Base entropy is `length * log2(pool)`, where the pool is the
 * sum of the character classes present. Characters inside repeated runs, simple sequences or
 * common passwords contribute far fewer bits; short or single-class passwords are scaled down.
 * Pure Kotlin, no Android APIs.
 */
object PasswordStrengthEvaluator {

    // Level thresholds in bits (lower bound of each level).
    const val WEAK_BITS = 28.0
    const val FAIR_BITS = 40.0
    const val STRONG_BITS = 60.0
    const val VERY_STRONG_BITS = 80.0

    const val SUGGEST_LENGTH = "Use at least 12 characters"
    const val SUGGEST_UPPER = "Add uppercase letters"
    const val SUGGEST_LOWER = "Add lowercase letters"
    const val SUGGEST_DIGITS = "Add numbers"
    const val SUGGEST_SYMBOLS = "Add symbols"
    const val SUGGEST_REPEATS = "Avoid repeated characters"
    const val SUGGEST_SEQUENCES = "Avoid sequences like abc or 123"
    const val SUGGEST_COMMON = "Avoid common passwords"

    private const val LOWER_POOL = 26
    private const val UPPER_POOL = 26
    private const val DIGIT_POOL = 10
    private const val SYMBOL_POOL = 33

    /** Bits granted to a character that is predictable from its neighbours. */
    private const val PATTERN_CHAR_BITS = 1.0

    /** Bits granted to a whole common-password match (dictionary rank ~ 2^10). */
    private const val COMMON_WORD_BITS = 10.0

    private const val SHORT_LENGTH = 8
    private const val RECOMMENDED_LENGTH = 12
    private const val SHORT_FACTOR = 0.6
    private const val SINGLE_CLASS_FACTOR = 0.85

    private val COMMON_PASSWORDS = listOf(
        "password", "passw0rd", "123456", "12345678", "123456789", "1234567890", "654321",
        "qwerty", "qwertyuiop", "asdfgh", "zxcvbn", "qazwsx", "letmein", "admin", "welcome",
        "iloveyou", "monkey", "dragon", "football", "baseball", "sunshine", "princess",
        "master", "shadow", "trustno1", "starwars", "superman", "freedom", "whatever",
        "secret", "login", "abc123", "111111", "000000", "hello", "charlie", "michael",
    ).sortedByDescending { it.length }

    private val KEYBOARD_SEQUENCES = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890")
        .flatMap { listOf(it, it.reversed()) }
    private const val KEYBOARD_RUN = 4

    private val LEET = mapOf('0' to 'o', '3' to 'e', '4' to 'a', '@' to 'a', '$' to 's', '5' to 's', '7' to 't')

    fun evaluate(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(
                StrengthLevel.VERY_WEAK,
                0.0,
                listOf(SUGGEST_LENGTH, SUGGEST_UPPER, SUGGEST_DIGITS, SUGGEST_SYMBOLS),
            )
        }

        val hasLower = password.any { it in 'a'..'z' }
        val hasUpper = password.any { it in 'A'..'Z' }
        val hasDigit = password.any { it in '0'..'9' }
        val hasSymbol = password.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9') }
        val classCount = listOf(hasLower, hasUpper, hasDigit, hasSymbol).count { it }
        val pool = (if (hasLower) LOWER_POOL else 0) + (if (hasUpper) UPPER_POOL else 0) +
            (if (hasDigit) DIGIT_POOL else 0) + (if (hasSymbol) SYMBOL_POOL else 0)
        val bitsPerChar = log2(pool.toDouble())

        val charBits = DoubleArray(password.length) { bitsPerChar }

        val repeated = markRepeats(password, charBits)
        val sequenced = markSequences(password, charBits)
        val commonMatches = markCommon(password, charBits)

        var bits = charBits.sum() + commonMatches * COMMON_WORD_BITS
        if (password.length < SHORT_LENGTH) bits *= SHORT_FACTOR
        if (classCount == 1) bits *= SINGLE_CLASS_FACTOR
        bits = bits.coerceAtLeast(0.0)

        val level = when {
            bits < WEAK_BITS -> StrengthLevel.VERY_WEAK
            bits < FAIR_BITS -> StrengthLevel.WEAK
            bits < STRONG_BITS -> StrengthLevel.FAIR
            bits < VERY_STRONG_BITS -> StrengthLevel.STRONG
            else -> StrengthLevel.VERY_STRONG
        }

        val suggestions = buildList {
            if (commonMatches > 0) add(SUGGEST_COMMON)
            if (password.length < RECOMMENDED_LENGTH) add(SUGGEST_LENGTH)
            if (!hasSymbol) add(SUGGEST_SYMBOLS)
            if (!hasDigit) add(SUGGEST_DIGITS)
            if (!hasUpper) add(SUGGEST_UPPER)
            if (!hasLower) add(SUGGEST_LOWER)
            if (repeated) add(SUGGEST_REPEATS)
            if (sequenced) add(SUGGEST_SEQUENCES)
        }

        return PasswordStrength(level, bits, suggestions)
    }

    /** "aaa": the third and later identical characters in a row are discounted. */
    private fun markRepeats(pw: String, charBits: DoubleArray): Boolean {
        var found = false
        var run = 1
        for (i in 1 until pw.length) {
            run = if (pw[i].lowercaseChar() == pw[i - 1].lowercaseChar()) run + 1 else 1
            if (run >= 3) {
                charBits[i] = minOf(charBits[i], PATTERN_CHAR_BITS)
                found = true
            }
        }
        return found
    }

    /**
     * "abc", "321", "xyz" (constant +/-1 step, run >= 3) and keyboard runs like "qwer"/"asdf"
     * (run >= 4). Only the character completing a run is discounted, so appending
     * characters never lowers the contribution of earlier ones.
     */
    private fun markSequences(pw: String, charBits: DoubleArray): Boolean {
        var found = false
        // Pattern offsets index the original UTF-16 array. Whole-string lowercase can
        // expand characters (for example U+0130), which would shift those offsets.
        val lower = String(CharArray(pw.length) { pw[it].lowercaseChar() })

        var run = 1
        var step = 0
        for (i in 1 until lower.length) {
            val a = lower[i - 1]
            val b = lower[i]
            val d = b - a
            val sameKind = (a.isLetter() && b.isLetter()) || (a.isDigit() && b.isDigit())
            if (sameKind && (d == 1 || d == -1)) {
                run = if (d == step) run + 1 else 2
                step = d
            } else {
                run = 1
                step = 0
            }
            if (run >= 3) {
                charBits[i] = minOf(charBits[i], PATTERN_CHAR_BITS)
                found = true
            }
        }

        // A character is discounted when it ends a keyboard run of KEYBOARD_RUN characters.
        for (k in KEYBOARD_RUN - 1 until lower.length) {
            val window = lower.substring(k - KEYBOARD_RUN + 1, k + 1)
            if (KEYBOARD_SEQUENCES.any { it.contains(window) }) {
                charBits[k] = minOf(charBits[k], PATTERN_CHAR_BITS)
                found = true
            }
        }
        return found
    }

    /** Common passwords (case-insensitive, also with simple leetspeak) become ~0 bits each char. */
    private fun markCommon(pw: String, charBits: DoubleArray): Int {
        // Pattern offsets index the original UTF-16 array. Whole-string lowercase can
        // expand characters (for example U+0130), which would shift those offsets.
        val lower = String(CharArray(pw.length) { pw[it].lowercaseChar() })
        val leet = String(CharArray(lower.length) { LEET[lower[it]] ?: lower[it] })
        val covered = BooleanArray(pw.length)
        var matches = 0
        for (word in COMMON_PASSWORDS) {
            for (candidate in listOf(lower, leet)) {
                var from = 0
                while (true) {
                    val idx = candidate.indexOf(word, from)
                    if (idx < 0) break
                    val range = idx until idx + word.length
                    if (range.any { !covered[it] }) {
                        range.forEach { covered[it] = true; charBits[it] = 0.0 }
                        matches++
                    }
                    from = idx + 1
                }
            }
        }
        return matches
    }
}
