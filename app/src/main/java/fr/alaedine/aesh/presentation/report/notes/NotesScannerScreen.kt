package fr.alaedine.aesh.presentation.report.notes

import android.Manifest
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.presentation.camera.CameraPermissionRationale
import fr.alaedine.aesh.presentation.camera.CameraPreview
import fr.alaedine.aesh.presentation.camera.ScanErrorBanner
import fr.alaedine.aesh.presentation.camera.ShutterButton
import fr.alaedine.aesh.presentation.camera.captureImage
import fr.alaedine.aesh.presentation.permission.rememberPermissionState
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import java.io.File

private const val CAPTURED_PHOTO_FILE_PREFIX = "observation_notes"

private val OverlayColor = Color.Black.copy(alpha = 0.5f)

/**
 * Stateful entry point wired to [NotesScannerViewModel]. Like
 * [fr.alaedine.aesh.presentation.schedule.scanner.ScheduleScannerRoute], it
 * also owns the `CAMERA` runtime permission, which is tied to the Android
 * platform and has no meaningful "stateless" representation.
 *
 * @param onNotesScanned Invoked with the text read from all the photos once
 * the user is done; the caller is expected to leave this screen and give
 * the text to the observation form.
 */
@Composable
fun NotesScannerRoute(
    onNotesScanned: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesScannerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.isGranted) cameraPermission.request()
    }

    LaunchedEffect(uiState.recognizedNotes) {
        val notes = uiState.recognizedNotes ?: return@LaunchedEffect
        viewModel.onRecognizedNotesConsumed()
        onNotesScanned(notes)
    }

    NotesScannerScreen(
        uiState = uiState,
        hasCameraPermission = cameraPermission.isGranted,
        onRequestPermission = cameraPermission::request,
        onPhotoCaptured = viewModel::onPhotoCaptured,
        onLastPhotoRemoved = viewModel::onLastPhotoRemoved,
        onDoneClicked = viewModel::onDoneClicked,
        onErrorDismissed = viewModel::onErrorDismissed,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScannerScreen(
    uiState: NotesScannerUiState,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
    onLastPhotoRemoved: () -> Unit,
    onDoneClicked: () -> Unit,
    onErrorDismissed: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.notes_scanner_title)) },
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
        ) {
            if (!hasCameraPermission) {
                CameraPermissionRationale(
                    message = stringResource(R.string.notes_scanner_camera_rationale),
                    grantButtonLabel = stringResource(R.string.notes_scanner_grant_access),
                    onRequestPermission = onRequestPermission,
                )
            } else {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onImageCaptureReady = { imageCapture = it },
                )
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.error?.let { error ->
                        ScanErrorBanner(message = resolvedErrorMessage(error), onDismiss = onErrorDismissed)
                    }
                    Text(
                        text = stringResource(R.string.notes_scanner_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier =
                            Modifier
                                .background(OverlayColor, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                    )
                }
                NotesScannerControls(
                    photoCount = uiState.photos.size,
                    isProcessing = uiState.isProcessing,
                    canFinish = uiState.canFinish,
                    onShutterClicked = {
                        imageCapture?.let { capture ->
                            captureImage(
                                context = context,
                                imageCapture = capture,
                                filePrefix = CAPTURED_PHOTO_FILE_PREFIX,
                                onCaptured = onPhotoCaptured,
                            )
                        }
                    },
                    onLastPhotoRemoved = onLastPhotoRemoved,
                    onDoneClicked = onDoneClicked,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                if (uiState.isProcessing) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(OverlayColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

/** Resolves a [NotesScannerError] to its localized banner text. */
@Composable
private fun resolvedErrorMessage(error: NotesScannerError): String =
    when (error) {
        NotesScannerError.NoTextRecognized -> stringResource(R.string.notes_scanner_no_text_recognized)
        NotesScannerError.RecognitionFailed -> stringResource(R.string.notes_scanner_read_failed)
    }

/** The shutter flanked by what was taken so far (and how to undo it) and the button that finishes the scan. */
@Composable
private fun NotesScannerControls(
    photoCount: Int,
    isProcessing: Boolean,
    canFinish: Boolean,
    onShutterClicked: () -> Unit,
    onLastPhotoRemoved: () -> Unit,
    onDoneClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(OverlayColor)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            if (photoCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.notes_scanner_photo_count, photoCount, photoCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
                TextButton(onClick = onLastPhotoRemoved, enabled = !isProcessing) {
                    Text(text = stringResource(R.string.notes_scanner_remove_last), color = Color.White)
                }
            }
        }
        ShutterButton(
            enabled = !isProcessing,
            contentDescription = stringResource(R.string.cd_take_photo),
            onClick = onShutterClicked,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Button(
                onClick = onDoneClicked,
                enabled = canFinish,
                // The default disabled colors are tuned for light surfaces and all but vanish on the dark control bar.
                colors =
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f),
                    ),
            ) {
                Text(text = stringResource(R.string.notes_scanner_done))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF444444)
@Composable
private fun NotesScannerControlsPreview() {
    AeshAssistantTheme {
        NotesScannerControls(
            photoCount = 2,
            isProcessing = false,
            canFinish = true,
            onShutterClicked = {},
            onLastPhotoRemoved = {},
            onDoneClicked = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesScannerPermissionRationalePreview() {
    AeshAssistantTheme {
        NotesScannerScreen(
            uiState = NotesScannerUiState(),
            hasCameraPermission = false,
            onRequestPermission = {},
            onPhotoCaptured = {},
            onLastPhotoRemoved = {},
            onDoneClicked = {},
            onErrorDismissed = {},
            onNavigateBack = {},
        )
    }
}
