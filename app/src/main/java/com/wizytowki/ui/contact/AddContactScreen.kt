package com.wizytowki.ui.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.R
import com.wizytowki.data.ContactRepository
import com.wizytowki.domain.CardMerger
import com.wizytowki.domain.ContactComparison
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.DuplicateFinder
import com.wizytowki.domain.DuplicateMatch
import com.wizytowki.domain.MatchReason
import com.wizytowki.extract.EntityExtractor
import com.wizytowki.ui.preview.CommentSection
import com.wizytowki.ui.preview.ContactForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A contact typed in by hand (#118); [duplicate] is a saved person who looks like the same one. */
data class AddContactState(
    val draft: ContactDraft = ContactDraft(),
    val duplicate: DuplicateMatch? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val savedId: Long? = null,
) {
    /** Something that identifies the contact: a name, company, phone or e-mail. */
    val canSave: Boolean
        get() = draft.cleaned().let { listOf(it.firstName, it.lastName, it.company).any(String::isNotEmpty) || it.phones.isNotEmpty() || it.emails.isNotEmpty() }
}

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val contacts: ContactRepository,
    private val extractor: EntityExtractor,
) : ViewModel() {
    private val _state = MutableStateFlow(AddContactState())
    val state: StateFlow<AddContactState> = _state.asStateFlow()

    fun edit(field: ContactField, values: List<String>) = _state.update {
        it.copy(draft = with(ContactComparison) { it.draft.with(field, values) }, duplicate = null)
    }

    fun editComment(text: String) = _state.update { it.copy(draft = it.draft.copy(comment = text)) }

    fun addDictation(text: String) = _state.update { it.copy(draft = it.draft.withDictation(text)) }

    /** Saves the contact; when the same person seems to be saved already, asks first (WF-32). */
    fun save(evenIfDuplicate: Boolean = false) {
        val s = _state.value
        if (!s.canSave || s.saving || s.savedId != null) return
        _state.update { it.copy(saving = true, failed = false) }
        val clean = s.draft.cleaned()
        val contact = clean.copy(phones = clean.phones.map { extractor.normalizePhone(it) ?: it }.distinct())
        viewModelScope.launch {
            try {
                val match = if (evenIfDuplicate) null else DuplicateFinder.find(contact, contacts.savedContacts())
                if (match != null) {
                    _state.update { it.copy(saving = false, duplicate = match) }
                } else {
                    val id = contacts.addContact(contact)
                    _state.update { it.copy(saving = false, duplicate = null, savedId = id) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}

/** New contact without a card (#118): the same form as when scanning, plus a comment. */
@Composable
fun AddContactScreen(viewModel: AddContactViewModel, onSaved: (Long) -> Unit, onOpenContact: (Long) -> Unit, onCancel: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.add_contact_title), style = MaterialTheme.typography.headlineMedium)
        ContactForm(state.draft, CardMerger.merge(emptyList()), emptySet(), enabled = !state.saving, onEdit = viewModel::edit)
        CommentSection(state.draft.comment, enabled = !state.saving, onEdit = viewModel::editComment, onDictated = viewModel::addDictation)

        state.duplicate?.let { match -> DuplicateWarning(match, onOpen = { onOpenContact(match.saved.personId) }, onSaveAnyway = { viewModel.save(evenIfDuplicate = true) }) }
        if (state.saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.failed) Text(stringResource(R.string.preview_save_failed), color = MaterialTheme.colorScheme.error)
        if (state.duplicate == null) {
            Button(onClick = { viewModel.save() }, enabled = state.canSave && !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun DuplicateWarning(match: DuplicateMatch, onOpen: () -> Unit, onSaveAnyway: () -> Unit) {
    val saved = match.saved.contact
    val name = listOf(saved.firstName, saved.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { stringResource(R.string.contact_no_name) }
    val reasons = match.reasons.sorted().map {
        stringResource(
            when (it) {
                MatchReason.EMAIL -> R.string.duplicate_reason_email
                MatchReason.PHONE -> R.string.duplicate_reason_phone
                MatchReason.NAME -> R.string.duplicate_reason_name
            },
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.add_contact_duplicate, name), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.duplicate_reasons, reasons.joinToString()), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.add_contact_open_existing))
            }
            OutlinedButton(onClick = onSaveAnyway, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.add_contact_save_anyway))
            }
        }
    }
}
