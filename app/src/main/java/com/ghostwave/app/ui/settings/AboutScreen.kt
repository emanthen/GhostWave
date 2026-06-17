package com.ghostwave.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack:    () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val gwId        by viewModel.gwId.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(32.dp))
                Text("👻", fontSize = 64.sp)
                Spacer(Modifier.height(8.dp))
                Text("GhostWave", style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("Vanish into the signal.", style = MaterialTheme.typography.bodyMedium,
                    color = ElectricViolet)
                Spacer(Modifier.height(32.dp))

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.ghostColors.surface,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        AboutRow("Your GW-ID", gwId ?: "—")
                        HorizontalDivider(color = MaterialTheme.ghostColors.divider, modifier = Modifier.padding(vertical = 8.dp))
                        AboutRow("Display name", displayName ?: "—")
                        HorizontalDivider(color = MaterialTheme.ghostColors.divider, modifier = Modifier.padding(vertical = 8.dp))
                        AboutRow("Version",   "1.0.0 (build 1)")
                        HorizontalDivider(color = MaterialTheme.ghostColors.divider, modifier = Modifier.padding(vertical = 8.dp))
                        AboutRow("Encryption", "Signal Protocol · AES-256-GCM · DTLS-SRTP")
                        HorizontalDivider(color = MaterialTheme.ghostColors.divider, modifier = Modifier.padding(vertical = 8.dp))
                        AboutRow("P2P",        "WebRTC · mDNS · IPFS")
                        HorizontalDivider(color = MaterialTheme.ghostColors.divider, modifier = Modifier.padding(vertical = 8.dp))
                        AboutRow("Push",       "FCM ping-only (zero content)")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "No central server. No message content ever leaves your device unencrypted.",
                    style   = MaterialTheme.typography.bodySmall,
                    color   = MaterialTheme.ghostColors.placeholder,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.ghostColors.placeholder)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
