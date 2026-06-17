package com.ghostwave.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using system default font family (Roboto on Android) to avoid bundling a
// custom font in early iterations. Swap to a custom FontFamily here once the
// final brand font is decided.
private val GhostWaveFontFamily = FontFamily.Default

val GhostWaveTypography = Typography(
    // Large display: used on splash / onboarding headline
    displayLarge = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
        color = OnSurface,
    ),
    displayMedium = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
        color = OnSurface,
    ),
    displaySmall = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
        color = OnSurface,
    ),

    // Headlines: screen titles, chat header
    headlineLarge = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
        color = OnSurface,
    ),
    headlineMedium = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
        color = OnSurface,
    ),
    headlineSmall = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        color = OnSurface,
    ),

    // Titles: list item names, toolbar
    titleLarge = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = OnSurface,
    ),
    titleMedium = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = OnSurface,
    ),
    titleSmall = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = OnSurface,
    ),

    // Body: message text
    bodyLarge = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        color = OnSurface,
    ),
    bodyMedium = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = OnSurface,
    ),
    bodySmall = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = OnSurfaceVariant,
    ),

    // Labels: timestamps, status indicators, chips
    labelLarge = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = OnSurface,
    ),
    labelMedium = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = OnSurfaceVariant,
    ),
    labelSmall = TextStyle(
        fontFamily = GhostWaveFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = OnSurfaceVariant,
    ),
)
