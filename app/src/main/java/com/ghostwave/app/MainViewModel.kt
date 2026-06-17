package com.ghostwave.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.IdentityRepository
import com.ghostwave.app.navigation.Screen
import com.ghostwave.app.promo.PromoCodeGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Determines the navigation start destination on cold launch.
 *
 * Priority order:
 *   1. Promo gate not passed         → PromoCode (mandatory)
 *   2. Gate passed, no identity yet  → IdentitySetup
 *   3. Gate passed, identity exists  → ContactList
 *
 * Null initial value keeps UI hidden until check completes,
 * preventing a one-frame flash of the wrong screen.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val promoCodeGate:      PromoCodeGate,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    init {
        viewModelScope.launch {
            _startDestination.value = when {
                promoCodeGate.shouldShowGate()       -> Screen.PromoCode.route
                identityRepository.hasIdentity()     -> Screen.ContactList.route
                else                                 -> Screen.IdentitySetup.route
            }
        }
    }
}
