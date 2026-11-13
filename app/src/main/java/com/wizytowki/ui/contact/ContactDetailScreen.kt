package com.wizytowki.ui.contact

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.JobHistoryItem
import com.wizytowki.data.db.entities.BusinessCardEntity
import com.wizytowki.domain.ContactComparison
import com.wizytowki.domain.ContactField
import com.wizytowki.ui.preview.CardThumbnails
import com.wizytowki.ui.preview.fieldLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Person card (REQUIREMENTS 8a, screen 7): contact data, current company and position, comments,
 * the scanned cards and quick actions – call, e-mail, map. Tapping a phone, e-mail, website or
 * address opens it as well.
 */
@Composable
fun ContactDetailScreen(
    viewModel: ContactDetailViewModel,
    onOpenCompany: (Long) -> Unit,
    onMerge: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            ContactDetailState.Loading -> Unit
            ContactDetailState.Missing -> Text(stringResource(R.string.contact_missing), style = MaterialTheme.typography.bodyLarge)
            is ContactDetailState.Ready -> {
                ContactCard(s.details, onOpenCompany)
                // Manual merge of two records of the same person (WF-35).
                OutlinedButton(onClick = { onMerge(s.details.personId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.merge_action))
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
fun ContactCard(details: ContactDetails, onOpenCompany: (Long) -> Unit) {
    val context = LocalContext.current
    val contact = details.contact
    val open: (Intent) -> Unit = { QuickActions.open(context, it) }
    val address = contact.addressPl.ifBlank { contact.addressEn }

    Text(
        listOf(contact.firstName, contact.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { stringResource(R.string.contact_no_name) },
        style = MaterialTheme.typography.headlineMedium,
    )
    val position = contact.positionPl.ifBlank { contact.positionEn }
    if (position.isNotBlank()) Text(position, style = MaterialTheme.typography.titleMedium)
    if (contact.company.isNotBlank()) {
        val companyId = details.companyId
        Text(
            contact.company,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = if (companyId != null) Modifier.clickable(role = Role.Button) { onOpenCompany(companyId) } else Modifier,
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ActionButton(Icons.Filled.Call, R.string.contact_call, contact.phones.firstOrNull()) { open(QuickActions.call(it)) }
        ActionButton(Icons.Filled.Email, R.string.contact_email, contact.emails.firstOrNull()) { open(QuickActions.email(it)) }
        ActionButton(Icons.Filled.Place, R.string.contact_map, address.ifBlank { null }) { open(QuickActions.map(it)) }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SHOWN_FIELDS.forEach { field ->
                ContactComparison.values(contact, field).forEach { value ->
                    val action = when (field) {
                        ContactField.PHONES -> QuickActions.call(value)
                        ContactField.EMAILS -> QuickActions.email(value)
                        ContactField.WEBSITES, ContactField.SOCIAL -> QuickActions.web(value)
                        ContactField.ADDRESS_PL, ContactField.ADDRESS_EN -> QuickActions.map(value)
                        else -> null
                    }
                    InfoRow(fieldLabel(field), value, action?.let { { open(it) } })
                }
            }
        }
    }

    if (contact.comment.isNotBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.contact_comment), style = MaterialTheme.typography.labelLarge)
                Text(contact.comment, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (details.jobs.any { !it.current }) {
        Text(stringResource(R.string.contact_history), style = MaterialTheme.typography.titleMedium)
        details.jobs.forEach { job -> JobRow(job) { onOpenCompany(job.companyId) } }
    }

    if (details.cards.isNotEmpty()) {
        Text(stringResource(R.string.contact_cards, details.cards.size), style = MaterialTheme.typography.titleMedium)
        details.cards.forEach { card -> ScannedCard(card) }
    }
}

/** One job of the history (WF-34): position, company and dates. */
@Composable
private fun JobRow(job: JobHistoryItem, onOpenCompany: () -> Unit) {
    val month = DateTimeFormatter.ofPattern("MM.yyyy")
    val from = job.dateFrom?.format(month).orEmpty()
    val to = if (job.current) stringResource(R.string.contact_job_now) else job.dateTo?.format(month).orEmpty()
    Card(modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpenCompany)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                listOf(job.position, job.company).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { stringResource(R.string.company_no_name) },
                style = MaterialTheme.typography.titleSmall,
            )
            if (job.department.isNotBlank()) Text(job.department, style = MaterialTheme.typography.bodyMedium)
            Text("$from – $to", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One scanned card of the person (WF-34): photos, date, event and place. */
@Composable
private fun ScannedCard(card: BusinessCardEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardThumbnails(listOf(card.frontPhotoPath, card.backPhotoPath).filter { it.isNotBlank() }.map { "file://$it" })
            Text(card.scannedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)), style = MaterialTheme.typography.bodyMedium)
            val where = listOf(card.event, card.location).filter { it.isNotBlank() }.joinToString(" · ")
            if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionButton(
    icon: ImageVector,
    label: Int,
    value: String?,
    onClick: (String) -> Unit,
) {
    FilledTonalButton(
        onClick = { value?.let(onClick) },
        enabled = value != null,
        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Fields listed on the card; name, company and position are in the header. */
private val SHOWN_FIELDS = listOf(
    ContactField.PHONES, ContactField.EMAILS, ContactField.WEBSITES, ContactField.SOCIAL,
    ContactField.POSITION_EN, ContactField.DEPARTMENT_PL, ContactField.DEPARTMENT_EN,
    ContactField.ADDRESS_PL, ContactField.ADDRESS_EN, ContactField.NIP,
)
