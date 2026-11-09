package com.wizytowki.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.scan.ScanSession

/** UI-09: an interrupted scan is offered for resuming on the home screen. */
@Composable
fun DraftCard(draft: ScanSession, onResume: () -> Unit, onDiscard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.draft_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when {
                        draft.frontUri == null -> R.string.draft_back_only
                        draft.backUri != null || draft.backSkipped -> R.string.draft_both_sides
                        else -> R.string.draft_front_only
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onDiscard, Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.draft_discard))
                }
                Button(onResume, Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.draft_resume))
                }
            }
        }
    }
}
