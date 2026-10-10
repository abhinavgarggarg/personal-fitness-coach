package com.personalfitnesscoach.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dark by default (NFR-09: gyms are bright and phones are held at arm's length; a dark screen with large numerals reads best).
 * Text colours keep at least 4.5:1 contrast on their backgrounds (NFR-08); colour is never the only signal — every state also has words.
 */
private val Dark = darkColorScheme(
    primary = Color(0xFF86EFAC),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFFA5D8FF),
    onSecondary = Color(0xFF0B2540),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFFFDE68A),
    onTertiary = Color(0xFF3B2F04),
    tertiaryContainer = Color(0xFF4A3B07),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE6E9EC),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE6E9EC),
    surfaceVariant = Color(0xFF242B31),
    onSurfaceVariant = Color(0xFFC3CAD0),
    outline = Color(0xFF8B949C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val Light = lightColorScheme(
    primary = Color(0xFF14532D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF1E3A5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF0B2540),
    tertiary = Color(0xFF6B5408),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF3B2F04),
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF15191C),
    surface = Color(0xFFF8FAF9),
    onSurface = Color(0xFF15191C),
    surfaceVariant = Color(0xFFE2E7EA),
    onSurfaceVariant = Color(0xFF40484E),
    outline = Color(0xFF6B747C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** The large numbers of a set target (Phase 2 A1 "big numerals"). */
val Numerals = TextStyle(fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)

@Composable
fun PfcTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
}
