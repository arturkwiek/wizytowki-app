package com.wizytowki.ui.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * ML Kit Document Scanner (decision in PR #82): edge detection, crop, deskew, auto-capture and
 * gallery import (WF-03, WF-06, UI-04). Runs inside Google Play services; no CAMERA permission needed.
 *
 * Returns a function that opens the scanner for one page. Cancelling the scanner reports nothing.
 */
@Composable
fun rememberDocumentScanner(onScanned: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val activity = LocalContext.current.findActivity()
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setPageLimit(1)
                .setGalleryImportAllowed(true)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
                ?.let { currentOnScanned(it.toString()) }
        }
    }
    return remember(activity, scanner, launcher) {
        {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { currentOnUnavailable() }
        }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No Activity in context")
}
