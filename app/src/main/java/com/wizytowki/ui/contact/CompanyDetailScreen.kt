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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.data.CompanyDetails
import com.wizytowki.data.ContactQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface CompanyDetailState {
    data object Loading : CompanyDetailState
    data object Missing : CompanyDetailState
    data class Ready(val details: CompanyDetails) : CompanyDetailState
}

/** Company card (REQUIREMENTS 8a, screen 8), kept up to date with the database. */
@HiltViewModel
class CompanyDetailViewModel @Inject constructor(queries: ContactQueries, savedState: SavedStateHandle) : ViewModel() {
    private val companyId: Long = checkNotNull(savedState.get<Long>(ARG_ID)) { "Missing $ARG_ID" }

    val state: StateFlow<CompanyDetailState> = queries.company(companyId)
        .map { details -> details?.let { CompanyDetailState.Ready(it) } ?: CompanyDetailState.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompanyDetailState.Loading)

    companion object {
        const val ARG_ID = "id"
    }
}

/** Company card: name, NIP, website, address and the people linked to it (WF-31). */
@Composable
fun CompanyDetailScreen(viewModel: CompanyDetailViewModel, onOpenContact: (Long) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            CompanyDetailState.Loading -> Unit
            CompanyDetailState.Missing -> Text(stringResource(R.string.company_missing), style = MaterialTheme.typography.bodyLarge)
            is CompanyDetailState.Ready -> CompanyCard(s.details, onOpenContact)
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
fun CompanyCard(details: CompanyDetails, onOpenContact: (Long) -> Unit) {
    val context = LocalContext.current
    val company = details.company
    Text(company.name.ifBlank { stringResource(R.string.company_no_name) }, style = MaterialTheme.typography.headlineMedium)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompanyRow(R.string.field_nip, company.nip)
            CompanyRow(R.string.field_website, company.website) { QuickActions.open(context, QuickActions.web(company.website)) }
            CompanyRow(R.string.company_address_pl, company.addressPl) { QuickActions.open(context, QuickActions.map(company.addressPl)) }
            CompanyRow(R.string.company_address_en, company.addressEn) { QuickActions.open(context, QuickActions.map(company.addressEn)) }
        }
    }
    Text(stringResource(R.string.company_people, details.people.size), style = MaterialTheme.typography.titleMedium)
    details.people.forEach { person ->
        Card(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenContact(person.personId) }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(person.name.ifBlank { stringResource(R.string.contact_no_name) }, style = MaterialTheme.typography.titleMedium)
                if (person.position.isNotBlank()) {
                    Text(person.position, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompanyRow(label: Int, value: String, onClick: (() -> Unit)? = null) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}
