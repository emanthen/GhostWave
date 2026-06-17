package com.ghostwave.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── GhostWave brand palette ────────────────────────────────────────────────

/** Primary background — deep space navy */
val NavyBackground    = Color(0xFF0D0F1A)
/** Slightly lighter surface for cards / bottom sheets */
val SurfaceDark       = Color(0xFF12152A)
/** Card / container background */
val SurfaceVariant    = Color(0xFF1A1D2E)
/** Elevated surface (modals, dialogs) */
val SurfaceElevated   = Color(0xFF20243A)

/** Primary accent — electric violet */
val ElectricViolet    = Color(0xFF7C3AED)
/** Lighter violet for secondary accents and icons */
val VioletLight       = Color(0xFFA78BFA)
/** Muted violet for disabled / hint states */
val VioletMuted       = Color(0xFF4C2A8A)
/** Violet container (chip backgrounds, badges) */
val VioletContainer   = Color(0xFF2D1B5E)

/** On-surface text — near-white */
val OnSurface         = Color(0xFFE8E8F0)
/** Secondary text — muted grey */
val OnSurfaceVariant  = Color(0xFF9090A8)
/** Placeholder / disabled text */
val Placeholder       = Color(0xFF55556A)

/** Destructive actions (delete, end call) */
val ErrorRed          = Color(0xFFCF4040)
val ErrorRedContainer = Color(0xFF4A1515)

/** Success (delivered, connected) */
val SuccessGreen      = Color(0xFF34C759)
val SuccessContainer  = Color(0xFF0D3320)

/** Warning (safety number mismatch, missed call) */
val WarningAmber      = Color(0xFFFF9F0A)
val WarningContainer  = Color(0xFF3D2800)

/** Outgoing message bubble */
val BubbleOutgoing    = Color(0xFF2D1B5E)   // dark violet tint
/** Incoming message bubble */
val BubbleIncoming    = Color(0xFF1A1D2E)   // surface variant

/** Divider / outline */
val Divider           = Color(0xFF2A2D40)

// ── Material 3 role overrides ──────────────────────────────────────────────
// These are mapped to M3 color scheme roles in Theme.kt.

val md_primary              = ElectricViolet
val md_onPrimary            = Color(0xFFFFFFFF)
val md_primaryContainer     = VioletContainer
val md_onPrimaryContainer   = VioletLight

val md_secondary            = VioletLight
val md_onSecondary          = NavyBackground
val md_secondaryContainer   = Color(0xFF231545)
val md_onSecondaryContainer = Color(0xFFD0BCFF)

val md_tertiary             = SuccessGreen
val md_onTertiary           = Color(0xFF003822)
val md_tertiaryContainer    = SuccessContainer
val md_onTertiaryContainer  = Color(0xFF9AE6B4)

val md_error                = ErrorRed
val md_onError              = Color(0xFFFFFFFF)
val md_errorContainer       = ErrorRedContainer
val md_onErrorContainer     = Color(0xFFFFB4AB)

val md_background           = NavyBackground
val md_onBackground         = OnSurface

val md_surface              = SurfaceDark
val md_onSurface            = OnSurface
val md_surfaceVariant       = SurfaceVariant
val md_onSurfaceVariant     = OnSurfaceVariant

val md_outline              = Divider
val md_outlineVariant       = Color(0xFF1E2035)

val md_inverseSurface       = OnSurface
val md_inverseOnSurface     = NavyBackground
val md_inversePrimary       = VioletMuted

val md_surfaceTint          = ElectricViolet
val md_scrim                = Color(0x99000000)
