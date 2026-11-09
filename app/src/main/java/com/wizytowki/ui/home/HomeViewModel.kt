package com.wizytowki.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.data.db.dao.PersonDao
import com.wizytowki.scan.DraftStore
import com.wizytowki.scan.ScanSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    personDao: PersonDao,
    private val draftStore: DraftStore,
) : ViewModel() {
    /** Number of saved contacts; collecting it opens (and on first run creates) the encrypted database. */
    val contactCount: StateFlow<Int> = personDao.countPersons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Interrupted scan that can be resumed (UI-09). */
    val draft: StateFlow<ScanSession?> = draftStore.draft

    init {
        viewModelScope.launch { draftStore.load() }
    }

    fun discardDraft() {
        viewModelScope.launch { draftStore.clear() }
    }
}
