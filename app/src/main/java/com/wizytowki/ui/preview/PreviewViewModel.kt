package com.wizytowki.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.ScanRecord
import com.wizytowki.domain.CardMerger
import com.wizytowki.domain.Choice
import com.wizytowki.domain.ContactComparison
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.DuplicateFinder
import com.wizytowki.domain.DuplicateMatch
import com.wizytowki.domain.FieldDiff
import com.wizytowki.scan.ScanSession
import com.wizytowki.domain.MergedCard
import com.wizytowki.extract.Entity
import com.wizytowki.extract.EntityExtractor
import com.wizytowki.ocr.CardOcr
import com.wizytowki.ocr.CardRecognizer
import com.wizytowki.scan.DraftStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PreviewUiState {
    data object Analyzing : PreviewUiState

    /** No finished scan to analyze (e.g. the draft was discarded meanwhile). */
    data object NoScan : PreviewUiState

    data object Failed : PreviewUiState

    /** [card] is [entities] of both sides merged into one record (WF-14, WF-15). */
    data class Ready(
        val ocr: CardOcr,
        val entities: List<Entity> = emptyList(),
        val card: MergedCard = CardMerger.merge(entities),
        val save: SaveState = SaveState.IDLE,
        val review: DuplicateReview? = null,
    ) : PreviewUiState
}

enum class SaveState { IDLE, SAVING, SAVED, UPDATED, FAILED }

/**
 * The scanned person is already saved (WF-32): the saved and new values side by side with the
 * user's choice per field (WF-15, #98). Fields without a choice take [FieldDiff.default].
 */
data class DuplicateReview(
    val match: DuplicateMatch,
    val new: ContactDraft,
    val choices: Map<ContactField, Choice> = emptyMap(),
) {
    val diffs: List<FieldDiff> get() = ContactComparison.diff(match.saved.contact, new)

    fun choice(diff: FieldDiff): Choice = choices[diff.field] ?: diff.default

    val result: ContactDraft get() = ContactComparison.resolve(match.saved.contact, new, choices)
}

/**
 * Preview screen (REQUIREMENTS 8a, screen 4): analyzes the finished scan from the draft.
 * Etap 2 shows the recognized text; field editing arrives in Etap 3 (WF-20).
 */
@HiltViewModel
class PreviewViewModel @Inject constructor(
    private val draftStore: DraftStore,
    private val cardRecognizer: CardRecognizer,
    private val entityExtractor: EntityExtractor,
    private val contacts: ContactRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<PreviewUiState>(PreviewUiState.Analyzing)
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()
    private var session: ScanSession? = null

    init {
        analyze()
    }

    fun retry() = analyze()

    /**
     * Saves the recognized card (#98). When the person is already saved, shows the comparison
     * instead ([PreviewUiState.Ready.review]); otherwise saves a new contact and ends the scan.
     */
    fun save() = persist { ready, scan ->
        val new = ContactDraft.from(ready.card)
        val match = DuplicateFinder.find(new, contacts.savedContacts())
        if (match != null) {
            ready.copy(save = SaveState.IDLE, review = DuplicateReview(match, new))
        } else {
            contacts.saveNew(new, scan)
            finished(ready, SaveState.SAVED)
        }
    }

    /** Choice for one field of the comparison. */
    fun choose(field: ContactField, choice: Choice) = updateReview { it.copy(choices = it.choices + (field to choice)) }

    /** "Keep saved" / "Take new" for the whole entry. */
    fun chooseAll(choice: Choice) = updateReview {
        it.copy(choices = ContactComparison.all(it.match.saved.contact, it.new, choice))
    }

    /** Updates the saved person with the chosen values and adds this card to them. */
    fun confirmUpdate() = persist { ready, scan ->
        val review = ready.review ?: return@persist ready
        contacts.update(review.match.saved, review.result, scan)
        finished(ready, SaveState.UPDATED)
    }

    /** Not the same person after all: saves the scan as a separate contact. */
    fun saveAsNew() = persist { ready, scan ->
        val review = ready.review ?: return@persist ready
        contacts.saveNew(review.new, scan)
        finished(ready, SaveState.SAVED)
    }

    fun cancelReview() = updateReview { null }

    private fun updateReview(change: (DuplicateReview) -> DuplicateReview?) {
        val ready = _state.value as? PreviewUiState.Ready ?: return
        val review = ready.review ?: return
        _state.value = ready.copy(review = change(review))
    }

    private suspend fun finished(ready: PreviewUiState.Ready, result: SaveState): PreviewUiState.Ready {
        draftStore.clear()
        return ready.copy(save = result, review = null)
    }

    private fun persist(action: suspend (PreviewUiState.Ready, ScanRecord) -> PreviewUiState.Ready) {
        val ready = _state.value as? PreviewUiState.Ready ?: return
        val front = session?.frontUri ?: return
        if (ready.save in setOf(SaveState.SAVING, SaveState.SAVED, SaveState.UPDATED)) return
        _state.value = ready.copy(save = SaveState.SAVING)
        viewModelScope.launch {
            _state.value = try {
                action(ready, ScanRecord(front, session?.backUri, ready.ocr.rawText))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ready.copy(save = SaveState.FAILED)
            }
        }
    }

    private fun analyze() {
        _state.value = PreviewUiState.Analyzing
        viewModelScope.launch {
            val session = draftStore.draft.value ?: draftStore.load()
            this@PreviewViewModel.session = session
            val front = session?.frontUri
            if (front == null) {
                _state.value = PreviewUiState.NoScan
                return@launch
            }
            _state.value = try {
                val ocr = cardRecognizer.recognize(front, session.backUri)
                PreviewUiState.Ready(ocr, entityExtractor.extract(ocr))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PreviewUiState.Failed
            }
        }
    }
}
