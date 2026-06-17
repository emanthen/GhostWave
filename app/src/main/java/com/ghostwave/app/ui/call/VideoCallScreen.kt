package com.ghostwave.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.call.ActiveCall
import com.ghostwave.app.call.CallViewModel
import com.ghostwave.app.data.model.CallStatus
import com.ghostwave.app.ui.theme.*

/**
 * Video call screen with full-screen remote video, PiP local camera,
 * and overlay controls (mute, camera on/off, speaker, end call).
 *
 * Remote video surface is a placeholder Box — wired to WebRTC VideoTrack
 * via SurfaceViewRenderer in the production pass.
 */
@Composable
fun VideoCallScreen(
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
            .background(Color.Black),
    ) {
        // Remote video feed placeholder (production: SurfaceViewRenderer)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center,
        ) {
            if (activeCall?.status != CallStatus.CONNECTED) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        activeCall?.peerName ?: "…",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        statusLabel(activeCall),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // Top status bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    activeCall?.peerName ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    "🔒 DTLS-SRTP",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }

        // Local camera PiP (top-right)
        Box(
            Modifier
                .size(width = 100.dp, height = 140.dp)
                .align(Alignment.TopEnd)
                .padding(top = 80.dp, end = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (activeCall?.isCameraOn == true)
                        Color(0xFF2A2A3E)
                    else
                        Color.Black
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (activeCall?.isCameraOn == false) {
                Icon(Icons.Default.VideocamOff, "Camera off",
                    tint = Color.White.copy(alpha = 0.5f))
            } else {
                Text("You", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }

        // Bottom controls overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            VideoCallControl(
                icon    = if (activeCall?.isMuted == true)
                              Icons.Default.MicOff else Icons.Default.Mic,
                label   = "Mute",
                active  = activeCall?.isMuted == true,
                onClick = viewModel::toggleMute,
            )
            VideoCallControl(
                icon    = if (activeCall?.isCameraOn == true)
                              Icons.Default.Videocam else Icons.Default.VideocamOff,
                label   = "Camera",
                active  = activeCall?.isCameraOn == false,
                onClick = viewModel::toggleCamera,
            )
            EndCallButton(onClick = viewModel::endCall)
            VideoCallControl(
                icon    = if (activeCall?.isSpeakerOn == true)
                              Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                label   = "Speaker",
                active  = activeCall?.isSpeakerOn == true,
                onClick = viewModel::toggleSpeaker,
            )
            VideoCallControl(
                icon    = Icons.Default.FlipCameraAndroid,
                label   = "Flip",
                active  = false,
                onClick = { /* flip camera */ },
            )
        }
    }
}

@Composable
private fun VideoCallControl(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    label:   String,
    active:  Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick  = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    else        Color.White.copy(alpha = 0.2f)
                ),
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.7f))
    }
}

private fun statusLabel(call: ActiveCall?): String = when (call?.status) {
    CallStatus.CALLING   -> "Calling…"
    CallStatus.RINGING   -> "Ringing…"
    CallStatus.CONNECTED -> "Connected · Encrypted"
    else -> "…"
}
