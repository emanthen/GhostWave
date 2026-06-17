package com.ghostwave.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.IdentityRepository
import com.ghostwave.app.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val identityRepo: IdentityRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val _gwId        = MutableStateFlow<String?>(null)
    private val _displayName = MutableStateFlow<String?>(null)
    val gwId:        StateFlow<String?> = _gwId.asStateFlow()
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    val notificationsEnabled:  StateFlow<Boolean> = settingsRepo.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notificationPreviews:  StateFlow<Boolean> = settingsRepo.notificationPreviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val appLockEnabled:        StateFlow<Boolean> = settingsRepo.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val readReceiptsEnabled:   StateFlow<Boolean> = settingsRepo.readReceiptsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val typingIndicators:      StateFlow<Boolean> = settingsRepo.typingIndicators
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val linkPreviewsEnabled:   StateFlow<Boolean> = settingsRepo.linkPreviewsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _gwId.value        = identityRepo.getGhostWaveId()
            _displayName.value = identityRepo.getDisplayName()
        }
    }

    fun setNotificationsEnabled(v: Boolean) = viewModelScope.launch { settingsRepo.setNotificationsEnabled(v) }
    fun setNotificationPreviews(v: Boolean) = viewModelScope.launch { settingsRepo.setNotificationPreviews(v) }
    fun setAppLockEnabled(v: Boolean)       = viewModelScope.launch { settingsRepo.setAppLockEnabled(v) }
    fun setReadReceiptsEnabled(v: Boolean)  = viewModelScope.launch { settingsRepo.setReadReceiptsEnabled(v) }
    fun setTypingIndicators(v: Boolean)     = viewModelScope.launch { settingsRepo.setTypingIndicators(v) }
    fun setLinkPreviewsEnabled(v: Boolean)  = viewModelScope.launch { settingsRepo.setLinkPreviewsEnabled(v) }
}
