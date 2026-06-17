package com.ghostwave.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    contactId:      String,
    onBack:         () -> Unit,
    onAudioCall:    () -> Unit,
    onVideoCall:    () -> Unit,
    viewModel:      ChatViewModel = hiltViewModel(),
) {
    val contact      by viewModel.contact.collectAsStateWithLifecycle()
    val messages     by viewModel.messages.collectAsStateWithLifecycle()
    val draftText    by viewModel.draftText.collectAsStateWithLifecycle()
    val sendState    by viewModel.sendState.collectAsStateWithLifecycle()

    val listState    = rememberLazyListState()
    val coroutine    = rememberCoroutineScope()
    var contextMsg   by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutine.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                contact?.displayName?.firstOrNull()?.uppercase() ?: "?",
                                color = VioletLight, fontSize = 16.sp,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                contact?.displayName ?: "…",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            contact?.ghostWaveId?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.ghostColors.placeholder)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = onAudioCall) {
                        Icon(Icons.Default.Call, "Audio call", tint = ElectricViolet)
                    }
                    IconButton(onClick = onVideoCall) {
                        Icon(Icons.Default.Videocam, "Video call", tint = ElectricViolet)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBackground),
            )
        },
        bottomBar = {
            MessageInputBar(
                text       = draftText,
                onChange   = viewModel::onDraftChanged,
                onSend     = viewModel::sendMessage,
                isSending  = sendState is SendState.Sending,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                EmptyChatPlaceholder(
                    name = contact?.displayName ?: "",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state          = listState,
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(
                            message     = msg,
                            onLongClick = { contextMsg = it },
                        )
                    }
                }
            }

            if (sendState is SendState.Error) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    action = {
                        TextButton(onClick = viewModel::dismissError) {
                            Text("OK", color = VioletLight)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        (sendState as SendState.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    contextMsg?.let { msg ->
        MessageContextMenu(
            message   = msg,
            onReact   = { emoji -> viewModel.sendReaction(msg.id, emoji); contextMsg = null },
            onDelete  = { viewModel.deleteMessage(msg.id); contextMsg = null },
            onDismiss = { contextMsg = null },
        )
    }
}

@Composable
private fun MessageInputBar(
    text:      String,
    onChange:  (String) -> Unit,
    onSend:    () -> Unit,
    isSending: Boolean,
) {
    Surface(color = MaterialTheme.ghostColors.surface, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value         = text,
                onValueChange = onChange,
                modifier      = Modifier.weight(1f),
                placeholder   = { Text("Message…", color = MaterialTheme.ghostColors.placeholder) },
                maxLines      = 5,
                shape         = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ElectricViolet,
                    unfocusedBorderColor = MaterialTheme.ghostColors.divider,
                    focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
                    cursorColor          = ElectricViolet,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (text.isBlank()) MaterialTheme.ghostColors.divider else ElectricViolet),
                contentAlignment = Alignment.Center,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                        Icon(Icons.Default.Send, "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatPlaceholder(name: String, modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔒", fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Messages are end-to-end encrypted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.ghostColors.placeholder,
        )
        if (name.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Say hello to $name",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.ghostColors.placeholder,
            )
        }
    }
}

@Composable
private fun MessageContextMenu(
    message:   Message,
    onReact:   (String) -> Unit,
    onDelete:  () -> Unit,
    onDismiss: () -> Unit,
) {
    val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.ghostColors.surface,
        title            = null,
        text = {
            Column {
                Text("React", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.ghostColors.placeholder)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emojis.forEach { emoji ->
                        TextButton(onClick = { onReact(emoji) }) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
                if (!message.isDeleted &&
                    message.direction == com.ghostwave.app.data.model.MessageDirection.OUTGOING) {
                    HorizontalDivider(
                        color    = MaterialTheme.ghostColors.divider,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete for me", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.ghostColors.placeholder)
            }
        },
    )
}
