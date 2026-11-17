package com.legenddrry.feature.tools.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.legenddrry.feature.tools.LocalToolsDependencies
import com.legenddrry.feature.tools.components.ToolsType
import com.legenddrry.feature.tools.contracts.SettingsRepository
import com.legenddrry.feature.tools.contracts.ThemeMode

// Development/test-only. Production always inherits Developer 1's app theme.
val SageClayLight = lightColorScheme(
    primary = Color(0xFF4A6651), onPrimary = Color(0xFFFBFBF7),
    primaryContainer = Color(0xFFDCE6D9), onPrimaryContainer = Color(0xFF2C4234),
    secondary = Color(0xFFAC5536), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E3D8), onSecondaryContainer = Color(0xFF7A3A22),
    tertiary = Color(0xFF4A6651), onTertiary = Color(0xFFFBFBF7),
    tertiaryContainer = Color(0xFFDCE6D9), onTertiaryContainer = Color(0xFF2C4234),
    background = Color(0xFFF3F4EC), onBackground = Color(0xFF1F2A22),
    surface = Color(0xFFFBFBF7), onSurface = Color(0xFF1F2A22),
    surfaceVariant = Color(0xFFE9ECE2), onSurfaceVariant = Color(0xFF5C6A5F),
    surfaceTint = Color(0xFF4A6651), surfaceContainer = Color(0xFFFBFBF7),
    surfaceContainerLowest = Color(0xFFFBFBF7), surfaceContainerLow = Color(0xFFFBFBF7),
    surfaceContainerHigh = Color(0xFFE9ECE2), surfaceContainerHighest = Color(0xFFE9ECE2),
    surfaceBright = Color(0xFFFBFBF7), surfaceDim = Color(0xFFE9ECE2),
    outline = Color(0xFF7D8A7F), outlineVariant = Color(0xFFDDE1D6),
    error = Color(0xFFA3343A), onError = Color.White,
    errorContainer = Color(0xFFF7E1DF), onErrorContainer = Color(0xFFA3343A),
    inverseSurface = Color(0xFF1F2A22), inverseOnSurface = Color(0xFFF3F4EC), inversePrimary = Color(0xFFDCE6D9),
    scrim = Color(0x66121814),
)
val SageClayDark = darkColorScheme(
    primary = Color(0xFF9CBBA3), onPrimary = Color(0xFF132017),
    primaryContainer = Color(0xFF2C3D31), onPrimaryContainer = Color(0xFFCFE2D2),
    secondary = Color(0xFFE8946F), onSecondary = Color(0xFF2A1309),
    secondaryContainer = Color(0xFF3A251C), onSecondaryContainer = Color(0xFFF3C2A9),
    tertiary = Color(0xFF9CBBA3), onTertiary = Color(0xFF132017),
    tertiaryContainer = Color(0xFF2C3D31), onTertiaryContainer = Color(0xFFCFE2D2),
    background = Color(0xFF121814), onBackground = Color(0xFFE8ECE3),
    surface = Color(0xFF1B231D), onSurface = Color(0xFFE8ECE3),
    surfaceVariant = Color(0xFF222B24), onSurfaceVariant = Color(0xFFA3AFA5),
    surfaceTint = Color(0xFF9CBBA3), surfaceContainer = Color(0xFF1B231D),
    surfaceContainerLowest = Color(0xFF1B231D), surfaceContainerLow = Color(0xFF1B231D),
    surfaceContainerHigh = Color(0xFF222B24), surfaceContainerHighest = Color(0xFF222B24),
    surfaceBright = Color(0xFF1B231D), surfaceDim = Color(0xFF121814),
    outline = Color(0xFF6B786E), outlineVariant = Color(0xFF2A332C),
    error = Color(0xFFF08A85), onError = Color(0xFF3B1E1D),
    errorContainer = Color(0xFF3B1E1D), onErrorContainer = Color(0xFFF08A85),
    inverseSurface = Color(0xFFE8ECE3), inverseOnSurface = Color(0xFF121814), inversePrimary = Color(0xFF4A6651),
    scrim = Color(0x8C000000),
)

@Composable
fun SageClayPreviewTheme(
    repository: SettingsRepository = LocalToolsDependencies.current.settings,
    content: @Composable () -> Unit,
) {
    val preferences by repository.preferences.collectAsStateWithLifecycle()
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) SageClayDark else SageClayLight,
        typography = ToolsType.materialTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}
