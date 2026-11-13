package com.wizytowki.ui.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.wizytowki.data.CompanySummary
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.ContactSummary
import com.wizytowki.domain.fold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

enum class BrowseTab { PEOPLE, COMPANIES }

/** What the browse screen shows: the chosen tab, filtered by the search text. */
data class BrowseState(
    val tab: BrowseTab = BrowseTab.PEOPLE,
    val query: String = "",
    val people: List<ContactSummary> = emptyList(),
    val companies: List<CompanySummary> = emptyList(),
)

/**
 * The database of people and companies (REQUIREMENTS 8a, screen 1; #112): everyone in Polish
 * alphabetical order, searchable by name, company, position, phone and e-mail, ignoring case
 * and Polish letters.
 */
@HiltViewModel
class BrowseViewModel @Inject constructor(queries: ContactQueries, private val savedState: SavedStateHandle) : ViewModel() {
    private val tab = MutableStateFlow(savedState.get<String>(KEY_TAB)?.let(BrowseTab::valueOf) ?: BrowseTab.PEOPLE)
    private val query = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val collator = Collator.getInstance(Locale("pl", "PL")).apply { strength = Collator.SECONDARY }

    val state: StateFlow<BrowseState> = combine(tab, query, queries.allContacts(), queries.companies()) { tab, query, people, companies ->
        BrowseState(
            tab = tab,
            query = query,
            people = people.filter { matches(query, listOf(it.firstName, it.lastName, it.company, it.position) + it.contactData) }
                .sortedWith { a, b -> compareNames(a, b) },
            companies = companies.filter { matches(query, listOf(it.name, it.nip)) }
                .sortedWith { a, b -> collator.compare(a.name, b.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseState())

    fun selectTab(value: BrowseTab) {
        tab.value = value
        savedState[KEY_TAB] = value.name
    }

    fun search(text: String) {
        query.value = text
        savedState[KEY_QUERY] = text
    }

    private fun compareNames(a: ContactSummary, b: ContactSummary): Int {
        val byLast = collator.compare(a.lastName.ifBlank { a.firstName }, b.lastName.ifBlank { b.firstName })
        return if (byLast != 0) byLast else collator.compare(a.firstName, b.firstName)
    }

    companion object {
        private const val KEY_TAB = "tab"
        private const val KEY_QUERY = "query"

        /** Every word of [query] occurs in one of [fields]; phones also match without spaces and dashes. */
        fun matches(query: String, fields: List<String>): Boolean {
            val words = fold(query).split(' ').filter { it.isNotBlank() }
            if (words.isEmpty()) return true
            val haystack = fields.joinToString(" ") { fold(it) }
            val digits = fields.joinToString(" ") { field -> field.filter { it.isDigit() } }
            return words.all { word ->
                word in haystack || (word.any { it.isDigit() } && word.filter { it.isDigit() } in digits)
            }
        }
    }
}

@Composable
fun BrowseScreen(viewModel: BrowseViewModel, onOpenContact: (Long) -> Unit, onOpenCompany: (Long) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.browse_title), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            label = { Text(stringResource(R.string.browse_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TabRow(selectedTabIndex = state.tab.ordinal) {
            Tab(
                selected = state.tab == BrowseTab.PEOPLE,
                onClick = { viewModel.selectTab(BrowseTab.PEOPLE) },
                text = { Text(stringResource(R.string.browse_people, state.people.size)) },
            )
            Tab(
                selected = state.tab == BrowseTab.COMPANIES,
                onClick = { viewModel.selectTab(BrowseTab.COMPANIES) },
                text = { Text(stringResource(R.string.browse_companies, state.companies.size)) },
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state.tab) {
                BrowseTab.PEOPLE -> {
                    if (state.people.isEmpty()) item { Text(stringResource(R.string.browse_empty)) }
                    items(state.people, key = { it.personId }) { person ->
                        Row(
                            title = person.name.ifBlank { stringResource(R.string.contact_no_name) },
                            subtitle = listOf(person.position, person.company).filter { it.isNotBlank() }.joinToString(" · "),
                        ) { onOpenContact(person.personId) }
                    }
                }
                BrowseTab.COMPANIES -> {
                    if (state.companies.isEmpty()) item { Text(stringResource(R.string.browse_empty)) }
                    items(state.companies, key = { it.companyId }) { company ->
                        Row(
                            title = company.name.ifBlank { stringResource(R.string.company_no_name) },
                            subtitle = stringResource(R.string.browse_company_people, company.people),
                        ) { onOpenCompany(company.companyId) }
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun Row(title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp).heightIn(min = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
