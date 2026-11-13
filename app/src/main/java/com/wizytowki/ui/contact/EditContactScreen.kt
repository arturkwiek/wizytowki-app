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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.R
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.domain.CardMerger
import com.wizytowki.domain.ContactComparison
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import com.wizytowki.extract.EntityExtractor
import com.wizytowki.ui.preview.ContactForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editing a saved contact (#115); [draft] is null until loaded, or when the person no longer exists. */
data class EditContactState(
    val draft: ContactDraft? = null,
    val missing: Boolean = false,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class EditContactViewModel @Inject constructor(
    queries: ContactQueries,
    private val contacts: ContactRepository,
    private val extractor: EntityExtractor,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val personId: Long = checkNotNull(savedState.get<Long>(ARG_ID)) { "Missing $ARG_ID" }
    private val _state = MutableStateFlow(EditContactState())
    val state: StateFlow<EditContactState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val details = queries.contact(personId).first()
            // Comments are not edited here: notes are added on the card and kept as they were written.
            _state.update { it.copy(draft = details?.contact?.copy(comment = ""), missing = details == null) }
        }
    }

    fun edit(field: ContactField, values: List<String>) = _state.update { s ->
        s.copy(draft = s.draft?.let { with(ContactComparison) { it.with(field, values) } })
    }

    fun save() {
        val s = _state.value
        val draft = s.draft ?: return
        if (s.saving || s.saved) return
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val ok = try {
                val clean = draft.cleaned()
                // Phones typed by hand are stored like recognized ones, so duplicates still match (WF-32).
                contacts.editContact(personId, clean.copy(phones = clean.phones.map { extractor.normalizePhone(it) ?: it }.distinct()))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _state.update { it.copy(saving = false, failed = !ok, saved = ok) }
        }
    }

    companion object {
        const val ARG_ID = "id"
    }
}

/** Edit screen of a saved contact: the same form as when scanning (WF-20), without the photos. */
@Composable
fun EditContactScreen(viewModel: EditContactViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.edit_contact_title), style = MaterialTheme.typography.headlineMedium)
        val draft = state.draft
        when {
            state.missing -> Text(stringResource(R.string.contact_missing))
            draft == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else -> {
                Text(stringResource(R.string.edit_contact_note), style = MaterialTheme.typography.bodySmall)
                ContactForm(draft, CardMerger.merge(emptyList()), emptySet(), enabled = !state.saving, onEdit = viewModel::edit)
                if (state.saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (state.failed) Text(stringResource(R.string.preview_save_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
