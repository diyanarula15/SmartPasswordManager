package com.example.group4app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.legenddrry.feature.tools.R as ToolsR

/** Manrope, bundled as font resources by the tools module (shared so the whole app uses one face). */
val Manrope = FontFamily(
    Font(ToolsR.font.tools_manrope_400, FontWeight.Normal),
    Font(ToolsR.font.tools_manrope_500, FontWeight.Medium),
    Font(ToolsR.font.tools_manrope_600, FontWeight.SemiBold),
    Font(ToolsR.font.tools_manrope_700, FontWeight.Bold),
)

private val Base = Typography()

private fun TextStyle.manrope(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
) = copy(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val Typography = Typography(
    displayLarge = Base.displayLarge.manrope(34, 40, FontWeight.Bold, -0.25),
    displayMedium = Base.displayMedium.manrope(34, 40, FontWeight.Bold, -0.25),
    displaySmall = Base.displaySmall.manrope(30, 36, FontWeight.Bold),
    headlineLarge = Base.headlineLarge.manrope(26, 32, FontWeight.Bold),
    headlineMedium = Base.headlineMedium.manrope(26, 32, FontWeight.Bold),
    headlineSmall = Base.headlineSmall.manrope(22, 28, FontWeight.Bold),
    titleLarge = Base.titleLarge.manrope(20, 26, FontWeight.SemiBold),
    titleMedium = Base.titleMedium.manrope(17, 24, FontWeight.SemiBold),
    titleSmall = Base.titleSmall.manrope(15, 20, FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.manrope(16, 24, FontWeight.Normal),
    bodyMedium = Base.bodyMedium.manrope(14, 20, FontWeight.Normal),
    bodySmall = Base.bodySmall.manrope(12, 16, FontWeight.Normal),
    labelLarge = Base.labelLarge.manrope(15, 20, FontWeight.SemiBold),
    labelMedium = Base.labelMedium.manrope(12, 16, FontWeight.SemiBold),
    labelSmall = Base.labelSmall.manrope(12, 16, FontWeight.Medium),
)
