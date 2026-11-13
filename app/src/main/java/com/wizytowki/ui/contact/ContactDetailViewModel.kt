package com.wizytowki.ui.contact

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.domain.CardMerger
import com.wizytowki.domain.ContactDraft
import com.wizytowki.extract.EntityExtractor
import com.wizytowki.ocr.CardOcr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Note being added on the person card (#115). */
data class NoteState(val text: String = "", val saving: Boolean = false, val failed: Boolean = false)

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
    private val contacts: ContactRepository,
    private val extractor: EntityExtractor,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val personId: Long = checkNotNull(savedState.get<Long>(ARG_ID)) { "Missing $ARG_ID" }

    val state: StateFlow<ContactDetailState> = queries.contact(personId)
        .map { details -> details?.let { ContactDetailState.Ready(it) } ?: ContactDetailState.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactDetailState.Loading)

    private val _movedTo = MutableStateFlow<Long?>(null)

    /** New person a card was moved to; the screen opens their card, then calls [movedShown]. */
    val movedTo: StateFlow<Long?> = _movedTo.asStateFlow()

    private val _splitFailed = MutableStateFlow(false)
    val splitFailed: StateFlow<Boolean> = _splitFailed.asStateFlow()

    private val _note = MutableStateFlow(NoteState())

    /** A note being written on the card (#115). */
    val note: StateFlow<NoteState> = _note.asStateFlow()

    fun editNote(text: String) {
        _note.value = _note.value.copy(text = text, failed = false)
    }

    fun addDictation(text: String) {
        _note.value = _note.value.copy(text = ContactDraft(comment = _note.value.text).withDictation(text).comment, failed = false)
    }

    /** Adds the note below the earlier comments; the card refreshes from the database. */
    fun saveNote() {
        val text = _note.value.text
        if (text.isBlank() || _note.value.saving) return
        _note.value = _note.value.copy(saving = true, failed = false)
        viewModelScope.launch {
            _note.value = try {
                contacts.addNote(personId, text)
                NoteState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _note.value.copy(saving = false, failed = true)
            }
        }
    }

    /**
     * Manual split (WF-35): the card belongs to someone else. Its data is read again from the stored
     * OCR text and the card moves to a new contact.
     */
    fun splitCard(card: BusinessCardEntity) {
        viewModelScope.launch {
            _splitFailed.value = false
            try {
                _movedTo.value = contacts.splitCard(card.id, contactFromCard(card))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _splitFailed.value = true
            }
        }
    }

    fun movedShown() {
        _movedTo.value = null
    }

    private fun contactFromCard(card: BusinessCardEntity): ContactDraft =
        ContactDraft.from(CardMerger.merge(extractor.extract(CardOcr.fromRawText(card.rawOcrText)))).cleaned()

    companion object {
        const val ARG_ID = "id"
    }
}
