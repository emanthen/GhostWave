package com.ghostwave.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostwave.app.data.ContactRepository
import com.ghostwave.app.data.model.Contact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ContactListViewModel @Inject constructor(
    contactRepo: ContactRepository,
) : ViewModel() {

    val contacts: StateFlow<List<Contact>> = contactRepo.observeAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
