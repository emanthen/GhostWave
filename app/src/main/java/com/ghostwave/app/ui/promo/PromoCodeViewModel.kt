package com.ghostwave.app.ui.promo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.GhostWaveApplication
import com.ghostwave.app.promo.PromoCodeGate
import com.ghostwave.app.promo.PromoCodeRepository
import com.ghostwave.app.promo.PromoCodeType
import com.ghostwave.app.promo.PromoCodeValidator
import com.ghostwave.app.promo.PromoResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI State ─────────────────────────────────────────────────────────────────

sealed class PromoUiState {
    /** Initial state — input is empty or in progress of being typed. */
    data object Idle : PromoUiState()

    /** Verification request in flight. */
    data object Loading : PromoUiState()

    /** Verification succeeded — navigate after animation. */
    data class Success(val codeType: PromoCodeType) : PromoUiState()

    /** Verification failed with a specific reason. */
    data class Error(val reason: PromoResult, val attemptsRemaining: Int) : PromoUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class PromoCodeViewModel @Inject constructor(
    private val validator:   PromoCodeValidator,
    private val repository:  PromoCodeRepository,
    private val gate:        PromoCodeGate,
    private val application: GhostWaveApplication,
) : ViewModel() {

    private val _uiState       = MutableStateFlow<PromoUiState>(PromoUiState.Idle)
    val uiState: StateFlow<PromoUiState> = _uiState.asStateFlow()

    /** Formatted display value (GW-XXXX-XXXX-XXXX). Updated as user types. */
    private val _displayCode   = MutableStateFlow("")
    val displayCode: StateFlow<String> = _displayCode.asStateFlow()

    /** True when the currently displayed code passes the format check. */
    private val _isFormatValid = MutableStateFlow(false)
    val isFormatValid: StateFlow<Boolean> = _isFormatValid.asStateFlow()

    private var countdownJob: Job? = null

    // ── Input handling ────────────────────────────────────────────────────────

    /**
     * Called on every keystroke. Formats the raw input into GW-XXXX-XXXX-XXXX
     * and updates [displayCode] + [isFormatValid].
     */
    fun onCodeChanged(raw: String) {
        val formatted      = formatInput(raw)
        _displayCode.value = formatted
        _isFormatValid.value = validator.formatCheck(formatted)
        // Clear error when user starts typing again
        if (_uiState.value is PromoUiState.Error) {
            _uiState.value = PromoUiState.Idle
        }
    }

    // ── Verification ──────────────────────────────────────────────────────────

    fun onVerifyClicked() {
        val code = _displayCode.value
        if (!validator.formatCheck(code)) return   // button should be disabled, but guard anyway
        if (_uiState.value is PromoUiState.Loading) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = PromoUiState.Loading

            // Check lockout before calling validator (fast path)
            val lockoutUntil = repository.getLockoutUntil()
            if (lockoutUntil > System.currentTimeMillis()) {
                _uiState.value = PromoUiState.Error(
                    reason           = PromoResult.RateLimited(lockoutUntil),
                    attemptsRemaining = 0,
                )
                startCountdown(lockoutUntil)
                return@launch
            }

            when (val result = validator.validate(code)) {
                is PromoResult.Success -> {
                    repository.storeUnlock(validator.hash(validator.normalize(code)), result.type)
                    // Boot WebRTC and related subsystems now that gate is passed
                    application.onPromoUnlocked()
                    _uiState.value = PromoUiState.Success(result.type)
                }
                is PromoResult.RateLimited -> {
                    _uiState.value = PromoUiState.Error(
                        reason           = result,
                        attemptsRemaining = 0,
                    )
                    startCountdown(result.lockoutUntilMs)
                }
                else -> {
                    val remaining = recordFailure()
                    if (remaining <= 0) {
                        val until = repository.getLockoutUntil()
                        _uiState.value = PromoUiState.Error(
                            reason           = PromoResult.RateLimited(until),
                            attemptsRemaining = 0,
                        )
                        startCountdown(until)
                    } else {
                        _uiState.value = PromoUiState.Error(
                            reason           = result,
                            attemptsRemaining = remaining,
                        )
                    }
                }
            }
        }
    }

    // ── Lockout countdown ─────────────────────────────────────────────────────

    /**
     * Emits remaining lockout seconds, counting down to 0 once per second.
     * When it reaches 0, re-enables the UI by reverting to [PromoUiState.Idle].
     */
    fun getRemainingLockoutSeconds() = flow {
        while (true) {
            val until     = repository.getLockoutUntil()
            val remaining = ((until - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            emit(remaining)
            if (remaining == 0L) break
            delay(1_000L)
        }
    }.flowOn(Dispatchers.Default)

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun recordFailure(): Int {
        val count = repository.recordFailedAttempt()
        val max   = 20  // permanent lockout threshold
        return (max - count).coerceAtLeast(0)
    }

    private fun startCountdown(lockoutUntilMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remaining = ((lockoutUntilMs - System.currentTimeMillis()) / 1000L)
                    .coerceAtLeast(0L)
                if (remaining == 0L) {
                    _uiState.value = PromoUiState.Idle
                    break
                }
                delay(1_000L)
            }
        }
    }

    /**
     * Auto-formats raw keyboard input into GW-XXXX-XXXX-XXXX.
     * Strips non-alphanumeric, uppercases, inserts dashes at positions 2, 6, 10.
     */
    private fun formatInput(raw: String): String {
        val stripped = raw.uppercase().filter { it.isLetterOrDigit() }
        return buildString {
            stripped.forEachIndexed { i, c ->
                // Insert dashes before positions 2 (after GW), 6, 10
                if (i == 2 || i == 6 || i == 10) append('-')
                if (length < 18) append(c)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
