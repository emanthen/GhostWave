package com.ghostwave.app.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.R
import com.ghostwave.app.data.model.Contact
import com.ghostwave.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onContactClick:  (contactId: String) -> Unit,
    onAddContact:    () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel:       ContactListViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("GhostWave",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground)
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onAddContact,
                containerColor = ElectricViolet,
                contentColor   = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, "Add contact")
            }
        },
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👻", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.contacts_empty),
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.ghostColors.placeholder,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.padding(horizontal = 48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to add your first contact",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.ghostColors.placeholder,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = { onContactClick(contact.id) },
                    )
                    HorizontalDivider(
                        color    = MaterialTheme.ghostColors.divider,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 72.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(ElectricViolet.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                contact.displayName.firstOrNull()?.uppercase() ?: "?",
                fontSize = 20.sp,
                color    = VioletLight,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text     = contact.displayName,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                contact.lastMessageAt?.let {
                    Text(
                        text  = formatTime(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (contact.unreadCount > 0) ElectricViolet
                                else MaterialTheme.ghostColors.placeholder,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = contact.lastMessagePreview ?: contact.ghostWaveId,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.ghostColors.placeholder,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contact.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text     = if (contact.unreadCount > 9) "9+" else contact.unreadCount.toString(),
                            fontSize = 10.sp,
                            color    = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
        }
    }
}

private val timeFmt  = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFmt  = SimpleDateFormat("MMM d", Locale.getDefault())
private fun formatTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    return if (now - epochMillis < 24 * 60 * 60 * 1000L) timeFmt.format(Date(epochMillis))
    else dateFmt.format(Date(epochMillis))
}
