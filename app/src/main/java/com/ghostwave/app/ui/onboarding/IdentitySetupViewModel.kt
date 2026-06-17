package com.ghostwave.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class IdentitySetupUiState {
    data class Idle(val displayName: String = "") : IdentitySetupUiState()
    data object GeneratingKeys : IdentitySetupUiState()
    data class Error(val message: String) : IdentitySetupUiState()
    data object Done : IdentitySetupUiState()
}

/**
 * Drives the first-launch identity creation flow.
 *
 * On [createIdentity]:
 *   1. Transitions to GeneratingKeys (UI shows spinner)
 *   2. Calls IdentityRepository.createAndSaveIdentity() which:
 *        - Generates Curve25519 key pair via libsignal
 *        - Encrypts private key with Keystore AES-256-GCM
 *        - Stores encrypted blob + metadata in DataStore
 *   3. Transitions to Done → nav pops to QrShareScreen
 *
 * Error handling: any exception from the Keystore or libsignal is caught
 * and surfaced as IdentitySetupUiState.Error so the user can retry.
 */
@HiltViewModel
class IdentitySetupViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<IdentitySetupUiState>(IdentitySetupUiState.Idle())
    val uiState: StateFlow<IdentitySetupUiState> = _uiState

    fun onDisplayNameChanged(name: String) {
        val current = _uiState.value as? IdentitySetupUiState.Idle ?: return
        _uiState.value = current.copy(displayName = name)
    }

    fun createIdentity() {
        val name = (_uiState.value as? IdentitySetupUiState.Idle)?.displayName?.trim()
        if (name.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.value = IdentitySetupUiState.GeneratingKeys
            try {
                identityRepository.createAndSaveIdentity(name)
                _uiState.value = IdentitySetupUiState.Done
            } catch (e: Exception) {
                _uiState.value = IdentitySetupUiState.Error(
                    message = e.message ?: "Failed to generate identity keys. Please try again."
                )
            }
        }
    }
}
