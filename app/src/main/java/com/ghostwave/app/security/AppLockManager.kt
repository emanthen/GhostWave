package com.ghostwave.app.security

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import com.ghostwave.app.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppLockManager"

/**
 * Manages app-lock state using BiometricPrompt (fingerprint, face, or PIN).
 *
 * Lock policy:
 *   - App locks when moved to background for > [SettingsRepository.screenLockTimeoutSecs].
 *   - BiometricPrompt is shown in [MainActivity.onResume] if [isLocked] = true.
 *   - Hardware-backed biometrics preferred; falls back to device credential (PIN/pattern).
 *
 * Security notes:
 *   - We do NOT roll our own auth — BiometricPrompt uses the platform's trusted UI.
 *   - Biometric auth is NOT used to derive encryption keys (key material is in Keystore).
 *     App lock is a UX gate, not an additional crypto layer.
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
) {
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var backgroundedAtMillis = 0L

    fun onAppForegrounded() {
        val timeoutSecs = runBlocking { settingsRepo.screenLockTimeoutSecs.first() }
        val enabled     = runBlocking { settingsRepo.appLockEnabled.first() }

        if (!enabled) {
            _isLocked.value = false
            return
        }

        val elapsed = System.currentTimeMillis() - backgroundedAtMillis
        if (backgroundedAtMillis > 0 && elapsed > timeoutSecs * 1000) {
            _isLocked.value = true
            Log.d(TAG, "App locked after ${elapsed}ms in background")
        }
    }

    fun onAppBackgrounded() {
        backgroundedAtMillis = System.currentTimeMillis()
    }

    /**
     * Shows the system BiometricPrompt to unlock the app.
     * [activity] must be a FragmentActivity (e.g. MainActivity).
     */
    fun promptBiometric(activity: ComponentActivity, onSuccess: () -> Unit, onFail: () -> Unit) {
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate  = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // Device has no biometrics — unlock immediately (no gate possible)
            Log.w(TAG, "No biometric available (code=$canAuthenticate), unlocking")
            _isLocked.value = false
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                _isLocked.value = false
                Log.i(TAG, "Biometric auth succeeded")
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                Log.w(TAG, "Biometric error: $errorCode $errString")
                onFail()
            }
            override fun onAuthenticationFailed() {
                Log.w(TAG, "Biometric attempt failed")
                // Don't call onFail — user may retry
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock GhostWave")
            .setSubtitle("Use biometrics or device PIN to continue")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }

    fun unlock() { _isLocked.value = false }
}
