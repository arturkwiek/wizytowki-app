package com.wizytowki.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.data.ContactSummary

/**
 * Home screen (REQUIREMENTS 8a, screen 1): the "Scan" button dominates (UI-01), an interrupted
 * scan can be resumed (UI-09), and recently added contacts open their cards.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onScan: () -> Unit,
    onResume: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val count by viewModel.contactCount.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val recent by viewModel.recent.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.home_contacts_count, count), style = MaterialTheme.typography.bodyMedium)
        draft?.let { DraftCard(draft = it, onResume = onResume, onDiscard = viewModel::discardDraft) }
        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Text(stringResource(R.string.nav_scan), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        if (recent.isNotEmpty()) {
            Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
            recent.forEach { contact -> ContactRow(contact) { onOpenContact(contact.personId) } }
        }
        OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.nav_settings))
        }
    }
}

@Composable
private fun ContactRow(contact: ContactSummary, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp).heightIn(min = 24.dp)) {
            Text(contact.name.ifBlank { stringResource(R.string.contact_no_name) }, style = MaterialTheme.typography.titleMedium)
            val line = listOf(contact.position, contact.company).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
