package fr.alaedine.aesh.presentation.schedule.scanner

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.presentation.camera.CameraPermissionState
import fr.alaedine.aesh.presentation.camera.CameraScaffold
import fr.alaedine.aesh.presentation.camera.CameraViewfinder
import fr.alaedine.aesh.presentation.camera.ScanErrorMessage
import fr.alaedine.aesh.presentation.camera.ShutterButton
import fr.alaedine.aesh.presentation.camera.captureImage
import fr.alaedine.aesh.presentation.components.GroupedListItem
import fr.alaedine.aesh.presentation.components.GroupedListItemSpacing
import fr.alaedine.aesh.presentation.permission.rememberPermissionState
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import fr.alaedine.aesh.presentation.theme.AeshTheme
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

private const val CAPTURED_PHOTO_FILE_PREFIX = "schedule_scan"

/** Shape of the guide the class goes in: a class's day, times, subject and room make a block wider than tall. */
private const val CLASS_GUIDE_ASPECT_RATIO = 4f / 3f

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
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.isGranted) cameraPermission.request()
    }

    ScheduleScannerScreen(
        uiState = uiState,
        hasCameraPermission = cameraPermission.isGranted,
        onRequestPermission = cameraPermission::request,
        onPhotoCaptured = viewModel::onPhotoCaptured,
        onRetake = viewModel::onRetake,
        onErrorDismissed = viewModel::onErrorDismissed,
        onConfirm = onScanned,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

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

    CameraScaffold(
        title = stringResource(R.string.schedule_scanner_title),
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) { contentPadding ->
        val parsedScheduleSlot = uiState.parsedScheduleSlot
        when {
            !hasCameraPermission ->
                CameraPermissionState(
                    message = stringResource(R.string.schedule_scanner_camera_rationale),
                    grantButtonLabel = stringResource(R.string.schedule_scanner_grant_access),
                    onRequestPermission = onRequestPermission,
                    contentPadding = contentPadding,
                )
            parsedScheduleSlot != null ->
                ScheduleScanReview(
                    parsed = parsedScheduleSlot,
                    onConfirm = { onConfirm(parsedScheduleSlot) },
                    onRetake = onRetake,
                    contentPadding = contentPadding,
                )
            else ->
                CameraViewfinder(
                    hint = stringResource(R.string.schedule_scanner_hint),
                    guideAspectRatio = CLASS_GUIDE_ASPECT_RATIO,
                    isBusy = uiState.isProcessing,
                    busyMessage = stringResource(R.string.schedule_scanner_reading),
                    error = uiState.error?.let { scanErrorMessage(it) },
                    onErrorDismissed = onErrorDismissed,
                    contentPadding = contentPadding,
                ) { imageCapture ->
                    ShutterButton(
                        enabled = imageCapture != null,
                        isBusy = uiState.isProcessing,
                        contentDescription = stringResource(R.string.cd_take_photo),
                        onClick = {
                            imageCapture?.let { capture ->
                                captureImage(
                                    context = context,
                                    imageCapture = capture,
                                    filePrefix = CAPTURED_PHOTO_FILE_PREFIX,
                                    onCaptured = onPhotoCaptured,
                                )
                            }
                        },
                    )
                }
        }
    }
}

/** Resolves a [ScheduleScannerError] to its localized title and message. */
@Composable
private fun scanErrorMessage(error: ScheduleScannerError): ScanErrorMessage =
    when (error) {
        ScheduleScannerError.NoTextRecognized ->
            ScanErrorMessage(
                title = stringResource(R.string.scanner_no_text_recognized_title),
                message = stringResource(R.string.schedule_scanner_no_text_recognized),
            )
        ScheduleScannerError.RecognitionFailed ->
            ScanErrorMessage(
                title = stringResource(R.string.schedule_scanner_read_failed_title),
                message = stringResource(R.string.schedule_scanner_read_failed),
            )
    }

/**
 * The fields read from the photo, for the user to check before they
 * pre-fill the class form, with the actions to use them or retake the photo
 * kept in reach below.
 */
@Composable
private fun ScheduleScanReview(
    parsed: ParsedScheduleSlot,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val fields =
        listOf(
            stringResource(R.string.schedule_field_day) to parsed.dayOfWeek?.displayName(),
            stringResource(R.string.schedule_field_start_time) to parsed.startTime?.format(TIME_FORMATTER),
            stringResource(R.string.schedule_field_end_time) to parsed.endTime?.format(TIME_FORMATTER),
            stringResource(R.string.schedule_field_subject) to parsed.subject,
            stringResource(R.string.schedule_field_room) to parsed.room,
        )
    Column(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.schedule_scanner_review_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.schedule_scanner_review_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(GroupedListItemSpacing)) {
                fields.forEachIndexed { index, (label, value) ->
                    RecognizedField(index = index, count = fields.size, label = label, value = value)
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.schedule_scanner_use_details))
            }
            OutlinedButton(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.schedule_scanner_retake_photo))
            }
        }
    }
}

/**
 * One field read from the photo: its [label] over its [value], or, when it
 * couldn't be read, a note flagging it as still to fill in on the form.
 */
@Composable
private fun RecognizedField(
    index: Int,
    count: Int,
    label: String,
    value: String?,
) {
    val pendingColor = AeshTheme.extendedColors.pending.color
    GroupedListItem(
        index = index,
        count = count,
        overlineContent = { Text(text = label) },
        headlineContent = {
            Text(
                text = value ?: stringResource(R.string.schedule_scanner_not_recognized),
                color = if (value == null) pendingColor else Color.Unspecified,
            )
        },
        trailingContent =
            if (value == null) {
                { Icon(imageVector = Icons.Outlined.Info, contentDescription = null, tint = pendingColor) }
            } else {
                null
            },
        // Read as one item, its label first.
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
}

/** E.g. "Monday" (or "Lundi" in French), capitalized like on the class form it's about to fill in. */
private fun DayOfWeek.displayName(): String {
    val locale = Locale.getDefault()
    return getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleScanReviewPreview() {
    AeshAssistantTheme {
        ScheduleScannerScreen(
            uiState =
                ScheduleScannerUiState(
                    parsedScheduleSlot =
                        ParsedScheduleSlot(
                            dayOfWeek = DayOfWeek.TUESDAY,
                            startTime = LocalTime.of(10, 0),
                            endTime = LocalTime.of(11, 0),
                            subject = "Mathématiques",
                            room = null,
                        ),
                ),
            hasCameraPermission = true,
            onRequestPermission = {},
            onPhotoCaptured = {},
            onRetake = {},
            onErrorDismissed = {},
            onConfirm = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleScannerPermissionPreview() {
    AeshAssistantTheme {
        ScheduleScannerScreen(
            uiState = ScheduleScannerUiState(),
            hasCameraPermission = false,
            onRequestPermission = {},
            onPhotoCaptured = {},
            onRetake = {},
            onErrorDismissed = {},
            onConfirm = {},
            onNavigateBack = {},
        )
    }
}
