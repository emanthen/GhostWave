package com.ghostwave.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.util.GhostWaveIdUtil
import com.ghostwave.app.util.QrCodeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AddContactUiState {
    data object Idle : AddContactUiState()
    data object Scanning : AddContactUiState()
    data class ManualEntry(val gwId: String = "", val error: String? = null) : AddContactUiState()
    data object Adding : AddContactUiState()
    data class Success(val contactId: String) : AddContactUiState()
    data class Error(val message: String) : AddContactUiState()
}

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddContactUiState>(AddContactUiState.Idle)
    val uiState: StateFlow<AddContactUiState> = _uiState

    fun onQrScanned(rawContent: String) {
        val payload = QrCodeUtil.decodeContactPayload(rawContent) ?: run {
            _uiState.value = AddContactUiState.Error("Invalid GhostWave QR code.")
            return
        }
        addContact(payload)
    }

    fun onManualGwIdChanged(id: String) {
        _uiState.value = AddContactUiState.ManualEntry(gwId = id, error = null)
    }

    fun submitManualId() {
        val state = _uiState.value as? AddContactUiState.ManualEntry ?: return
        val normalised = GhostWaveIdUtil.normalise(state.gwId)
        if (normalised == null) {
            _uiState.value = state.copy(error = "Enter a valid GW-XXXX-XXXX ID.")
            return
        }
        // Without the peer's public key (only available via QR or X3DH in Step 5),
        // we store a placeholder and complete the key exchange on first message.
        viewModelScope.launch {
            _uiState.value = AddContactUiState.Adding
            try {
                val contactId = contactRepository.addContactManual(
                    gwId            = normalised,
                    displayName     = normalised,   // updated after X3DH in Step 5
                    publicKeyBase64 = "",            // fetched from IPFS prekey bundle in Step 5
                )
                _uiState.value = AddContactUiState.Success(contactId)
            } catch (e: Exception) {
                _uiState.value = AddContactUiState.Error(e.message ?: "Failed to add contact.")
            }
        }
    }

    private fun addContact(payload: QrCodeUtil.ContactPayload) {
        viewModelScope.launch {
            _uiState.value = AddContactUiState.Adding
            try {
                val contactId = contactRepository.addContactFromQr(payload)
                _uiState.value = AddContactUiState.Success(contactId)
            } catch (e: Exception) {
                _uiState.value = AddContactUiState.Error(e.message ?: "Failed to add contact.")
            }
        }
    }

    fun reset() { _uiState.value = AddContactUiState.Idle }
}
