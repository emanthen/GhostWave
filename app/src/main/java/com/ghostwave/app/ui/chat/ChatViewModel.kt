package com.ghostwave.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.data.MessageRepository
import com.ghostwave.app.data.model.Contact
import com.ghostwave.app.data.model.Message
import com.ghostwave.app.messaging.P2pMessagingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle:    SavedStateHandle,
    private val messageRepo: MessageRepository,
    private val contactRepo: ContactRepository,
    private val p2p:         P2pMessagingService,
) : ViewModel() {

    val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    val contact: StateFlow<Contact?> = contactRepo.observeContact(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<Message>> = messageRepo.observeMessages(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unreadCount: StateFlow<Int> = messageRepo.observeUnreadCount(contactId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState.asStateFlow()

    // ── Entered conversation — mark all read ──────────────────────────────
    init {
        viewModelScope.launch { messageRepo.markAllReadForContact(contactId) }
    }

    fun onDraftChanged(text: String) { _draftText.value = text }

    fun sendMessage() {
        val body = _draftText.value.trim()
        if (body.isBlank()) return
        _draftText.value = ""
        _sendState.value = SendState.Sending

        viewModelScope.launch {
            try {
                val message = messageRepo.sendTextMessage(contactId, body)
                val contact = contactRepo.getContactById(contactId) ?: return@launch
                // Hand off to P2P service — it encrypts + delivers (or queues if offline)
                p2p.deliverMessage(contact, message)
                _sendState.value = SendState.Idle
            } catch (e: Exception) {
                _sendState.value = SendState.Error(e.message ?: "Failed to send")
            }
        }
    }

    fun sendReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            p2p.sendReaction(contactId, messageId, emoji)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { messageRepo.deleteMessage(messageId) }
    }

    fun dismissError() { _sendState.value = SendState.Idle }
}

sealed interface SendState {
    data object Idle    : SendState
    data object Sending : SendState
    data class  Error(val message: String) : SendState
}
