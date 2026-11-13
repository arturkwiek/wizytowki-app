package com.wizytowki.ui.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.domain.ContactComparison
import com.wizytowki.domain.ContactDraft
import com.wizytowki.domain.ContactField
import com.wizytowki.domain.MergedCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The recognized contact as an editable form (WF-20): every field can be corrected before saving,
 * both language versions are shown (WF-21), uncertain values are marked with an icon and text
 * (WF-22, UI-06) and a different value read on the other side is shown under the field (WF-15).
 */
@Composable
fun ContactForm(
    draft: ContactDraft,
    card: MergedCard,
    lowConfidence: Set<ContactField>,
    enabled: Boolean,
    onEdit: (ContactField, List<String>) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.preview_found_data), style = MaterialTheme.typography.titleMedium)
            ContactField.entries.forEach { field ->
                val values = ContactComparison.values(draft, field)
                if (field.multi) {
                    MultiField(field, values, field in lowConfidence, enabled) { onEdit(field, it) }
                } else {
                    SingleField(field, values.firstOrNull().orEmpty(), field in lowConfidence, enabled) { onEdit(field, listOf(it)) }
                }
                ConflictNote(field, values, card)
            }
        }
    }
}

@Composable
private fun SingleField(field: ContactField, value: String, low: Boolean, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(fieldLabel(field)) },
        enabled = enabled,
        singleLine = field !in MULTILINE,
        supportingText = lowConfidenceSupport(low),
        keyboardOptions = keyboard(field),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun lowConfidenceSupport(low: Boolean): (@Composable () -> Unit)? =
    if (low) { { LowConfidenceNote() } } else null

/** One text field per value, plus "Add" for another phone, e-mail, website or profile. */
@Composable
private fun MultiField(field: ContactField, values: List<String>, low: Boolean, enabled: Boolean, onChange: (List<String>) -> Unit) {
    val shown = values.ifEmpty { listOf("") }
    shown.forEachIndexed { i, value ->
        OutlinedTextField(
            value = value,
            onValueChange = { new -> onChange(shown.toMutableList().also { it[i] = new }) },
            label = { Text(fieldLabel(field)) },
            enabled = enabled,
            singleLine = true,
            supportingText = lowConfidenceSupport(low && i == 0),
            keyboardOptions = keyboard(field),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (enabled && shown.last().isNotBlank()) {
        TextButton(onClick = { onChange(shown + "") }) {
            Text(stringResource(R.string.preview_add_value, fieldLabel(field)))
        }
    }
}

/** A different value read on the other side of the card (WF-15). */
@Composable
private fun ConflictNote(field: ContactField, values: List<String>, card: MergedCard) {
    val others = card.all(field.type)
        .filter { it.conflict && it.value in values }
        .flatMap { merged -> merged.alternatives.filter { it.side !in merged.sides }.map { it.value } }
        .distinct()
    if (others.isNotEmpty()) {
        Text(
            stringResource(R.string.preview_conflict, others.joinToString(" / ")),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Low-confidence mark (WF-22): icon and text, not color alone (UI-06). */
@Composable
internal fun LowConfidenceNote() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.preview_low_confidence), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
    }
}

private val MULTILINE = setOf(ContactField.ADDRESS_PL, ContactField.ADDRESS_EN)

private fun keyboard(field: ContactField) = when (field) {
    ContactField.PHONES -> KeyboardOptions(keyboardType = KeyboardType.Phone)
    ContactField.EMAILS -> KeyboardOptions(keyboardType = KeyboardType.Email)
    ContactField.WEBSITES, ContactField.SOCIAL -> KeyboardOptions(keyboardType = KeyboardType.Uri)
    ContactField.NIP -> KeyboardOptions(keyboardType = KeyboardType.Number)
    else -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
}

/** Thumbnails of the scanned sides (WF-20), decoded small off the main thread. */
@Composable
fun CardThumbnails(photos: List<String>) {
    if (photos.isEmpty()) return
    // Tapping a thumbnail shows the photo on the full screen (#118).
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    opened?.let { PhotoViewer(it) { opened = null } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        photos.forEachIndexed { i, uri ->
            val bitmap by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { decodeThumbnail(uri) } }
            Card(
                onClick = { opened = uri },
                modifier = Modifier.weight(1f).height(120.dp),
            ) {
                bitmap?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = stringResource(if (i == 0) R.string.preview_side_front else R.string.preview_side_back),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }
            }
        }
    }
}

internal fun decodeThumbnail(uri: String, target: Int = 480): Bitmap? = runCatching {
    val path = Uri.parse(uri).path ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()
