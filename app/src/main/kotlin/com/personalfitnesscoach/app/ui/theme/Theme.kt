package com.personalfitnesscoach.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalfitnesscoach.R

/**
 * The "Glow" look (D-099, `docs/phase3/design_system.md`): deep navy, frosted-glass cards, an orange-to-teal glow for what matters, big
 * white numbers. Dark by default (NFR-09: gyms are bright and phones are held at arm's length), light on request. Text keeps at least
 * 4.5:1 contrast (NFR-08); white on the orange button is bold and at least 18 sp (large text, 3:1 or better across the whole gradient).
 * `tools/check_contrast.py` checks every pair in CI. Colour is never the only signal: every state also has words.
 */
@Immutable
data class GlowColors(
    val dark: Boolean,
    /** The screen background, top to bottom. */
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    /** Frosted-glass cards: a light veil over the background with a hairline border. */
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val text: Color,
    val muted: Color,
    /** The main colour (orange) and its button gradient. */
    val warm: Color,
    val warmStart: Color,
    val warmEnd: Color,
    val onWarm: Color,
    /** Done states, coach answers, goals met, "Why?" links (teal). */
    val cool: Color,
    val link: Color,
    val caution: Color,
    val cautionTint: Color,
    val alert: Color,
    val alertTint: Color,
    /** The floating tab bar. */
    val bar: Color,
)

val DarkGlow = GlowColors(
    dark = true,
    bgTop = Color(0xFF101A2B), bgMid = Color(0xFF0A101C), bgBottom = Color(0xFF080D17),
    glass = Color(0x0EFFFFFF), glassStrong = Color(0x17FFFFFF), glassBorder = Color(0x1AFFFFFF),
    text = Color(0xFFF4F7FB), muted = Color(0xFF9DB0C6),
    // The button runs warmStart → warmEnd, dark enough for its white words (large bold text, ≥ 3:1; review R7-02); warm is the accent.
    warm = Color(0xFFFF6B2C), warmStart = Color(0xFFD9480F), warmEnd = Color(0xFFEE6424), onWarm = Color(0xFFFFFFFF),
    cool = Color(0xFF2DE2D0), link = Color(0xFF5BE7D9),
    caution = Color(0xFFFFCF7A), cautionTint = Color(0x1AFFB020),
    alert = Color(0xFFFF9DAA), alertTint = Color(0x1FFF5A6E),
    bar = Color(0xEB101A2B),
)

val LightGlow = GlowColors(
    dark = false,
    bgTop = Color(0xFFF4F7FB), bgMid = Color(0xFFF1F4F9), bgBottom = Color(0xFFEEF2F7),
    glass = Color(0xFFFFFFFF), glassStrong = Color(0xFFF6F8FB), glassBorder = Color(0xFFDCE3EC),
    text = Color(0xFF0E1726), muted = Color(0xFF4F5F73),
    warm = Color(0xFFE8551C), warmStart = Color(0xFFD9480F), warmEnd = Color(0xFFEE6424), onWarm = Color(0xFFFFFFFF),
    cool = Color(0xFF0E9C90), link = Color(0xFF0A7268),
    caution = Color(0xFF8A5600), cautionTint = Color(0x1FFFB020),
    alert = Color(0xFFB4203A), alertTint = Color(0x1AFF5A6E),
    bar = Color(0xF2FFFFFF),
)

val LocalGlow = staticCompositionLocalOf { DarkGlow }

private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A3D), onPrimary = Color(0xFF1A0A02),
    primaryContainer = Color(0xFF3A1E12), onPrimaryContainer = Color(0xFFFFD9C7),
    secondary = Color(0xFF2DE2D0), onSecondary = Color(0xFF032421),
    secondaryContainer = Color(0xFF113A3A), onSecondaryContainer = Color(0xFFBDF7F0),
    tertiary = Color(0xFFFFB020), onTertiary = Color(0xFF2A1A00),
    tertiaryContainer = Color(0xFF3A2A10), onTertiaryContainer = Color(0xFFFFE2B0),
    error = Color(0xFFFF5A6E), onError = Color(0xFF2B0006),
    errorContainer = Color(0xFF3D1520), onErrorContainer = Color(0xFFFFD1D7),
    background = Color(0xFF0A101C), onBackground = Color(0xFFF4F7FB),
    surface = Color(0xFF0F1828), onSurface = Color(0xFFF4F7FB),
    surfaceVariant = Color(0xFF18233A), onSurfaceVariant = Color(0xFF9DB0C6),
    surfaceContainerLowest = Color(0xFF0B1220), surfaceContainerLow = Color(0xFF111B2C),
    surfaceContainer = Color(0xFF141F31), surfaceContainerHigh = Color(0xFF17233A), surfaceContainerHighest = Color(0xFF1A2740),
    outline = Color(0xFF4A5B75), outlineVariant = Color(0xFF26334A),
    inverseSurface = Color(0xFFF4F7FB), inverseOnSurface = Color(0xFF0E1726),
)

private val Light = lightColorScheme(
    primary = Color(0xFFE8551C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE3D6), onPrimaryContainer = Color(0xFF5A1E05),
    secondary = Color(0xFF0B7F75), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2F4F0), onSecondaryContainer = Color(0xFF063F3A),
    tertiary = Color(0xFF8A5600), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFEBC7), onTertiaryContainer = Color(0xFF3A2400),
    error = Color(0xFFB4203A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDDE2), onErrorContainer = Color(0xFF4A0010),
    background = Color(0xFFF4F7FB), onBackground = Color(0xFF0E1726),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0E1726),
    surfaceVariant = Color(0xFFEEF2F7), onSurfaceVariant = Color(0xFF4F5F73),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF3F6FA), surfaceContainerHigh = Color(0xFFEEF2F7), surfaceContainerHighest = Color(0xFFE8EDF4),
    outline = Color(0xFF8A98AB), outlineVariant = Color(0xFFDCE3EC),
    inverseSurface = Color(0xFF0E1726), inverseOnSurface = Color(0xFFF4F7FB),
)

/** Plus Jakarta Sans, built into the app (no download; SIL Open Font License in assets/licenses). */
val Jakarta = FontFamily(
    Font(R.font.jakarta_medium, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

private fun TextStyle.j(weight: FontWeight) = copy(fontFamily = Jakarta, fontWeight = weight)

private fun typography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.j(FontWeight.ExtraBold), displayMedium = t.displayMedium.j(FontWeight.ExtraBold),
        displaySmall = t.displaySmall.j(FontWeight.ExtraBold),
        headlineLarge = t.headlineLarge.j(FontWeight.ExtraBold), headlineMedium = t.headlineMedium.j(FontWeight.ExtraBold),
        headlineSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp),
        titleLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    )
}

private val PfcShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** The large numbers of a set target and the rest timer (Phase 2 A1 "big numerals"). */
val Numerals = TextStyle(fontFamily = Jakarta, fontSize = 76.sp, lineHeight = 80.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp)

/** Medium numbers: tiles, the second number of a target. */
val NumeralsSmall = TextStyle(fontFamily = Jakarta, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)

/**
 * This style, growing with the phone's text size only up to [maxScale] times. The big numbers are already larger than body text at
 * 200%, and at twice their size "102.5 kg" or a 25:00 timer would break in the middle of the number (review R7-03, D-103).
 */
@Composable
fun TextStyle.grownAtMost(maxScale: Float): TextStyle {
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    if (scale <= maxScale) return this
    val k = maxScale / scale
    return copy(fontSize = fontSize * k, lineHeight = if (lineHeight.isSp) lineHeight * k else lineHeight)
}

@Composable
fun PfcTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlow provides if (dark) DarkGlow else LightGlow) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography(), shapes = PfcShapes, content = content)
    }
}
