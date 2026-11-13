package com.wizytowki.ui.preview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.domain.Choice
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.FieldDiff
import com.wizytowki.domain.MatchReason

/**
 * Duplicate screen (REQUIREMENTS 8a, screen 6; WF-15, WF-32, #98): the saved contact and the new
 * scan side by side. The user keeps one entry as a whole or picks each differing field.
 */
@Composable
fun DuplicateReviewSection(review: DuplicateReview, save: SaveState, viewModel: PreviewViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.duplicate_title), style = MaterialTheme.typography.titleLarge)
        val reasons = review.match.reasons.sorted().map { stringResource(it.label()) }
        Text(
            stringResource(R.string.duplicate_reasons, reasons.joinToString()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.chooseAll(Choice.SAVED) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.duplicate_keep_saved))
            }
            OutlinedButton(onClick = { viewModel.chooseAll(Choice.NEW) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.duplicate_take_new))
            }
        }
        review.diffs.forEach { diff -> FieldChoice(diff, review.choice(diff)) { viewModel.choose(diff.field, it) } }

        if (save == SaveState.SAVING) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (save == SaveState.FAILED) Text(stringResource(R.string.preview_save_failed), color = MaterialTheme.colorScheme.error)
        Button(onClick = viewModel::confirmUpdate, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.duplicate_save_choice))
        }
        OutlinedButton(onClick = viewModel::saveAsNew, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.duplicate_create_new))
        }
        TextButton(onClick = viewModel::cancelReview, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun FieldChoice(diff: FieldDiff, selected: Choice, onChoose: (Choice) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp).selectableGroup()) {
            Text(fieldLabel(diff.field), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (diff.same) {
                Text(diff.values(selected).joinToString(", "), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.duplicate_unchanged), style = MaterialTheme.typography.bodySmall)
            } else {
                Option(R.string.duplicate_saved_value, diff.saved, selected == Choice.SAVED) { onChoose(Choice.SAVED) }
                Option(R.string.duplicate_new_value, diff.new, selected == Choice.NEW) { onChoose(Choice.NEW) }
                if (diff.field.multi && diff.saved.isNotEmpty() && diff.new.isNotEmpty()) {
                    Option(R.string.duplicate_both, diff.saved + diff.new, selected == Choice.BOTH) { onChoose(Choice.BOTH) }
                }
            }
        }
    }
}

@Composable
private fun Option(@StringRes label: Int, values: List<String>, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            Text(values.ifEmpty { listOf("—") }.distinct().joinToString(", "), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun FieldDiff.values(choice: Choice) = when (choice) {
    Choice.SAVED -> saved
    Choice.NEW -> new
    Choice.BOTH -> (saved + new).distinct()
}

@Composable
internal fun fieldLabel(field: ContactField): String {
    val name = stringResource(
        when (field) {
            ContactField.FIRST_NAME -> R.string.field_first_name
            ContactField.LAST_NAME -> R.string.field_last_name
            ContactField.COMPANY -> R.string.field_company
            ContactField.POSITION_PL, ContactField.POSITION_EN -> R.string.field_position
            ContactField.DEPARTMENT_PL, ContactField.DEPARTMENT_EN -> R.string.field_department
            ContactField.PHONES -> R.string.field_phone
            ContactField.EMAILS -> R.string.field_email
            ContactField.WEBSITES -> R.string.field_website
            ContactField.SOCIAL -> R.string.field_social
            ContactField.ADDRESS_PL, ContactField.ADDRESS_EN -> R.string.field_address
            ContactField.NIP -> R.string.field_nip
        },
    )
    return when {
        field.name.endsWith("_PL") -> "$name · PL"
        field.name.endsWith("_EN") -> "$name · EN"
        else -> name
    }
}

@StringRes
private fun MatchReason.label() = when (this) {
    MatchReason.EMAIL -> R.string.duplicate_reason_email
    MatchReason.PHONE -> R.string.duplicate_reason_phone
    MatchReason.NAME -> R.string.duplicate_reason_name
}
