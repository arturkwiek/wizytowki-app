package com.wizytowki.ui.contact

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.R
import com.wizytowki.data.CompanyEdit
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactRepository
import com.wizytowki.extract.CardFieldRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editing a company (#115); [edit] is null until loaded, or when the company no longer exists. */
data class EditCompanyState(
    val edit: CompanyEdit? = null,
    val people: Int = 0,
    val missing: Boolean = false,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
) {
    /** A NIP that does not pass the checksum (WF-13) – shown as a warning, saving is still allowed. */
    val nipSuspicious: Boolean
        get() = edit?.nip?.filter { it.isDigit() }?.let { it.isNotEmpty() && !CardFieldRules.isValidNip(it) } == true
}

@HiltViewModel
class EditCompanyViewModel @Inject constructor(
    queries: ContactQueries,
    private val contacts: ContactRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val companyId: Long = checkNotNull(savedState.get<Long>(ARG_ID)) { "Missing $ARG_ID" }
    private val _state = MutableStateFlow(EditCompanyState())
    val state: StateFlow<EditCompanyState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val details = queries.company(companyId).first()
            val company = details?.company
            _state.update {
                it.copy(
                    edit = company?.let { c -> CompanyEdit(c.name, c.nip, c.website, c.addressPl, c.addressEn) },
                    people = details?.people?.size ?: 0,
                    missing = company == null,
                )
            }
        }
    }

    fun change(transform: (CompanyEdit) -> CompanyEdit) = _state.update { s -> s.copy(edit = s.edit?.let(transform)) }

    fun save() {
        val s = _state.value
        val edit = s.edit ?: return
        if (s.saving || s.saved) return
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val ok = try {
                contacts.editCompany(companyId, edit)
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

@Composable
fun EditCompanyScreen(viewModel: EditCompanyViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.edit_company_title), style = MaterialTheme.typography.headlineMedium)
        val edit = state.edit
        when {
            state.missing -> Text(stringResource(R.string.company_missing))
            edit == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else -> {
                if (state.people > 1) Text(stringResource(R.string.edit_company_shared, state.people), style = MaterialTheme.typography.bodySmall)
                Field(R.string.field_company, edit.name) { v -> viewModel.change { it.copy(name = v) } }
                Field(R.string.field_nip, edit.nip, KeyboardType.Number) { v -> viewModel.change { it.copy(nip = v) } }
                if (state.nipSuspicious) Text(stringResource(R.string.edit_company_bad_nip), color = MaterialTheme.colorScheme.error)
                Field(R.string.field_website, edit.website, KeyboardType.Uri) { v -> viewModel.change { it.copy(website = v) } }
                Field(R.string.company_address_pl, edit.addressPl) { v -> viewModel.change { it.copy(addressPl = v) } }
                Field(R.string.company_address_en, edit.addressEn) { v -> viewModel.change { it.copy(addressEn = v) } }
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

@Composable
private fun Field(label: Int, value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}
