package com.wizytowki.ui.preview

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wizytowki.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Circumstances of meeting the contact (WF-24): the scan date, saved automatically, and optionally
 * the event and the approximate place. Location is asked for only when the user taps
 * "Add place", with the purpose explained first (WNF-04); it is read from the phone's last known
 * position, offline, and stored as coordinates only on the phone.
 */
@Composable
fun MeetingSection(
    event: String,
    location: String,
    enabled: Boolean,
    onEvent: (String) -> Unit,
    onLocation: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scannedAt = remember { LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)) }
    var problem by rememberSaveable { mutableStateOf(false) }

    fun readLocation() {
        scope.launch {
            val found = withContext(Dispatchers.IO) { lastKnownLocation(context) }
            problem = found == null
            found?.let(onLocation)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) readLocation() else problem = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.meeting_scanned_at, scannedAt), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = event,
            onValueChange = onEvent,
            label = { Text(stringResource(R.string.meeting_event)) },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (location.isEmpty()) {
            Text(stringResource(R.string.meeting_location_purpose), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                enabled = enabled,
                onClick = {
                    if (hasLocationPermission(context)) readLocation() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Place, contentDescription = null)
                Text(stringResource(R.string.meeting_add_location))
            }
            if (problem) {
                Text(stringResource(R.string.meeting_location_unavailable), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Place, contentDescription = stringResource(R.string.meeting_location))
                Text(location, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(enabled = enabled, onClick = { onLocation("") }) { Text(stringResource(R.string.meeting_remove_location)) }
            }
        }
    }
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Most recent position any provider knows, formatted; null when there is none. Works offline. */
@SuppressLint("MissingPermission") // checked by the caller
private fun lastKnownLocation(context: Context): String? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return manager.getProviders(true)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { formatCoordinates(it.latitude, it.longitude) }
}

/** "52.22970, 21.01220" – five decimals, about 1 m, dot as separator in every language. */
internal fun formatCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)
