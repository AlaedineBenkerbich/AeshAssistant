package fr.alaedine.aesh.presentation.report.notes

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.presentation.camera.CameraPermissionState
import fr.alaedine.aesh.presentation.camera.CameraScaffold
import fr.alaedine.aesh.presentation.camera.CameraViewfinder
import fr.alaedine.aesh.presentation.camera.ScanErrorMessage
import fr.alaedine.aesh.presentation.camera.ShutterButton
import fr.alaedine.aesh.presentation.camera.captureImage
import fr.alaedine.aesh.presentation.permission.rememberPermissionState
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import java.io.File

private const val CAPTURED_PHOTO_FILE_PREFIX = "observation_notes"

/** Shape of the guide each page goes in: a portrait sheet of paper, with A4's proportions. */
private const val PAGE_GUIDE_ASPECT_RATIO = 210f / 297f

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
        photoDirectory = viewModel.photoDirectory,
        onCaptureStarted = viewModel::onCaptureStarted,
        onPhotoCaptured = viewModel::onPhotoCaptured,
        onCaptureFailed = viewModel::onCaptureFailed,
        onLastPhotoRemoved = viewModel::onLastPhotoRemoved,
        onDoneClicked = viewModel::onDoneClicked,
        onErrorDismissed = viewModel::onErrorDismissed,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
fun NotesScannerScreen(
    uiState: NotesScannerUiState,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    photoDirectory: File,
    onCaptureStarted: () -> Unit,
    onPhotoCaptured: (File) -> Unit,
    onCaptureFailed: () -> Unit,
    onLastPhotoRemoved: () -> Unit,
    onDoneClicked: () -> Unit,
    onErrorDismissed: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    CameraScaffold(
        title = stringResource(R.string.notes_scanner_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { contentPadding ->
        if (!hasCameraPermission) {
            CameraPermissionState(
                message = stringResource(R.string.notes_scanner_camera_rationale),
                grantButtonLabel = stringResource(R.string.notes_scanner_grant_access),
                onRequestPermission = onRequestPermission,
                contentPadding = contentPadding,
            )
        } else {
            CameraViewfinder(
                hint = stringResource(R.string.notes_scanner_hint),
                guideAspectRatio = PAGE_GUIDE_ASPECT_RATIO,
                isBusy = uiState.isProcessing,
                busyMessage = stringResource(R.string.notes_scanner_reading),
                error = uiState.error?.let { scanErrorMessage(it) },
                onErrorDismissed = onErrorDismissed,
                contentPadding = contentPadding,
                leadingControl = {
                    TakenPhotos(
                        photoCount = uiState.photos.size,
                        isProcessing = uiState.isProcessing,
                        onLastPhotoRemoved = onLastPhotoRemoved,
                    )
                },
                trailingControl = { DoneButton(enabled = uiState.canFinish, onClick = onDoneClicked) },
            ) { imageCapture ->
                ShutterButton(
                    enabled = imageCapture != null,
                    isBusy = uiState.isProcessing,
                    contentDescription = stringResource(R.string.cd_take_photo),
                    onClick = {
                        imageCapture?.let { capture ->
                            onCaptureStarted()
                            captureImage(
                                context = context,
                                imageCapture = capture,
                                filePrefix = CAPTURED_PHOTO_FILE_PREFIX,
                                onCaptured = onPhotoCaptured,
                                directory = photoDirectory,
                                onFailed = onCaptureFailed,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** Resolves a [NotesScannerError] to its localized title and message. */
@Composable
private fun scanErrorMessage(error: NotesScannerError): ScanErrorMessage =
    when (error) {
        NotesScannerError.NoTextRecognized ->
            ScanErrorMessage(
                title = stringResource(R.string.scanner_no_text_recognized_title),
                message = stringResource(R.string.notes_scanner_no_text_recognized),
            )
        NotesScannerError.RecognitionFailed ->
            ScanErrorMessage(
                title = stringResource(R.string.notes_scanner_read_failed_title),
                message = stringResource(R.string.notes_scanner_read_failed),
            )
        NotesScannerError.CaptureFailed ->
            ScanErrorMessage(
                title = stringResource(R.string.notes_scanner_capture_failed_title),
                message = stringResource(R.string.notes_scanner_capture_failed),
            )
    }

/** How many photos were taken so far, and how to undo the last one; nothing until the first is taken. */
@Composable
private fun TakenPhotos(
    photoCount: Int,
    isProcessing: Boolean,
    onLastPhotoRemoved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (photoCount == 0) return
    Column(modifier = modifier) {
        Text(
            text = pluralStringResource(R.plurals.notes_scanner_photo_count, photoCount, photoCount),
            style = MaterialTheme.typography.labelLarge,
            // Lines up with the text of the button below.
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        TextButton(onClick = onLastPhotoRemoved, enabled = !isProcessing) {
            Text(text = stringResource(R.string.notes_scanner_remove_last))
        }
    }
}

/** Finishes the scan, reading every photo taken. */
@Composable
private fun DoneButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        // Narrower than the default, so that the label still fits on one line on a narrow phone with larger text.
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = stringResource(R.string.notes_scanner_done))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun NotesScannerControlsPreview() {
    AeshAssistantTheme(darkTheme = true) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                TakenPhotos(photoCount = 2, isProcessing = false, onLastPhotoRemoved = {})
            }
            ShutterButton(enabled = true, isBusy = false, contentDescription = "", onClick = {})
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                DoneButton(enabled = true, onClick = {})
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesScannerPermissionPreview() {
    AeshAssistantTheme {
        NotesScannerScreen(
            uiState = NotesScannerUiState(),
            hasCameraPermission = false,
            onRequestPermission = {},
            photoDirectory = File(""),
            onCaptureStarted = {},
            onPhotoCaptured = {},
            onCaptureFailed = {},
            onLastPhotoRemoved = {},
            onDoneClicked = {},
            onErrorDismissed = {},
            onNavigateBack = {},
        )
    }
}
