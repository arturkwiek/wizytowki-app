package com.wizytowki.ui.scan

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wizytowki.R
import com.wizytowki.scan.DecodedImage
import com.wizytowki.scan.QualityIssue
import com.wizytowki.scan.ScanSession.Step
import com.wizytowki.scan.decodePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ButtonMinHeight = 56.dp

/**
 * Screens 2–3 of REQUIREMENTS 8a: front, then back or "Pomiń – tył pusty" (WF-01, WF-02, UI-03).
 * The front scanner opens immediately; main actions sit at the bottom (UI-10).
 */
@Composable
fun ScanScreen(viewModel: ScanViewModel, onCancel: () -> Unit, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val session = state.session
    val openScanner = rememberDocumentScanner(
        onScanned = viewModel::onScanned,
        onUnavailable = viewModel::onScannerUnavailable,
    )

    LaunchedEffect(Unit) {
        if (!state.frontAutoLaunched && session.step == Step.FRONT) {
            viewModel.onFrontAutoLaunched()
            openScanner()
        }
    }
    state.qualityWarning?.let { warning ->
        QualityWarningDialog(
            warning = warning,
            onRetake = {
                viewModel.retakeAfterWarning()
                openScanner()
            },
            onAccept = viewModel::acceptQualityWarning,
        )
    }
    LaunchedEffect(state.navigateToPreview) {
        if (state.navigateToPreview) {
            viewModel.onNavigatedToPreview()
            onDone()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = when (session.step) {
                Step.FRONT -> stringResource(R.string.scan_front)
                Step.BACK -> stringResource(R.string.scan_back)
                Step.DONE -> stringResource(R.string.scan_both_ready)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.scannerUnavailable) {
            Text(stringResource(R.string.scan_unavailable), color = MaterialTheme.colorScheme.error)
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.scan_save_failed), color = MaterialTheme.colorScheme.error)
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.checkingQuality) {
            Text(stringResource(R.string.scan_checking_quality), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (session.frontUri != null || session.backUri != null || session.backSkipped) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SideThumbnail(R.string.scan_side_front, session.frontUri, skipped = false, Modifier.weight(1f))
                SideThumbnail(R.string.scan_side_back, session.backUri, session.backSkipped, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.weight(1f))

        // WF-06: gallery import lives inside the ML Kit scanner; tell the user where to find it.
        if (session.step != Step.DONE) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Text(stringResource(R.string.scan_gallery_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }

        when (session.step) {
            Step.FRONT -> {
                PrimaryButton(R.string.scan_take_front, openScanner)
                SecondaryButton(R.string.action_cancel, onCancel)
            }
            Step.BACK -> {
                PrimaryButton(R.string.scan_take_back, openScanner)
                SecondaryButton(R.string.scan_skip_back, viewModel::skipBack)
                TertiaryButton(R.string.scan_retake_front, viewModel::retakeFront)
            }
            Step.DONE -> {
                PrimaryButton(R.string.action_next, onDone)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(viewModel::retakeFront, Modifier.weight(1f).heightIn(min = ButtonMinHeight)) {
                        Text(stringResource(R.string.scan_retake_front))
                    }
                    OutlinedButton(viewModel::retakeBack, Modifier.weight(1f).heightIn(min = ButtonMinHeight)) {
                        Text(stringResource(R.string.scan_retake_back))
                    }
                }
            }
        }
    }
}

/** WF-04: asks only when the photo looks unreadable (UI-02); keeping it is one tap. */
@Composable
private fun QualityWarningDialog(warning: QualityWarning, onRetake: () -> Unit, onAccept: () -> Unit) {
    val side = stringResource(if (warning.side == CardSide.FRONT) R.string.scan_side_front else R.string.scan_side_back)
    AlertDialog(
        onDismissRequest = onAccept,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.quality_title, side)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                warning.issues.sortedBy { it.ordinal }.forEach { issue ->
                    Text(
                        stringResource(
                            when (issue) {
                                QualityIssue.BLURRY -> R.string.quality_blurry
                                QualityIssue.TOO_DARK -> R.string.quality_too_dark
                                QualityIssue.TOO_BRIGHT -> R.string.quality_too_bright
                            }
                        )
                    )
                }
            }
        },
        confirmButton = { Button(onRetake) { Text(stringResource(R.string.scan_retake)) } },
        dismissButton = { TextButton(onAccept) { Text(stringResource(R.string.quality_use_anyway)) } },
    )
}

@Composable
private fun PrimaryButton(label: Int, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)) { Text(stringResource(label)) }
}

@Composable
private fun SecondaryButton(label: Int, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)) { Text(stringResource(label)) }
}

@Composable
private fun TertiaryButton(label: Int, onClick: () -> Unit) {
    TextButton(onClick, Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)) { Text(stringResource(label)) }
}

@Composable
private fun SideThumbnail(label: Int, uri: String?, skipped: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val image by produceState<DecodedImage?>(initialValue = null, uri) {
        value = uri?.let { withContext(Dispatchers.IO) { decodePreview(context, Uri.parse(it)) } }
    }
    Card(modifier = modifier) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
            Box(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
                image?.let {
                    Image(
                        bitmap = it.bitmap.asImageBitmap(),
                        contentDescription = stringResource(label),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            when {
                image != null -> Text(
                    stringResource(R.string.scan_resolution, image!!.width, image!!.height),
                    style = MaterialTheme.typography.bodySmall,
                )
                skipped -> Text(stringResource(R.string.scan_back_skipped), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
