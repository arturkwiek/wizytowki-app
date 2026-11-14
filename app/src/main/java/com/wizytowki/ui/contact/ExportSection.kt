package com.wizytowki.ui.contact

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wizytowki.R
import com.wizytowki.data.ContactDetails
import com.wizytowki.data.ContactQueries
import com.wizytowki.data.export.ContactExport
import com.wizytowki.data.export.ExportFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Writes exported text to a file the user picked (#122). Separate from the view model for tests. */
fun interface ExportTarget {
    suspend fun write(uri: String, text: String)
}

class ContentResolverExportTarget @Inject constructor(@ApplicationContext private val context: Context) : ExportTarget {
    override suspend fun write(uri: String, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("Cannot open $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}

sealed interface ExportState {
    data object Idle : ExportState
    data object Working : ExportState
    data class Done(val count: Int) : ExportState
    data object Failed : ExportState
}

/** Export of all saved contacts to a file (#122); nothing leaves the phone unless the user picks a place. */
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val queries: ContactQueries,
    private val target: ExportTarget,
) : ViewModel() {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    fun export(format: ExportFormat, uri: String) {
        if (_state.value == ExportState.Working) return
        _state.value = ExportState.Working
        viewModelScope.launch {
            _state.value = try {
                val contacts = allDetails()
                target.write(uri, ContactExport.write(format, contacts))
                ExportState.Done(contacts.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportState.Failed
            }
        }
    }

    fun dismiss() {
        _state.value = ExportState.Idle
    }

    private suspend fun allDetails(): List<ContactDetails> =
        queries.allContacts().first().mapNotNull { queries.contact(it.personId).first() }
}

/** "Export contacts" button: format choice, the system save dialog, then the result (#122). */
@Composable
fun ExportSection(viewModel: ExportViewModel) {
    val state by viewModel.state.collectAsState()
    var choosing by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf(ExportFormat.VCARD) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let { viewModel.export(format, it.toString()) }
    }

    OutlinedButton(
        onClick = { choosing = true },
        enabled = state != ExportState.Working,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.export_action))
    }
    when (val s = state) {
        ExportState.Working -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is ExportState.Done -> Text(stringResource(R.string.export_done, s.count), color = MaterialTheme.colorScheme.primary)
        ExportState.Failed -> Text(stringResource(R.string.export_failed), color = MaterialTheme.colorScheme.error)
        ExportState.Idle -> Unit
    }

    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.export_choose)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExportFormat.entries.forEach { option ->
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .clickable(role = Role.Button) {
                                    choosing = false
                                    format = option
                                    viewModel.dismiss()
                                    saveFile.launch(ContactExport.fileName(option))
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(option.title(), style = MaterialTheme.typography.titleMedium)
                            Text(option.description(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ExportFormat.title() = stringResource(
    when (this) {
        ExportFormat.VCARD -> R.string.export_vcard
        ExportFormat.CSV -> R.string.export_csv
        ExportFormat.JSON -> R.string.export_json
    },
)

@Composable
private fun ExportFormat.description() = stringResource(
    when (this) {
        ExportFormat.VCARD -> R.string.export_vcard_info
        ExportFormat.CSV -> R.string.export_csv_info
        ExportFormat.JSON -> R.string.export_json_info
    },
)
