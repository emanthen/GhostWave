package com.ghostwave.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onBack:    () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val readReceipts   by viewModel.readReceiptsEnabled.collectAsStateWithLifecycle()
    val typingIndicators by viewModel.typingIndicators.collectAsStateWithLifecycle()
    val linkPreviews   by viewModel.linkPreviewsEnabled.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
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
                    title    = "Read receipts",
                    subtitle = "Let contacts know when you've read their messages",
                    checked  = readReceipts,
                    onToggle = viewModel::setReadReceiptsEnabled,
                )
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)
                SettingsToggleRow(
                    title    = "Typing indicators",
                    subtitle = "Show when you're composing a message",
                    checked  = typingIndicators,
                    onToggle = viewModel::setTypingIndicators,
                )
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)
                SettingsToggleRow(
                    title    = "Link previews",
                    subtitle = "Auto-fetch metadata for links (connects to third-party servers)",
                    checked  = linkPreviews,
                    onToggle = viewModel::setLinkPreviewsEnabled,
                )
                HorizontalDivider(color = MaterialTheme.ghostColors.divider, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    title:    String,
    subtitle: String,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ghostColors.placeholder)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = ElectricViolet,
                checkedTrackColor  = ElectricViolet.copy(alpha = 0.4f),
            ),
        )
    }
}
