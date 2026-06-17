package com.ghostwave.app.ui.onboarding

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.IdentityRepository
import com.ghostwave.app.util.QrCodeUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class QrShareUiState(
    val ghostWaveId: String   = "",
    val displayName: String   = "",
    val qrBitmap:    Bitmap?  = null,
    val isLoading:   Boolean  = true,
)

@HiltViewModel
class QrShareViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrShareUiState())
    val uiState: StateFlow<QrShareUiState> = _uiState

    init {
        viewModelScope.launch {
            val gwId    = identityRepository.getGhostWaveId()  ?: ""
            val name    = identityRepository.getDisplayName()   ?: ""
            val keyPair = identityRepository.getIdentityKeyPair()
            val pubKeyB64 = if (keyPair != null) {
                android.util.Base64.encodeToString(
                    keyPair.publicKey.publicKey.serialize(),
                    android.util.Base64.NO_WRAP,
                )
            } else ""

            // Generate QR bitmap on Default dispatcher (CPU-bound)
            val bitmap = withContext(Dispatchers.Default) {
                runCatching {
                    val payload = QrCodeUtil.encodeContactPayload(gwId, name, pubKeyB64)
                    QrCodeUtil.generateQrBitmap(payload, size = 512)
                }.getOrNull()
            }

            _uiState.value = QrShareUiState(
                ghostWaveId = gwId,
                displayName = name,
                qrBitmap    = bitmap,
                isLoading   = false,
            )
        }
    }
}
