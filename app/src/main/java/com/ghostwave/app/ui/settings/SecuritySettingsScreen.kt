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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onBack:    () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Security") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            item {
                SettingsToggleRow(
                    title    = "App lock",
                    subtitle = "Require biometrics or PIN when opening GhostWave",
                    checked  = appLockEnabled,
                    onToggle = viewModel::setAppLockEnabled,
                )
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)

                // Safety numbers nav row
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text("Safety numbers", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text("Verify your encrypted connection with each contact",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.ghostColors.placeholder)
                }
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)

                // Encryption status info card
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.ghostColors.surface,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Encryption status", style = MaterialTheme.typography.titleSmall,
                            color = ElectricViolet)
                        Spacer(Modifier.height(8.dp))
                        StatusRow("Signal Protocol",  "Active · X3DH + Double Ratchet")
                        StatusRow("Key storage",      "Android Keystore (hardware-backed)")
                        StatusRow("Database",         "SQLCipher AES-256-GCM")
                        StatusRow("Calls",            "DTLS-SRTP (mandatory)")
                        StatusRow("File transfer",    "AES-256-GCM per-file key")
                        StatusRow("Push",             "Ping-only (zero content in FCM)")
                    }
                }

                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)

                // Delete identity
                TextButton(
                    onClick  = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors   = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete identity and all data")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor   = MaterialTheme.ghostColors.surface,
            title = { Text("Delete everything?", color = MaterialTheme.colorScheme.error) },
            text  = {
                Text(
                    "This permanently deletes your identity key, all messages, and all contacts. " +
                    "This action cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.ghostColors.placeholder)
                }
            },
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.ghostColors.placeholder, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.55f))
    }
}
