package com.wizytowki.ui.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.wizytowki.R
import com.wizytowki.scan.DecodedImage
import com.wizytowki.scan.ScanSession.Step
import com.wizytowki.scan.decodePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Etap 1 prototype (#7): the same front/back flow driven by ML Kit Document Scanner or by CameraX. */
@Composable
fun ScanPrototypeScreen(onBack: () -> Unit, viewModel: ScanPrototypeViewModel) {
    val state by viewModel.state.collectAsState()
    val method = state.method

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.proto_title), style = MaterialTheme.typography.headlineSmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (method == null) {
            MethodChooser(onChoose = viewModel::chooseMethod, onBack = onBack)
            return@Column
        }

        val session = state.session
        Text(
            when (session.step) {
                Step.FRONT -> stringResource(R.string.scan_front)
                Step.BACK -> stringResource(R.string.scan_back)
                Step.DONE -> stringResource(R.string.proto_done)
            },
            style = MaterialTheme.typography.titleMedium,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SideThumbnail(R.string.proto_side_front, session.frontUri, skipped = false, Modifier.weight(1f))
            SideThumbnail(R.string.proto_side_back, session.backUri, session.backSkipped, Modifier.weight(1f))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.step != Step.DONE) {
                when (method) {
                    ScannerMethod.MLKIT_DOCUMENT_SCANNER ->
                        MlKitCapture(onCaptured = viewModel::onCaptured, onError = viewModel::onError)
                    ScannerMethod.CAMERAX ->
                        CameraXCapture(
                            onCaptured = viewModel::onCaptured,
                            onError = viewModel::onError,
                            modifier = Modifier.weight(1f),
                        )
                }
            }
        }

        // Bottom actions (UI-10: important buttons at the bottom).
        if (session.step == Step.BACK) {
            OutlinedButton(onClick = viewModel::skipBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scan_skip_back))
            }
        }
        if (session.frontUri != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = viewModel::retakeFront, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.proto_retake_front))
                }
                if (session.step == Step.DONE) {
                    OutlinedButton(onClick = viewModel::retakeBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.proto_retake_back))
                    }
                }
            }
        }
        OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.proto_start_over))
        }
    }
}

@Composable
private fun MethodChooser(onChoose: (ScannerMethod) -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.proto_intro))
        MethodCard(R.string.proto_mlkit, R.string.proto_mlkit_desc) { onChoose(ScannerMethod.MLKIT_DOCUMENT_SCANNER) }
        MethodCard(R.string.proto_camerax, R.string.proto_camerax_desc) { onChoose(ScannerMethod.CAMERAX) }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Composable
private fun MethodCard(title: Int, description: Int, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(stringResource(title)) }
        }
    }
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
                    stringResource(R.string.proto_resolution, image!!.width, image!!.height),
                    style = MaterialTheme.typography.bodySmall,
                )
                skipped -> Text(stringResource(R.string.proto_back_skipped), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** ML Kit Document Scanner: Google's own UI with edge detection, crop, deskew, auto-capture and gallery import. */
@Composable
private fun MlKitCapture(onCaptured: (String) -> Unit, onError: (String) -> Unit) {
    val activity = LocalContext.current.findActivity()
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
                ?.let { onCaptured(it.toString()) }
        }
    }
    Button(
        onClick = {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
                .addOnFailureListener { onError(it.message ?: it.toString()) }
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(stringResource(R.string.proto_open_scanner))
    }
}

/** CameraX: plain in-app preview and capture; no edge detection or perspective correction. */
@Composable
private fun CameraXCapture(onCaptured: (String) -> Unit, onError: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    if (!hasPermission) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.proto_camera_permission))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.proto_grant_permission))
            }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }.onFailure { onError(it.message ?: it.toString()) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds())
        Button(
            onClick = {
                val dir = File(context.cacheDir, "scan_prototype").apply { mkdirs() }
                val file = File(dir, "camerax_${System.currentTimeMillis()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            onCaptured(Uri.fromFile(file).toString())
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError(exception.message ?: exception.toString())
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.proto_take_photo))
        }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No Activity in context")
}
