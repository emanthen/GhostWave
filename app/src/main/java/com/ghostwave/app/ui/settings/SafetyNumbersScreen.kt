package com.ghostwave.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.crypto.FingerprintUtil
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.data.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ghostwave.app.ui.theme.*

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class SafetyNumbersViewModel @Inject constructor(
    savedStateHandle:  SavedStateHandle,
    private val identityRepo: IdentityRepository,
    private val contactRepo:  ContactRepository,
) : ViewModel() {

    private val contactId: String = checkNotNull(savedStateHandle["contactId"])

    private val _safetyNumber = MutableStateFlow<String?>(null)
    val safetyNumber: StateFlow<String?> = _safetyNumber

    private val _contactName = MutableStateFlow("")
    val contactName: StateFlow<String> = _contactName

    private val _isVerified = MutableStateFlow(false)
    val isVerified: StateFlow<Boolean> = _isVerified

    init {
        viewModelScope.launch {
            val contact    = contactRepo.getContactById(contactId) ?: return@launch
            val localKp    = identityRepo.getIdentityKeyPair()    ?: return@launch
            val localGwId  = identityRepo.getGhostWaveId()        ?: return@launch

            _contactName.value = contact.displayName
            _isVerified.value  = contact.isVerified

            // FingerprintUtil is an object (pure functions) — call directly
            runCatching {
                val peerBytes = android.util.Base64.decode(contact.publicKeyBase64, android.util.Base64.NO_WRAP)
                val peerIk    = org.signal.libsignal.protocol.IdentityKey(peerBytes, 0)
                val localFp   = FingerprintUtil.computeFingerprint(localKp.publicKey, localGwId)
                val peerFp    = FingerprintUtil.computeFingerprint(peerIk, contact.ghostWaveId)
                val combined  = FingerprintUtil.combinedSafetyNumber(localFp, peerFp)
                _safetyNumber.value = FingerprintUtil.formatSafetyNumber(combined)
            }
        }
    }

    fun markVerified() {
        viewModelScope.launch {
            contactRepo.setVerified(contactId, true)
            _isVerified.value = true
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyNumbersScreen(
    onBack:    () -> Unit,
    viewModel: SafetyNumbersViewModel = hiltViewModel(),
) {
    val safetyNumber by viewModel.safetyNumber.collectAsState()
    val contactName  by viewModel.contactName.collectAsState()
    val isVerified   by viewModel.isVerified.collectAsState()

    Scaffold(
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = { Text("Safety Numbers") },
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
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                "Verify your connection with $contactName",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.ghostColors.placeholder,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            // Safety number display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.ghostColors.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (safetyNumber == null) {
                    CircularProgressIndicator(color = ElectricViolet, modifier = Modifier.size(32.dp))
                } else {
                    Text(
                        text       = safetyNumber!!,
                        style      = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily  = FontFamily.Monospace,
                            fontSize    = 14.sp,
                            lineHeight  = 24.sp,
                            letterSpacing = 1.sp,
                        ),
                        color      = MaterialTheme.colorScheme.onSurface,
                        textAlign  = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isVerified) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("Verified", color = SuccessGreen,
                        style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Warning, null, tint = WarningAmber)
                    Spacer(Modifier.width(8.dp))
                    Text("Not yet verified", color = WarningAmber,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Compare these numbers with $contactName in person or via a trusted channel. " +
                "If they match, tap Mark as Verified.",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.ghostColors.placeholder,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.weight(1f))

            if (!isVerified) {
                Button(
                    onClick  = viewModel::markVerified,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                ) {
                    Text("Mark as Verified")
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick  = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.ghostColors.divider),
            ) {
                Text("Close", color = MaterialTheme.ghostColors.placeholder)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
