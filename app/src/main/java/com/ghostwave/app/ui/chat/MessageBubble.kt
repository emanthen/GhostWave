package com.ghostwave.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.data.model.MessageDirection
import com.ghostwave.app.data.model.MessageStatus
import com.ghostwave.app.data.model.MessageType
import com.ghostwave.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val OutgoingShape = RoundedCornerShape(
    topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp,
)
private val IncomingShape = RoundedCornerShape(
    topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp,
)
private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message:    Message,
    onLongClick: (Message) -> Unit = {},
    modifier:   Modifier = Modifier,
) {
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val bubbleColor = if (isOutgoing) ElectricViolet else MaterialTheme.ghostColors.bubbleIncoming
    val textColor   = if (isOutgoing) Color.White    else MaterialTheme.colorScheme.onSurface
    val alignment   = if (isOutgoing) Alignment.End  else Alignment.Start
    val shape       = if (isOutgoing) OutgoingShape  else IncomingShape

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = alignment,
    ) {
        // Reply reference
        message.replyToMessageId?.let {
            ReplyRef(modifier = Modifier.padding(bottom = 2.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick    = {},
                    onLongClick = { onLongClick(message) },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            when {
                message.isDeleted -> {
                    Text(
                        "This message was deleted",
                        color = textColor.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    )
                }
                message.messageType == MessageType.IMAGE -> {
                    MediaBubbleContent(message, textColor)
                }
                else -> {
                    Column {
                        Text(
                            text  = message.body,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        BubbleFooter(message, textColor, isOutgoing)
                    }
                }
            }
        }

        // Reactions row
        if (message.reactionsJson != "{}") {
            ReactionsRow(
                reactionsJson = message.reactionsJson,
                modifier      = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun BubbleFooter(message: Message, textColor: Color, isOutgoing: Boolean) {
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text  = timeFmt.format(Date(message.timestamp)),
            color = textColor.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
        )
        if (isOutgoing) {
            Spacer(Modifier.width(4.dp))
            StatusTick(status = message.status, tint = textColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun StatusTick(status: MessageStatus, tint: Color) {
    val text = when (status) {
        MessageStatus.PENDING   -> "○"
        MessageStatus.SENT      -> "✓"
        MessageStatus.DELIVERED -> "✓✓"
        MessageStatus.READ      -> "✓✓"
        MessageStatus.FAILED    -> "✗"
    }
    val color = if (status == MessageStatus.READ) VioletLight else tint
    Text(text, color = color, fontSize = 10.sp)
}

@Composable
private fun MediaBubbleContent(message: Message, textColor: Color) {
    // Placeholder: actual image rendering via Coil in Step 18 polish
    Box(
        Modifier
            .size(width = 200.dp, height = 160.dp)
            .background(MaterialTheme.ghostColors.surface.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("📷", fontSize = 40.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReplyRef(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.ghostColors.surface.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("↩ Reply", color = VioletLight, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReactionsRow(reactionsJson: String, modifier: Modifier = Modifier) {
    // Parse {"👍":["GW-..."]} format
    val reactions = try {
        org.json.JSONObject(reactionsJson).let { obj ->
            obj.keys().asSequence().map { emoji ->
                val arr = obj.getJSONArray(emoji)
                emoji to arr.length()
            }.filter { (_, count) -> count > 0 }.toList()
        }
    } catch (_: Exception) { emptyList() }

    if (reactions.isEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.forEach { (emoji, count) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.ghostColors.surface)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("$emoji $count", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
