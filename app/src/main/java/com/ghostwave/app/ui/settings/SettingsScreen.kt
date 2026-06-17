package com.ghostwave.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.navigation.Screen
import com.ghostwave.app.ui.theme.*

private data class SettingsItem(
    val title:    String,
    val subtitle: String,
    val icon:     ImageVector,
    val route:    String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:     () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel:  SettingsViewModel = hiltViewModel(),
) {
    val gwId        by viewModel.gwId.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()

    val items = listOf(
        SettingsItem("Profile",       "Name, GW-ID, QR code",                 Icons.Default.Person,        Screen.SettingsProfile.route),
        SettingsItem("Privacy",       "Disappearing messages, read receipts",  Icons.Default.Lock,          Screen.SettingsPrivacy.route),
        SettingsItem("Security",      "App lock, safety numbers, re-register", Icons.Default.Security,      Screen.SettingsSecurity.route),
        SettingsItem("Notifications", "Per-conversation mute, global mute",    Icons.Default.Notifications, Screen.SettingsNotifications.route),
        SettingsItem("Storage",       "IPFS cache, message history",           Icons.Default.Storage,       Screen.SettingsStorage.route),
        SettingsItem("Network",       "STUN/TURN servers, IPFS peers",         Icons.Default.NetworkWifi,   Screen.SettingsNetwork.route),
        SettingsItem("About",         "Version, licenses",                     Icons.Default.Info,          Screen.SettingsAbout.route),
    )

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = NavyBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
        ) {
            // Identity header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Text(
                        displayName ?: "GhostWave User",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        gwId ?: "No identity",
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricViolet,
                    )
                }
                HorizontalDivider(color = MaterialTheme.ghostColors.divider)
            }

            items(items) { item ->
                SettingsRow(item = item, onClick = { onNavigate(item.route) })
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun SettingsRow(item: SettingsItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, null, tint = ElectricViolet, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ghostColors.placeholder)
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.ghostColors.placeholder, modifier = Modifier.size(20.dp))
    }
}
