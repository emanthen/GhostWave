package com.ghostwave.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack:    () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val notifEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val previews     by viewModel.notificationPreviews.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
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
                    title    = "Notifications",
                    subtitle = "Show notifications for new messages and calls",
                    checked  = notifEnabled,
                    onToggle = viewModel::setNotificationsEnabled,
                )
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)
                SettingsToggleRow(
                    title    = "Message previews",
                    subtitle = "Show message text in notification (off = privacy-first)",
                    checked  = previews,
                    onToggle = viewModel::setNotificationPreviews,
                )
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)
            }
        }
    }
}
