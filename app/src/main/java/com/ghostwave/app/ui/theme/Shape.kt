package com.ghostwave.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val GhostWaveShapes = Shapes(
    // Small: chips, badges, message status icons
    small        = RoundedCornerShape(4.dp),
    // Medium: buttons, text fields, small cards
    medium       = RoundedCornerShape(12.dp),
    // Large: bottom sheets, dialogs, large cards
    large        = RoundedCornerShape(16.dp),
    // Extra large: full-screen modals, call screen rounded corners
    extraLarge   = RoundedCornerShape(28.dp),
)

// Message bubble shapes (outgoing has a cut corner top-right)
val BubbleShapeOutgoing = RoundedCornerShape(
    topStart    = 18.dp,
    topEnd      = 4.dp,   // cut — points toward the sender side
    bottomEnd   = 18.dp,
    bottomStart = 18.dp,
)

val BubbleShapeIncoming = RoundedCornerShape(
    topStart    = 4.dp,   // cut — points toward the sender side
    topEnd      = 18.dp,
    bottomEnd   = 18.dp,
    bottomStart = 18.dp,
)
