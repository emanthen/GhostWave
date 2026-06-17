package com.ghostwave.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import com.google.accompanist.systemuicontroller.rememberSystemUiController

// GhostWave is dark-first and does not ship a light theme variant.
// If the OS switches to light mode, we still enforce the dark palette
// to maintain brand consistency and readability of encrypted content
// on the deep navy background.

private val GhostWaveDarkColorScheme: ColorScheme = darkColorScheme(
    primary              = md_primary,
    onPrimary            = md_onPrimary,
    primaryContainer     = md_primaryContainer,
    onPrimaryContainer   = md_onPrimaryContainer,

    secondary            = md_secondary,
    onSecondary          = md_onSecondary,
    secondaryContainer   = md_secondaryContainer,
    onSecondaryContainer = md_onSecondaryContainer,

    tertiary             = md_tertiary,
    onTertiary           = md_onTertiary,
    tertiaryContainer    = md_tertiaryContainer,
    onTertiaryContainer  = md_onTertiaryContainer,

    error                = md_error,
    onError              = md_onError,
    errorContainer       = md_errorContainer,
    onErrorContainer     = md_onErrorContainer,

    background           = md_background,
    onBackground         = md_onBackground,

    surface              = md_surface,
    onSurface            = md_onSurface,
    surfaceVariant       = md_surfaceVariant,
    onSurfaceVariant     = md_onSurfaceVariant,

    outline              = md_outline,
    outlineVariant       = md_outlineVariant,

    inverseSurface       = md_inverseSurface,
    inverseOnSurface     = md_inverseOnSurface,
    inversePrimary       = md_inversePrimary,

    surfaceTint          = md_surfaceTint,
    scrim                = md_scrim,
)

/**
 * GhostWave Material 3 theme.
 *
 * Always enforces the dark palette regardless of system setting.
 * Applies electric-violet / deep-navy brand colours and sets the
 * status bar + navigation bar to match the background.
 */
@Composable
fun GhostWaveTheme(
    content: @Composable () -> Unit,
) {
    val systemUiController = rememberSystemUiController()

    // Edge-to-edge: transparent bars, icons match dark background
    SideEffect {
        systemUiController.setSystemBarsColor(
            color = Color.Transparent,
            darkIcons = false,   // light icons on dark background
        )
        systemUiController.setNavigationBarColor(
            color = NavyBackground,
            darkIcons = false,
            navigationBarContrastEnforced = false,
        )
    }

    MaterialTheme(
        colorScheme = GhostWaveDarkColorScheme,
        typography  = GhostWaveTypography,
        content     = content,
    )
}

// ── Semantic colour extensions ────────────────────────────────────────────
// Access brand-specific colours that don't map 1:1 to M3 roles.
// Usage: MaterialTheme.ghostColors.bubbleOutgoing

data class GhostColors(
    val bubbleOutgoing: Color,
    val bubbleIncoming: Color,
    val successGreen:   Color,
    val warningAmber:   Color,
    val placeholder:    Color,
    val divider:        Color,
    val surface:        Color,
    val navyBackground: Color,
    val electricViolet: Color,
    val violetLight:    Color,
    val violetMuted:    Color,
)

val MaterialTheme.ghostColors: GhostColors
    get() = GhostColors(
        bubbleOutgoing = BubbleOutgoing,
        bubbleIncoming = BubbleIncoming,
        successGreen   = SuccessGreen,
        warningAmber   = WarningAmber,
        placeholder    = Placeholder,
        divider        = Divider,
        surface        = SurfaceDark,
        navyBackground = NavyBackground,
        electricViolet = ElectricViolet,
        violetLight    = VioletLight,
        violetMuted    = VioletMuted,
    )
