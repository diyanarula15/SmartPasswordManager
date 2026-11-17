package com.legenddrry.feature.tools.components

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.legenddrry.feature.tools.R

/** Feature typography only; the application owns MaterialTheme and its colour scheme. */
object ToolsType {
    val family = FontFamily(
        Font(R.font.tools_manrope_400, FontWeight.Normal),
        Font(R.font.tools_manrope_500, FontWeight.Medium),
        Font(R.font.tools_manrope_600, FontWeight.SemiBold),
        Font(R.font.tools_manrope_700, FontWeight.Bold),
    )
    val display = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp)
    val headline = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp)
    val titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp)
    val title = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp)
    val bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp)
    val caption = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val overline = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)
    /** Optional host typography input; this value does not install a theme. */
    val materialTypography = Typography(
        displaySmall = display, headlineSmall = headline, headlineMedium = headline,
        titleLarge = titleLarge, titleMedium = title, bodyLarge = bodyLarge,
        bodyMedium = body, labelLarge = label, labelMedium = caption, labelSmall = overline,
    )
}
