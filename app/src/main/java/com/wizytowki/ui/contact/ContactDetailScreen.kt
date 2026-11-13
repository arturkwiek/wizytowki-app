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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.wizytowki.ui.preview.CommentSection
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
    onEdit: (Long) -> Unit,
    onOpenContact: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val movedTo by viewModel.movedTo.collectAsState()
    val splitFailed by viewModel.splitFailed.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val deleteFailed by viewModel.deleteFailed.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(movedTo) {
        movedTo?.let {
            viewModel.movedShown()
            onOpenContact(it)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val s = state) {
            ContactDetailState.Loading -> Unit
            ContactDetailState.Missing -> Text(stringResource(R.string.contact_missing), style = MaterialTheme.typography.bodyLarge)
            is ContactDetailState.Ready -> {
                Button(onClick = { onEdit(s.details.personId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.edit_contact_action))
                }
                ContactCard(s.details, onOpenCompany, onSplitCard = viewModel::splitCard, onDeleteCard = viewModel::deleteCard)
                val note by viewModel.note.collectAsState()
                NoteSection(note, viewModel::editNote, viewModel::addDictation, viewModel::saveNote)
                if (splitFailed) Text(stringResource(R.string.split_failed), color = MaterialTheme.colorScheme.error)
                // Manual merge of two records of the same person (WF-35).
                OutlinedButton(onClick = { onMerge(s.details.personId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.merge_action))
                }
                if (deleteFailed) Text(stringResource(R.string.delete_failed), color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.delete_contact))
                }
                if (confirmDelete) {
                    ConfirmDelete(
                        text = stringResource(R.string.delete_contact_confirm, s.details.cards.size),
                        onConfirm = {
                            confirmDelete = false
                            viewModel.deletePerson()
                        },
                        onDismiss = { confirmDelete = false },
                    )
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
fun ContactCard(
    details: ContactDetails,
    onOpenCompany: (Long) -> Unit,
    onSplitCard: ((BusinessCardEntity) -> Unit)? = null,
    onDeleteCard: ((BusinessCardEntity) -> Unit)? = null,
) {
    var cardToDelete by remember { mutableStateOf<BusinessCardEntity?>(null) }
    cardToDelete?.let { card ->
        ConfirmDelete(
            text = stringResource(R.string.delete_card_confirm),
            onConfirm = {
                cardToDelete = null
                onDeleteCard?.invoke(card)
            },
            onDismiss = { cardToDelete = null },
        )
    }
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
        // A card can move to a new contact only if this person keeps at least one (WF-35).
        val split = onSplitCard.takeIf { details.cards.size > 1 }
        details.cards.forEach { card ->
            ScannedCard(card, split?.let { { it(card) } }, onDeleteCard?.let { { cardToDelete = card } })
        }
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
private fun ScannedCard(card: BusinessCardEntity, onSplit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardThumbnails(listOf(card.frontPhotoPath, card.backPhotoPath).filter { it.isNotBlank() }.map { "file://$it" })
            Text(card.scannedAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)), style = MaterialTheme.typography.bodyMedium)
            val where = listOf(card.event, card.location).filter { it.isNotBlank() }.joinToString(" · ")
            if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onSplit != null) {
                TextButton(onClick = onSplit, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.split_action)) }
            }
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.delete_card)) }
            }
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

/** Adding a note later, typed or dictated (WF-23, #115); saved below the earlier comments. */
@Composable
fun NoteSection(note: NoteState, onEdit: (String) -> Unit, onDictated: (String) -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.note_title), style = MaterialTheme.typography.titleMedium)
        CommentSection(note.text, enabled = !note.saving, onEdit = onEdit, onDictated = onDictated)
        if (note.failed) Text(stringResource(R.string.note_failed), color = MaterialTheme.colorScheme.error)
        FilledTonalButton(
            onClick = onSave,
            enabled = note.text.isNotBlank() && !note.saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.note_add))
        }
    }
}

/** Deleting cannot be undone, so it is confirmed (UI-02 allows asking when it is needed). */
@Composable
private fun ConfirmDelete(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.delete_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
