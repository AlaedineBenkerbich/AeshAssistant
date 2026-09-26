package fr.alaedine.aesh.presentation.schedule.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreviewUseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Stateful entry point wired to [ScheduleScannerViewModel]. Unlike other
 * `*Route` composables in this codebase, this one also owns the `CAMERA`
 * runtime permission and the captured-photo file: both are tightly coupled
 * to the Android platform and CameraX's lifecycle, with no meaningful
 * "stateless" representation.
 *
 * @param onScanned Invoked once the user confirms the review step with the
 * recognized fields; the caller is expected to navigate to the schedule
 * form pre-filled with them.
 */
@Composable
fun ScheduleScannerRoute(
    onScanned: (ParsedScheduleSlot) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleScannerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted -> hasCameraPermission = isGranted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    ScheduleScannerScreen(
        uiState = uiState,
        hasCameraPermission = hasCameraPermission,
        onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.CAMERA) },
        onPhotoCaptured = viewModel::onPhotoCaptured,
        onRetake = viewModel::onRetake,
        onErrorDismissed = viewModel::onErrorDismissed,
        onConfirm = onScanned,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScannerScreen(
    uiState: ScheduleScannerUiState,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
    onRetake: () -> Unit,
    onErrorDismissed: () -> Unit,
    onConfirm: (ParsedScheduleSlot) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.schedule_scanner_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val parsedScheduleSlot = uiState.parsedScheduleSlot
            when {
                !hasCameraPermission -> CameraPermissionRationale(onRequestPermission = onRequestPermission)
                parsedScheduleSlot != null -> ScheduleScanReview(
                    parsed = parsedScheduleSlot,
                    onConfirm = { onConfirm(parsedScheduleSlot) },
                    onRetake = onRetake,
                )
                else -> {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onImageCaptureReady = { imageCapture = it },
                    )
                    ShutterButton(
                        enabled = !uiState.isProcessing,
                        onClick = {
                            imageCapture?.let { capture ->
                                captureImage(context = context, imageCapture = capture, onCaptured = onPhotoCaptured)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(32.dp),
                    )
                    if (uiState.isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error?.let { error ->
                        ErrorBanner(
                            message = resolvedErrorMessage(error),
                            onDismiss = onErrorDismissed,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Resolves a [ScheduleScannerError] to its localized banner text. */
@Composable
private fun resolvedErrorMessage(error: ScheduleScannerError): String = when (error) {
    ScheduleScannerError.NoTextRecognized -> stringResource(R.string.schedule_scanner_no_text_recognized)
    ScheduleScannerError.RecognitionFailed -> stringResource(R.string.schedule_scanner_read_failed)
}

/**
 * Binds a CameraX preview + [ImageCapture] use case to [PreviewView], both
 * scoped to the current [LocalLifecycleOwner] so CameraX automatically
 * releases the camera when this leaves composition (e.g. navigating away,
 * or the review screen replacing this content).
 */
@Composable
private fun CameraPreview(onImageCaptureReady: (ImageCapture) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = CameraPreviewUseCase.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val capture = ImageCapture.Builder().build()
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                    )
                    onImageCaptureReady(capture)
                } catch (error: Exception) {
                    // Binding failed (e.g. no camera hardware, or the
                    // lifecycle is already destroyed): the shutter button
                    // simply stays disabled since onImageCaptureReady was
                    // never called.
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }
}

/** Captures a photo to a temporary cache file and hands it to [onCaptured] once saved. */
private fun captureImage(context: Context, imageCapture: ImageCapture, onCaptured: (File) -> Unit) {
    val photoFile = File(context.cacheDir, "schedule_scan_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onCaptured(photoFile)
            }

            override fun onError(exception: ImageCaptureException) {
                // Rare in practice (e.g. disk full, camera disconnected
                // mid-capture); the user can simply try the shutter again.
            }
        },
    )
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun CameraPermissionRationale(onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.schedule_scanner_camera_rationale),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequestPermission) {
            Text(text = stringResource(R.string.schedule_scanner_grant_access))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(text = stringResource(R.string.action_ok))
            }
        }
    }
}

@Composable
private fun ScheduleScanReview(
    parsed: ParsedScheduleSlot,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.schedule_scanner_review_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.schedule_scanner_review_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScanResultRow(label = stringResource(R.string.schedule_field_day), value = parsed.dayOfWeek?.displayName())
                ScanResultRow(
                    label = stringResource(R.string.schedule_field_start_time),
                    value = parsed.startTime?.format(TIME_FORMATTER),
                )
                ScanResultRow(
                    label = stringResource(R.string.schedule_field_end_time),
                    value = parsed.endTime?.format(TIME_FORMATTER),
                )
                ScanResultRow(label = stringResource(R.string.schedule_field_subject), value = parsed.subject)
                ScanResultRow(label = stringResource(R.string.schedule_field_room), value = parsed.room)
            }
        }
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.schedule_scanner_use_details))
        }
        OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.schedule_scanner_retake_photo))
        }
    }
}

@Composable
private fun ScanResultRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(
            text = value ?: stringResource(R.string.schedule_scanner_not_recognized),
            style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun ScheduleScanReviewPreview() {
    AeshAssistantTheme {
        ScheduleScanReview(
            parsed = ParsedScheduleSlot(
                dayOfWeek = DayOfWeek.TUESDAY,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0),
                subject = "Mathématiques",
                room = null,
            ),
            onConfirm = {},
            onRetake = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CameraPermissionRationalePreview() {
    AeshAssistantTheme {
        CameraPermissionRationale(onRequestPermission = {})
    }
}
