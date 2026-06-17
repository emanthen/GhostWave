package com.ghostwave.app.ui.promo

import android.app.Application
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

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class PromoUiState {
    data object Idle    : PromoUiState()
    data object Loading : PromoUiState()
    data class  Success(val codeType: PromoCodeType) : PromoUiState()
    data class  Error(val reason: PromoResult, val attemptsRemaining: Int) : PromoUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class PromoCodeViewModel @Inject constructor(
    private val validator:   PromoCodeValidator,
    private val repository:  PromoCodeRepository,
    private val gate:        PromoCodeGate,
    // Hilt provides Application; we cast to GhostWaveApplication when needed.
    private val application: Application,
) : ViewModel() {

    private val _uiState       = MutableStateFlow<PromoUiState>(PromoUiState.Idle)
    val uiState: StateFlow<PromoUiState> = _uiState.asStateFlow()

    private val _displayCode   = MutableStateFlow("")
    val displayCode: StateFlow<String> = _displayCode.asStateFlow()

    private val _isFormatValid = MutableStateFlow(false)
    val isFormatValid: StateFlow<Boolean> = _isFormatValid.asStateFlow()

    private var countdownJob: Job? = null

    // ── Input ─────────────────────────────────────────────────────────────────

    fun onCodeChanged(raw: String) {
        val formatted        = formatInput(raw)
        _displayCode.value   = formatted
        _isFormatValid.value = validator.formatCheck(formatted)
        if (_uiState.value is PromoUiState.Error) _uiState.value = PromoUiState.Idle
    }

    // ── Verification ──────────────────────────────────────────────────────────

    fun onVerifyClicked() {
        val code = _displayCode.value
        if (!validator.formatCheck(code)) return
        if (_uiState.value is PromoUiState.Loading) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = PromoUiState.Loading

            val lockoutUntil = repository.getLockoutUntil()
            if (lockoutUntil > System.currentTimeMillis()) {
                _uiState.value = PromoUiState.Error(PromoResult.RateLimited(lockoutUntil), 0)
                startCountdown(lockoutUntil)
                return@launch
            }

            when (val result = validator.validate(code)) {
                is PromoResult.Success -> {
                    repository.storeUnlock(
                        validator.hash(validator.normalize(code)), result.type,
                    )
                    (application as GhostWaveApplication).onPromoUnlocked()
                    _uiState.value = PromoUiState.Success(result.type)
                }
                is PromoResult.RateLimited -> {
                    _uiState.value = PromoUiState.Error(result, 0)
                    startCountdown(result.lockoutUntilMs)
                }
                else -> {
                    val remaining = recordFailure()
                    if (remaining <= 0) {
                        val until = repository.getLockoutUntil()
                        _uiState.value = PromoUiState.Error(PromoResult.RateLimited(until), 0)
                        startCountdown(until)
                    } else {
                        _uiState.value = PromoUiState.Error(result, remaining)
                    }
                }
            }
        }
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    fun getRemainingLockoutSeconds() = flow {
        while (true) {
            val remaining = ((repository.getLockoutUntil() - System.currentTimeMillis()) / 1000L)
                .coerceAtLeast(0L)
            emit(remaining)
            if (remaining == 0L) break
            delay(1_000L)
        }
    }.flowOn(Dispatchers.Default)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun recordFailure(): Int {
        val count = repository.recordFailedAttempt()
        return (20 - count).coerceAtLeast(0)
    }

    private fun startCountdown(lockoutUntilMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val remaining = ((lockoutUntilMs - System.currentTimeMillis()) / 1000L)
                    .coerceAtLeast(0L)
                if (remaining == 0L) { _uiState.value = PromoUiState.Idle; break }
                delay(1_000L)
            }
        }
    }

    private fun formatInput(raw: String): String {
        val stripped = raw.uppercase().filter { it.isLetterOrDigit() }
        return buildString {
            stripped.forEachIndexed { i, c ->
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
