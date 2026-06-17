package com.ghostwave.app.ui.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.call.ActiveCall
import com.ghostwave.app.call.CallViewModel
import com.ghostwave.app.data.model.CallStatus
import com.ghostwave.app.ui.theme.*

@Composable
fun AudioCallScreen(
    contactId:   String,
    isOutgoing:  Boolean,
    onCallEnded: () -> Unit,
    viewModel:   CallViewModel = hiltViewModel(),
) {
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()

    LaunchedEffect(activeCall) {
        if (activeCall == null) onCallEnded()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground),
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))

            // Avatar with pulse ring
            PulsingAvatar(
                name   = activeCall?.peerName ?: "…",
                active = activeCall?.status == CallStatus.CONNECTED,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                activeCall?.peerName ?: "…",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                statusLabel(activeCall),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.ghostColors.placeholder,
            )

            Spacer(Modifier.weight(1f))

            // Control buttons
            Row(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                CallControlButton(
                    icon    = if (activeCall?.isMuted == true)
                                  Icons.Default.MicOff else Icons.Default.Mic,
                    label   = if (activeCall?.isMuted == true) "Unmute" else "Mute",
                    tint    = if (activeCall?.isMuted == true) MaterialTheme.colorScheme.error else ElectricViolet,
                    onClick = viewModel::toggleMute,
                )
                EndCallButton(onClick = viewModel::endCall)
                CallControlButton(
                    icon    = if (activeCall?.isSpeakerOn == true)
                                  Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label   = "Speaker",
                    tint    = if (activeCall?.isSpeakerOn == true) ElectricViolet else MaterialTheme.ghostColors.placeholder,
                    onClick = viewModel::toggleSpeaker,
                )
            }
        }
    }
}

@Composable
private fun PulsingAvatar(name: String, active: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = if (active) 1.15f else 1f,
        animationSpec  = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulse ring
        if (active) {
            Box(
                Modifier
                    .size(140.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(ElectricViolet.copy(alpha = 0.15f)),
            )
        }
        // Avatar
        Box(
            Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(ElectricViolet.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                fontSize = 42.sp,
                color    = VioletLight,
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    label:   String,
    tint:    Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick  = onClick,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.ghostColors.surface),
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.ghostColors.placeholder)
    }
}

@Composable
fun EndCallButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick  = onClick,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Default.CallEnd, "End call",
                tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("End", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.ghostColors.placeholder)
    }
}

private fun statusLabel(call: ActiveCall?): String = when (call?.status) {
    CallStatus.CALLING   -> "Calling…"
    CallStatus.RINGING   -> "Ringing…"
    CallStatus.CONNECTED -> "Connected · Encrypted"
    CallStatus.MISSED    -> "Missed"
    CallStatus.DECLINED  -> "Declined"
    null, CallStatus.COMPLETED, CallStatus.FAILED -> "Ended"
}
