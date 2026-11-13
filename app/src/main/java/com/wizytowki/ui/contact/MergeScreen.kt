package com.wizytowki.ui.contact

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.R
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.data.ContactSummary
import com.wizytowki.domain.Choice
import com.wizytowki.domain.ContactComparison
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.FieldDiff
import com.wizytowki.domain.SavedContact
import com.wizytowki.ui.preview.FieldChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Manual merge (WF-35): pick the other person, then choose the data of the merged contact. */
data class MergeState(
    val keep: ContactDetails? = null,
    val candidates: List<ContactSummary> = emptyList(),
    val other: ContactDetails? = null,
    val choices: Map<ContactField, Choice> = emptyMap(),
    val busy: Boolean = false,
    val failed: Boolean = false,
    val done: Boolean = false,
) {
    val diffs: List<FieldDiff>
        get() = if (keep != null && other != null) ContactComparison.diff(keep.contact, other.contact) else emptyList()

    fun choice(diff: FieldDiff): Choice = choices[diff.field] ?: mergeDefault(diff)

    val result: ContactDraft?
        get() = if (keep != null && other != null) {
            ContactComparison.resolve(keep.contact, other.contact, diffs.associate { it.field to choice(it) })
        } else {
            null
        }

    companion object {
        /** Neither person is "newer": keep this person's value, lists of both, fill what is missing. */
        fun mergeDefault(diff: FieldDiff): Choice = when {
            diff.saved.isEmpty() -> Choice.NEW
            diff.field.multi && diff.new.isNotEmpty() && !diff.same -> Choice.BOTH
            else -> Choice.SAVED
        }
    }
}

@HiltViewModel
class MergeViewModel @Inject constructor(
    private val queries: ContactQueries,
    private val contacts: ContactRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val keepId: Long = checkNotNull(savedState.get<Long>(ARG_ID)) { "Missing $ARG_ID" }
    private val _state = MutableStateFlow(MergeState())
    val state: StateFlow<MergeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val keep = queries.contact(keepId).first()
            val candidates = queries.recentContacts(limit = ALL).first().filter { it.personId != keepId }
                .sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }))
            _state.update { it.copy(keep = keep, candidates = candidates) }
        }
    }

    fun select(personId: Long) {
        viewModelScope.launch {
            val other = queries.contact(personId).first()
            _state.update { it.copy(other = other, choices = emptyMap()) }
        }
    }

    fun back() = _state.update { it.copy(other = null, choices = emptyMap()) }

    fun choose(field: ContactField, choice: Choice) = _state.update { it.copy(choices = it.choices + (field to choice)) }

    fun merge() {
        val s = _state.value
        val keep = s.keep ?: return
        val other = s.other ?: return
        val result = s.result ?: return
        if (s.busy || s.done) return
        _state.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val ok = try {
                contacts.mergePeople(SavedContact(keep.personId, keep.companyId, keep.contact), other.personId, result)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _state.update { it.copy(busy = false, failed = !ok, done = ok) }
        }
    }

    companion object {
        const val ARG_ID = "id"
        private const val ALL = 10_000
    }
}

/** Merge screen for the person [MergeViewModel] was opened for; [onDone] returns to their card. */
@Composable
fun MergeScreen(viewModel: MergeViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.merge_title), style = MaterialTheme.typography.headlineMedium)
        val keep = state.keep
        val other = state.other
        if (keep != null) Text(stringResource(R.string.merge_keep, nameOf(keep.contact)), style = MaterialTheme.typography.bodyLarge)
        if (other == null) {
            Text(stringResource(R.string.merge_pick), style = MaterialTheme.typography.titleMedium)
            if (state.candidates.isEmpty()) Text(stringResource(R.string.merge_no_candidates))
            state.candidates.forEach { candidate ->
                Card(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { viewModel.select(candidate.personId) }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(candidate.name.ifBlank { stringResource(R.string.contact_no_name) }, style = MaterialTheme.typography.titleMedium)
                        val line = listOf(candidate.position, candidate.company).filter { it.isNotBlank() }.joinToString(" · ")
                        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        } else {
            Text(stringResource(R.string.merge_with, nameOf(other.contact)), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.merge_explain, other.cards.size), style = MaterialTheme.typography.bodySmall)
            state.diffs.forEach { diff ->
                FieldChoice(diff, state.choice(diff), R.string.merge_this_value, R.string.merge_other_value) { viewModel.choose(diff.field, it) }
            }
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (state.failed) Text(stringResource(R.string.merge_failed), color = MaterialTheme.colorScheme.error)
            Button(onClick = viewModel::merge, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.merge_confirm))
            }
            OutlinedButton(onClick = viewModel::back, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.merge_other_person))
            }
        }
    }
}

@Composable
private fun nameOf(contact: ContactDraft): String =
    listOf(contact.firstName, contact.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { stringResource(R.string.contact_no_name) }
