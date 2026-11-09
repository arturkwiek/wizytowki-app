package com.wizytowki.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.data.db.dao.PersonDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(personDao: PersonDao) : ViewModel() {
    /** Number of saved contacts; collecting it opens (and on first run creates) the encrypted database. */
    val contactCount: StateFlow<Int> = personDao.countPersons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
