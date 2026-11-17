// OWNER: worker "logic". LOCKED API — keep signatures. (Skeleton stub; replace body.)
package com.legenddrry.feature.vault.strength

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

object PasswordStrengthEvaluator {
    fun evaluate(password: String): PasswordStrength {
        // STUB: length only.
        val level = when {
            password.length < 8 -> StrengthLevel.VERY_WEAK
            password.length < 10 -> StrengthLevel.WEAK
            password.length < 12 -> StrengthLevel.FAIR
            password.length < 16 -> StrengthLevel.STRONG
            else -> StrengthLevel.VERY_STRONG
        }
        return PasswordStrength(level, password.length * 4.0, emptyList())
    }
}
