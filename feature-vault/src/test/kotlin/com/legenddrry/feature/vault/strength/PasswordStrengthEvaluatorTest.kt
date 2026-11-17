package com.legenddrry.feature.vault.strength

import com.legenddrry.feature.vault.generator.GeneratorOptions
import com.legenddrry.feature.vault.generator.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordStrengthEvaluatorTest {

    private fun level(pw: String) = PasswordStrengthEvaluator.evaluate(pw).level
    private val generator = PasswordGenerator()

    @Test
    fun emptyIsVeryWeak() {
        val s = PasswordStrengthEvaluator.evaluate("")
        assertEquals(StrengthLevel.VERY_WEAK, s.level)
        assertEquals(0.0, s.entropyBits, 0.0)
        assertTrue(s.suggestions.isNotEmpty())
        assertEquals(0.2f, s.fraction, 0.0001f)
    }

    @Test
    fun trivialPasswordsAreVeryWeak() {
        for (pw in listOf("weak", "password", "PASSWORD", "Password", "123456", "qwerty", "letmein", "aaaaaaaa", "abcdefgh")) {
            assertEquals("for '$pw'", StrengthLevel.VERY_WEAK, level(pw))
        }
    }

    @Test
    fun password1IsAtMostFair() {
        assertTrue(level("Password1") <= StrengthLevel.FAIR)
        assertTrue(level("Password1!") <= StrengthLevel.FAIR)
        assertTrue(level("P@ssw0rd") <= StrengthLevel.FAIR)
    }

    @Test
    fun commonPasswordAsMajorityIsPenalised() {
        assertTrue(level("iloveyou12") <= StrengthLevel.WEAK)
        assertTrue(level("Welcome2024") <= StrengthLevel.FAIR)
        assertTrue(level("admin!!") <= StrengthLevel.WEAK)
        assertTrue(PasswordStrengthEvaluator.evaluate("letmein99").suggestions.contains(PasswordStrengthEvaluator.SUGGEST_COMMON))
    }

    @Test
    fun sequencesAndRepeatsArePenalised() {
        val random = PasswordStrengthEvaluator.evaluate("k9Tz!m2Q").entropyBits
        assertTrue(PasswordStrengthEvaluator.evaluate("abc12345").entropyBits < random)
        assertTrue(PasswordStrengthEvaluator.evaluate("Zzzzzz1!").entropyBits < random)
        assertTrue(PasswordStrengthEvaluator.evaluate("qwer4567").entropyBits < random)
        assertTrue(PasswordStrengthEvaluator.evaluate("aaa").suggestions.contains(PasswordStrengthEvaluator.SUGGEST_REPEATS))
        assertTrue(PasswordStrengthEvaluator.evaluate("xabcx").suggestions.contains(PasswordStrengthEvaluator.SUGGEST_SEQUENCES))
    }

    @Test
    fun singleClassIsWeakerThanMixed() {
        assertTrue(
            PasswordStrengthEvaluator.evaluate("kqzvbmtrxw").entropyBits <
                PasswordStrengthEvaluator.evaluate("kQz7bM!rXw").entropyBits,
        )
    }

    @Test
    fun sixteenCharMixedRandomIsVeryStrong() {
        assertEquals(StrengthLevel.VERY_STRONG, level("q7#Lm2!vZ9xR@k4P"))
        repeat(500) {
            val pw = generator.generate(GeneratorOptions(length = 16))
            assertEquals("for '$pw'", StrengthLevel.VERY_STRONG, level(pw))
        }
    }

    @Test
    fun generatorOutputWithAllClassesIsAtLeastStrong() {
        for (len in GeneratorOptions.MIN_LENGTH..GeneratorOptions.MAX_LENGTH) {
            repeat(300) {
                val pw = generator.generate(GeneratorOptions(length = len))
                assertTrue("'$pw' rated ${level(pw)}", level(pw) >= StrengthLevel.STRONG)
            }
        }
    }

    @Test
    fun longerRandomPasswordIsNotWeaker() {
        repeat(300) {
            val pw = generator.generate(GeneratorOptions(length = 32))
            var prev = StrengthLevel.VERY_WEAK
            var prevBits = 0.0
            for (len in 1..32) {
                val s = PasswordStrengthEvaluator.evaluate(pw.take(len))
                assertTrue("prefix $len of '$pw' got weaker", s.level >= prev)
                prev = s.level
                if (len >= 12) {
                    assertTrue("bits dropped at prefix $len of '$pw'", s.entropyBits >= prevBits - 1e-9)
                }
                prevBits = s.entropyBits
            }
        }
    }

    @Test
    fun suggestionsMentionMissingThings() {
        val s = PasswordStrengthEvaluator.evaluate("kqzvbm")
        assertTrue(s.suggestions.contains(PasswordStrengthEvaluator.SUGGEST_LENGTH))
        assertTrue(s.suggestions.contains(PasswordStrengthEvaluator.SUGGEST_SYMBOLS))
        assertTrue(s.suggestions.contains(PasswordStrengthEvaluator.SUGGEST_DIGITS))
        assertTrue(s.suggestions.contains(PasswordStrengthEvaluator.SUGGEST_UPPER))
        assertTrue(PasswordStrengthEvaluator.evaluate("q7#Lm2!vZ9xR@k4P").suggestions.isEmpty())
    }

    @Test
    fun fractionTracksLevel() {
        assertEquals(1.0f, PasswordStrengthEvaluator.evaluate("q7#Lm2!vZ9xR@k4P").fraction, 0.0001f)
    }
}
