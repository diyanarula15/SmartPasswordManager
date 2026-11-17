package com.legenddrry.feature.vault.generator

import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.DIGITS
import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.LOWERCASE
import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.SYMBOLS
import com.legenddrry.feature.vault.generator.PasswordGenerator.Companion.UPPERCASE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    private val generator = PasswordGenerator()

    @Test
    fun boundaryLengthsAreAccepted() {
        assertEquals(12, generator.generate(GeneratorOptions(length = 12)).length)
        assertEquals(32, generator.generate(GeneratorOptions(length = 32)).length)
        assertEquals(GeneratorOptions.DEFAULT_LENGTH, generator.generate(GeneratorOptions()).length)
    }

    @Test(expected = IllegalArgumentException::class)
    fun lengthBelowMinimumThrows() {
        generator.generate(GeneratorOptions(length = 11))
    }

    @Test(expected = IllegalArgumentException::class)
    fun lengthAboveMaximumThrows() {
        generator.generate(GeneratorOptions(length = 33))
    }

    @Test(expected = IllegalArgumentException::class)
    fun noCategoryThrows() {
        generator.generate(
            GeneratorOptions(uppercase = false, lowercase = false, digits = false, symbols = false),
        )
    }

    @Test
    fun everyLengthInRangeProducesThatLength() {
        for (len in GeneratorOptions.MIN_LENGTH..GeneratorOptions.MAX_LENGTH) {
            assertEquals(len, generator.generate(GeneratorOptions(length = len)).length)
        }
    }

    @Test
    fun singleCategoryProducesOnlyThatCategory() {
        val cases = listOf(
            GeneratorOptions(uppercase = true, lowercase = false, digits = false, symbols = false) to UPPERCASE,
            GeneratorOptions(uppercase = false, lowercase = true, digits = false, symbols = false) to LOWERCASE,
            GeneratorOptions(uppercase = false, lowercase = false, digits = true, symbols = false) to DIGITS,
            GeneratorOptions(uppercase = false, lowercase = false, digits = false, symbols = true) to SYMBOLS,
        )
        for ((options, set) in cases) {
            repeat(200) {
                val pw = generator.generate(options)
                assertTrue("'$pw' has chars outside '$set'", pw.all { it in set })
            }
        }
    }

    @Test
    fun allFifteenCombinationsAlwaysContainEachEnabledCategory() {
        var combos = 0
        for (mask in 1..15) {
            combos++
            val options = GeneratorOptions(
                length = if (mask % 2 == 0) GeneratorOptions.MIN_LENGTH else 20,
                uppercase = mask and 1 != 0,
                lowercase = mask and 2 != 0,
                digits = mask and 4 != 0,
                symbols = mask and 8 != 0,
            )
            val enabled = buildList {
                if (options.uppercase) add(UPPERCASE)
                if (options.lowercase) add(LOWERCASE)
                if (options.digits) add(DIGITS)
                if (options.symbols) add(SYMBOLS)
            }
            val allowed = enabled.joinToString("")
            repeat(500) {
                val pw = generator.generate(options)
                assertEquals(options.length, pw.length)
                assertTrue("'$pw' has disallowed chars for mask $mask", pw.all { it in allowed })
                for (set in enabled) {
                    assertTrue("'$pw' misses a char from '$set' (mask $mask)", pw.any { it in set })
                }
            }
        }
        assertEquals(15, combos)
    }

    @Test
    fun guaranteeHoldsAtMinimumLengthWithAllCategories() {
        // Stress the "one from each" + shuffle path where it matters most.
        repeat(5_000) {
            val pw = generator.generate(GeneratorOptions(length = 12))
            assertTrue(pw.any { it in UPPERCASE })
            assertTrue(pw.any { it in LOWERCASE })
            assertTrue(pw.any { it in DIGITS })
            assertTrue(pw.any { it in SYMBOLS })
        }
    }

    @Test
    fun thousandGenerationsAreAllDistinct() {
        val seen = HashSet<String>()
        repeat(1_000) { seen += generator.generate(GeneratorOptions(length = 16)) }
        assertEquals(1_000, seen.size)
    }

    @Test
    fun characterFrequencyIsRoughlyUniformWithinCategory() {
        // Lowercase only: 26 symbols. 20k passwords * 16 chars = 320k samples, ~12.3k expected each.
        val options = GeneratorOptions(length = 16, uppercase = false, lowercase = true, digits = false, symbols = false)
        val counts = IntArray(26)
        repeat(20_000) { generator.generate(options).forEach { counts[it - 'a']++ } }
        val expected = counts.sum() / 26.0
        counts.forEachIndexed { i, c ->
            assertTrue("'${'a' + i}' count $c too far from $expected", c in (expected * 0.9).toInt()..(expected * 1.1).toInt())
        }
    }

    @Test
    fun guaranteedCharactersAreNotPinnedToFixedPositions() {
        // If the shuffle were missing, position 0 would always be uppercase. Check each position
        // sees every category over many runs.
        val options = GeneratorOptions(length = 12)
        val sets = listOf(UPPERCASE, LOWERCASE, DIGITS, SYMBOLS)
        val seen = Array(12) { BooleanArray(4) }
        repeat(2_000) {
            val pw = generator.generate(options)
            pw.forEachIndexed { pos, ch -> seen[pos][sets.indexOfFirst { ch in it }] = true }
        }
        seen.forEachIndexed { pos, cats -> assertTrue("position $pos lacks a category", cats.all { it }) }
    }
}
