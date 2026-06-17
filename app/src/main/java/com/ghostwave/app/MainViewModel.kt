package com.ghostwave.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.IdentityRepository
import com.ghostwave.app.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Determines the navigation start destination on cold launch.
 *
 * Logic:
 *   - Identity exists → land on ContactList (normal flow)
 *   - No identity    → land on IdentitySetup (first launch)
 *
 * The null initial value keeps the UI hidden until the check
 * completes, preventing a one-frame flash of the wrong screen.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination

    init {
        viewModelScope.launch {
            _startDestination.value =
                if (identityRepository.hasIdentity()) Screen.ContactList.route
                else Screen.IdentitySetup.route
        }
    }
}
