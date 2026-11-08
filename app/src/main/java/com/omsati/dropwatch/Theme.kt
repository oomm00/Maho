package com.omsati.dropwatch

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// -------------------------------------------------------------
// COLOR TOKENS
// -------------------------------------------------------------
val DarkBackground = Color(0xFF0E0E0E)
val DarkSurface = Color(0xFF171716)
val DarkSurfaceRaised = Color(0xFF1F1F1D)
val DarkBorder = Color(0xFF2A2A27)
val DarkTextPrimary = Color(0xFFF4F1EA)
val DarkTextSecondary = Color(0xFF8F8B83)

val LightBackground = Color(0xFFF6F3EC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceRaised = Color(0xFFEBE6DA)
val LightBorder = Color(0xFFE4DFD3)
val LightTextPrimary = Color(0xFF141412)
val LightTextSecondary = Color(0xFF6F6A60)

val AccentVermilion = Color(0xFFFF4A1C)
val AccentVermilionLight = Color(0xFFE8390E)
val MutedGreen = Color(0xFF5BC48A)
val ErrorRed = Color(0xFFFF6B5E)

val DarkColorScheme = darkColorScheme(
    primary = AccentVermilion,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E150F),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = DarkTextSecondary,
    onSecondary = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceContainer = DarkSurfaceRaised,
    surfaceVariant = DarkSurfaceRaised,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = ErrorRed,
    onError = Color.White,
)

val LightColorScheme = lightColorScheme(
    primary = AccentVermilionLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFECE5),
    onPrimaryContainer = Color(0xFF5C1100),
    secondary = LightTextSecondary,
    onSecondary = LightTextPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceContainer = LightSurfaceRaised,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = ErrorRed,
    onError = Color.White,
)

// -------------------------------------------------------------
// FONTS & TYPOGRAPHY
// -------------------------------------------------------------
val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val SpaceGroteskFont = GoogleFont("Space Grotesk")
val InterFont = GoogleFont("Inter")

val DisplayFont = FontFamily(
    Font(googleFont = SpaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = SpaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.ExtraBold)
)

val BodyFont = FontFamily(
    Font(googleFont = InterFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = InterFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = InterFont, fontProvider = fontProvider, weight = FontWeight.SemiBold)
)

val MahoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.02).em,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.04.em,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.06.em,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.08.em,
    ),
)

// -------------------------------------------------------------
// SHAPES
// -------------------------------------------------------------
val MahoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MahoTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (isDark) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = MahoTypography,
        shapes = MahoShapes,
        content = content
    )
}
