package com.berlin.porntv.ui.theme


import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Define your Material 3 dark color scheme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF4081),              // Pink A200 - Example
    onPrimary = Color.Black,                  // Text/icons on primary
    primaryContainer = Color(0xFFF50057),        // A darker/lighter shade or for larger surfaces
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF03DAC5),            // Teal A200 - Example
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF00BFA5),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFFB388FF),             // Deep Purple A100 - Example for accents
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF7C4DFF),
    onTertiaryContainer = Color.White,
    error = Color(0xFFCF6679),                // Standard M3 error color for dark themes
    onError = Color.Black,
    errorContainer = Color(0xFFB00020),
    onErrorContainer = Color.White,
    background = Color(0xFF121212),            // Common dark background
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),                // Surfaces like cards, sheets
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),        // A slightly different surface color
    onSurfaceVariant = Color.White,
    outline = Color(0xFF8C8C8C),
    inverseOnSurface = Color(0xFF1E1E1E),
    inverseSurface = Color.White,
    inversePrimary = Color(0xFFC0007A),       // For elements on inverseSurface that need primary emphasis
    surfaceTint = Color(0xFFFF4081),          // Tint color for surfaces, often the primary color
    outlineVariant = Color(0xFF4A4A4A),
    scrim = Color.Black,
)

@Composable
fun TvAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme, // Use the M3 ColorScheme
        typography = AppTypography,     // Assuming Typography is M3 compatible
        shapes = Shapes,           // Assuming Shapes are M3 compatible
        content = content
    )
}