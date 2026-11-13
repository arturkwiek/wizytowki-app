package com.wizytowki.ui.preview

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import java.util.Locale

/** Languages of dictation (WF-23); BCP-47 tags passed to the speech recognizer. */
enum class DictationLanguage(val tag: String) { PL("pl-PL"), EN("en-US") }

/**
 * The user's comment (WF-23, UI-08): typed, or dictated with one tap on the microphone.
 * Dictation uses the system speech recognizer screen with offline preferred, so the app itself
 * needs no microphone permission (WNF-04) and nothing is sent by the app (WNF-01).
 */
@Composable
fun CommentSection(comment: String, enabled: Boolean, onEdit: (String) -> Unit, onDictated: (String) -> Unit) {
    var language by rememberSaveable {
        mutableStateOf(if (Locale.getDefault().language == "pl") DictationLanguage.PL else DictationLanguage.EN)
    }
    var unavailable by rememberSaveable { mutableStateOf(false) }
    val prompt = stringResource(R.string.preview_dictate)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onDictated)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = comment,
            onValueChange = onEdit,
            label = { Text(stringResource(R.string.preview_add_comment)) },
            enabled = enabled,
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            trailingIcon = {
                IconButton(
                    enabled = enabled,
                    onClick = {
                        unavailable = !startDictation(language) { launcher.launch(it) }
                    },
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = prompt)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.preview_dictation_language), style = MaterialTheme.typography.labelMedium)
            DictationLanguage.entries.forEach { option ->
                FilterChip(
                    selected = language == option,
                    onClick = { language = option },
                    label = { Text(option.name) },
                    enabled = enabled,
                )
            }
        }
        if (unavailable) {
            Text(stringResource(R.string.preview_dictation_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Opens the speech recognizer; false when the phone has none. */
private fun startDictation(language: DictationLanguage, launch: (Intent) -> Unit): Boolean = try {
    launch(dictationIntent(language))
    true
} catch (e: ActivityNotFoundException) {
    false
}

internal fun dictationIntent(language: DictationLanguage) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
}
