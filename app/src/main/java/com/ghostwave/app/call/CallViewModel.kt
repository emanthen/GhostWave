package com.ghostwave.app.call

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    private val callManager: CallManager,
) : ViewModel() {

    val activeCall: StateFlow<ActiveCall?> = callManager.activeCall

    fun toggleMute()    = callManager.toggleMute()
    fun toggleCamera()  = callManager.toggleCamera()
    fun toggleSpeaker() = callManager.toggleSpeaker()
    fun endCall()       = callManager.endCall()
    fun acceptCall()    = callManager.acceptCall()
}
