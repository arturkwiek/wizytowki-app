package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface ContactDetailState {
    data object Loading : ContactDetailState

    /** The person was deleted or never existed. */
    data object Missing : ContactDetailState

    data class Ready(val details: ContactDetails) : ContactDetailState
}

/** Person card (REQUIREMENTS 8a, screen 7), kept up to date with the database. */
@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    queries: ContactQueries,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val personId: Long = checkNotNull(savedState.get<Long>(ARG_ID)) { "Missing $ARG_ID" }

    val state: StateFlow<ContactDetailState> = queries.contact(personId)
        .map { details -> details?.let { ContactDetailState.Ready(it) } ?: ContactDetailState.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailState.Loading)

    companion object {
        const val ARG_ID = "id"
    }
}
