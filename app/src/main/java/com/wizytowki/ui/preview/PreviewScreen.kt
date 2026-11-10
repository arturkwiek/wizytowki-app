package com.wizytowki.ui.preview

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.extract.Entity
import com.wizytowki.extract.EntityType
import com.wizytowki.extract.Side
import com.wizytowki.ocr.Language
import com.wizytowki.ocr.OcrBlock
import com.wizytowki.ocr.OcrPage

/** Screen 4 "Podgląd" – Etap 2: recognized text of both sides with fragment languages and the analysis time. */
@Composable
fun PreviewScreen(viewModel: PreviewViewModel) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.headlineMedium)
        when (val s = state) {
            PreviewUiState.Analyzing -> {
                Text(stringResource(R.string.preview_analyzing))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            PreviewUiState.NoScan -> Text(stringResource(R.string.preview_no_scan))
            PreviewUiState.Failed -> {
                Text(stringResource(R.string.preview_ocr_failed))
                Button(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            is PreviewUiState.Ready -> {
                Text(
                    stringResource(R.string.preview_ocr_time, s.ocr.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (s.entities.isNotEmpty()) EntityList(s.entities)
                SideText(R.string.preview_side_front, s.ocr.front)
                s.ocr.back?.let { SideText(R.string.preview_side_back, it) }
            }
        }
    }
}

@Composable
private fun SideText(@StringRes label: Int, page: OcrPage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
            if (page.isEmpty) {
                Text(stringResource(R.string.preview_no_text), style = MaterialTheme.typography.bodyMedium)
            } else {
                page.blocks.filter { it.text.isNotBlank() }.forEach { block -> Fragment(block) }
            }
        }
    }
}

/** One OCR fragment with its detected language (WF-11); neutral or unknown fragments carry no tag. */
@Composable
private fun Fragment(block: OcrBlock) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (block.language?.language) {
            Language.PL -> LanguageTag("PL")
            Language.EN -> LanguageTag("EN")
            else -> Unit
        }
        SelectionContainer { Text(block.text, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun LanguageTag(code: String) {
    Text(code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** Contact data found in the text (WF-12); editing and field confidence arrive in Etap 3. */
@Composable
private fun EntityList(entities: List<Entity>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.preview_found_data), style = MaterialTheme.typography.titleMedium)
            entities.forEach { entity ->
                Column {
                    val side = stringResource(if (entity.side == Side.FRONT) R.string.preview_side_front else R.string.preview_side_back)
                    Text(
                        "${stringResource(entity.type.label())} · $side",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer { Text(entity.value, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
}

@StringRes
private fun EntityType.label() = when (this) {
    EntityType.PHONE -> R.string.field_phone
    EntityType.EMAIL -> R.string.field_email
    EntityType.WEBSITE -> R.string.field_website
    EntityType.SOCIAL -> R.string.field_social
    EntityType.ADDRESS -> R.string.field_address
}
