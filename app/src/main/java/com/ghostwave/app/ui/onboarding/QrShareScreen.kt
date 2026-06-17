package com.ghostwave.app.ui.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.ui.theme.*

@Composable
fun QrShareScreen(
    onContinue: () -> Unit,
    viewModel:  QrShareViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Your GhostWave ID",
            style     = MaterialTheme.typography.headlineMedium,
            color     = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        if (uiState.displayName.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(uiState.displayName, style = MaterialTheme.typography.titleSmall,
                color = VioletLight, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Share this QR code or GW-ID with contacts.",
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.ghostColors.placeholder,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // QR Code — real ZXing bitmap from ViewModel
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(2.dp, ElectricViolet, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(color = ElectricViolet)
                }
                uiState.qrBitmap != null -> {
                    Image(
                        bitmap      = uiState.qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR code for ${uiState.ghostWaveId}",
                        modifier    = Modifier
                            .size(220.dp)
                            .padding(8.dp),
                    )
                }
                else -> {
                    Text("QR", style = MaterialTheme.typography.headlineLarge, color = Color.Black)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // GW-ID chip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                    color = ElectricViolet, strokeWidth = 2.dp)
            } else {
                Text(
                    uiState.ghostWaveId.ifEmpty { "GW-????-????" },
                    style     = MaterialTheme.typography.titleMedium,
                    color     = VioletLight,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled  = !uiState.isLoading,
            colors   = ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text("Continue to Contacts", style = MaterialTheme.typography.labelLarge)
        }
    }
}
