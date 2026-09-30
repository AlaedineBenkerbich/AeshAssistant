package fr.alaedine.aesh.presentation.report

import android.Manifest
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.permission.rememberPermissionState
import fr.alaedine.aesh.presentation.report.notes.DictationDialog
import fr.alaedine.aesh.presentation.report.notes.DictationError
import fr.alaedine.aesh.presentation.report.notes.DictationViewModel
import fr.alaedine.aesh.presentation.report.notes.NotesAssistantCard
import fr.alaedine.aesh.presentation.report.notes.NotesSortingDialog
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** How long the success state stays visible before [DailyReportFormRoute] navigates back. */
private val SAVED_STATE_DISPLAY_DURATION = 900.milliseconds

/**
 * Stateful entry point wired to [DailyReportFormViewModel]. Kept separate
 * from the stateless [DailyReportFormScreen] so the latter has no
 * Android/ViewModel dependencies and stays trivially previewable and
 * testable.
 *
 * Also wires in filling the free-text fields from dictated or photographed
 * notes: it owns the `RECORD_AUDIO` runtime permission dictation needs (tied
 * to the Android platform, like the scanners' camera permission) and hands
 * the text of both sources to [DailyReportFormViewModel.onRawNotesReceived].
 *
 * @param studentId `null` when reached from the dashboard's "new report"
 * FAB (the student is picked from within the form), or the id of the
 * student to preselect when reached by tapping their row on the dashboard.
 * @param date The dashboard's currently selected date when reached by
 * tapping a student row there, so editing/completing that day's
 * observation opens the form already on the right day; `null` when
 * reached from the "new report" FAB, in which case the form defaults to
 * today (see [DailyReportFormViewModel]).
 * @param onScanNotes Opens the scanner that photographs handwritten notes;
 * its text comes back as [scannedNotes].
 * @param scannedNotes The text the scanner read from the user's photos, handed
 * back through the navigation back stack once they are done, or `null`.
 * Sorted into the form's fields, then cleared with [onScannedNotesConsumed].
 */
@Composable
fun DailyReportFormRoute(
    studentId: Long?,
    onNavigateBack: () -> Unit,
    onScanNotes: () -> Unit,
    scannedNotes: String?,
    onScannedNotesConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    date: LocalDate? = null,
    viewModel: DailyReportFormViewModel = koinViewModel(parameters = { parametersOf(studentId, date) }),
    dictationViewModel: DictationViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dictationState by dictationViewModel.uiState.collectAsStateWithLifecycle()
    val microphonePermission =
        rememberPermissionState(Manifest.permission.RECORD_AUDIO) { isGranted ->
            if (isGranted) dictationViewModel.onStartClicked() else dictationViewModel.onMicrophonePermissionDenied()
        }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            // Briefly shows the success state (see `DailyReportFormScreen`) before leaving,
            // so the user gets visible confirmation the report was actually saved.
            delay(SAVED_STATE_DISPLAY_DURATION)
            onNavigateBack()
        }
    }

    // Both sources of notes (the scanner's photos, the dictated transcript) end up in the form the same way.
    LaunchedEffect(scannedNotes) {
        val notes = scannedNotes ?: return@LaunchedEffect
        onScannedNotesConsumed()
        viewModel.onRawNotesReceived(notes)
    }
    LaunchedEffect(dictationState.transcript) {
        val transcript = dictationState.transcript ?: return@LaunchedEffect
        dictationViewModel.onTranscriptConsumed()
        viewModel.onRawNotesReceived(transcript)
    }
    LaunchedEffect(dictationState.error) {
        val error = dictationState.error ?: return@LaunchedEffect
        dictationViewModel.onErrorShown()
        viewModel.onDictationFailed(error)
    }

    DailyReportFormScreen(
        uiState = uiState,
        onDateSelected = viewModel::onDateSelected,
        onStudentSelected = viewModel::onStudentSelected,
        onMoodLevelChanged = viewModel::onMoodLevelChanged,
        onFocusLevelChanged = viewModel::onFocusLevelChanged,
        onSocialInteractionsChanged = viewModel::onSocialInteractionsChanged,
        onAutonomyLevelChanged = viewModel::onAutonomyLevelChanged,
        onObstaclesChanged = viewModel::onObstaclesChanged,
        onSupportStrategiesChanged = viewModel::onSupportStrategiesChanged,
        onFreeNotesChanged = viewModel::onFreeNotesChanged,
        onDictateClicked = {
            // Without an on-device recognizer, starting just reports why: no point asking for the microphone first.
            if (microphonePermission.isGranted || !dictationState.isAvailable) {
                dictationViewModel.onStartClicked()
            } else {
                microphonePermission.request()
            }
        },
        onScanNotesClicked = onScanNotes,
        onSkipSortingClicked = viewModel::onSkipSortingClicked,
        onStatusMessageShown = viewModel::onStatusMessageShown,
        onSaveClicked = viewModel::onSaveClicked,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )

    if (dictationState.isListening) {
        DictationDialog(
            partialTranscript = dictationState.partialTranscript,
            onStopClicked = dictationViewModel::onStopClicked,
            onCancelClicked = dictationViewModel::onCancelled,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportFormScreen(
    uiState: DailyReportFormUiState,
    onDateSelected: (LocalDate) -> Unit,
    onStudentSelected: (Student) -> Unit,
    onMoodLevelChanged: (Int) -> Unit,
    onFocusLevelChanged: (Int) -> Unit,
    onSocialInteractionsChanged: (Int) -> Unit,
    onAutonomyLevelChanged: (Int) -> Unit,
    onObstaclesChanged: (String) -> Unit,
    onSupportStrategiesChanged: (String) -> Unit,
    onFreeNotesChanged: (String) -> Unit,
    onDictateClicked: () -> Unit,
    onScanNotesClicked: () -> Unit,
    onSkipSortingClicked: () -> Unit,
    onStatusMessageShown: () -> Unit,
    onSaveClicked: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val statusMessageText = uiState.statusMessage?.let { resolvedStatusMessage(it) }

    LaunchedEffect(uiState.statusMessage) {
        val message = statusMessageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onStatusMessageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.daily_report_title)) },
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        if (uiState.isSaved) {
            SavedConfirmation(modifier = Modifier.padding(contentPadding))
        } else {
            DailyReportForm(
                uiState = uiState,
                onDateSelected = onDateSelected,
                onStudentSelected = onStudentSelected,
                onMoodLevelChanged = onMoodLevelChanged,
                onFocusLevelChanged = onFocusLevelChanged,
                onSocialInteractionsChanged = onSocialInteractionsChanged,
                onAutonomyLevelChanged = onAutonomyLevelChanged,
                onObstaclesChanged = onObstaclesChanged,
                onSupportStrategiesChanged = onSupportStrategiesChanged,
                onFreeNotesChanged = onFreeNotesChanged,
                onDictateClicked = onDictateClicked,
                onScanNotesClicked = onScanNotesClicked,
                onSaveClicked = onSaveClicked,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    if (uiState.isSortingNotes) {
        NotesSortingDialog(onSkipClicked = onSkipSortingClicked)
    }
}

/** Resolves a one-shot [DailyReportStatusMessage] to its localized Snackbar text. */
@Composable
private fun resolvedStatusMessage(message: DailyReportStatusMessage): String =
    when (message) {
        is DailyReportStatusMessage.NotesFilledIn -> stringResource(R.string.notes_filled_in)
        is DailyReportStatusMessage.NotesAddedUnsorted ->
            stringResource(
                when (message.reason) {
                    UnsortedNotesReason.AiUnavailable -> R.string.notes_added_unsorted_ai_unavailable
                    UnsortedNotesReason.Timeout -> R.string.notes_added_unsorted_timeout
                    UnsortedNotesReason.Failed -> R.string.notes_added_unsorted_failed
                    UnsortedNotesReason.Skipped -> R.string.notes_added_unsorted_skipped
                },
            )
        is DailyReportStatusMessage.DictationFailed ->
            stringResource(
                when (message.error) {
                    DictationError.Unavailable -> R.string.notes_dictation_unavailable
                    DictationError.LanguageNotInstalled -> R.string.notes_dictation_language_not_installed
                    DictationError.NoSpeechDetected -> R.string.notes_dictation_no_speech
                    DictationError.MicrophonePermissionDenied -> R.string.notes_dictation_permission_denied
                    DictationError.Failed -> R.string.notes_dictation_failed
                },
            )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyReportForm(
    uiState: DailyReportFormUiState,
    onDateSelected: (LocalDate) -> Unit,
    onStudentSelected: (Student) -> Unit,
    onMoodLevelChanged: (Int) -> Unit,
    onFocusLevelChanged: (Int) -> Unit,
    onSocialInteractionsChanged: (Int) -> Unit,
    onAutonomyLevelChanged: (Int) -> Unit,
    onObstaclesChanged: (String) -> Unit,
    onSupportStrategiesChanged: (String) -> Unit,
    onFreeNotesChanged: (String) -> Unit,
    onDictateClicked: () -> Unit,
    onScanNotesClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DateField(date = uiState.date, onDateSelected = onDateSelected)

        if (uiState.students.isEmpty() && !uiState.isLoading) {
            Text(
                text = stringResource(R.string.daily_report_empty_students),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            StudentDropdown(
                students = uiState.students,
                selectedStudent = uiState.selectedStudent,
                onStudentSelected = onStudentSelected,
            )
        }

        if (uiState.selectedStudent != null) {
            LevelSlider(
                label = stringResource(R.string.daily_report_mood),
                level = uiState.moodLevel,
                onLevelChanged = onMoodLevelChanged,
            )
            LevelSlider(
                label = stringResource(R.string.daily_report_focus),
                level = uiState.focusLevel,
                onLevelChanged = onFocusLevelChanged,
            )
            LevelSlider(
                label = stringResource(R.string.daily_report_social_interactions),
                level = uiState.socialInteractions,
                onLevelChanged = onSocialInteractionsChanged,
            )
            LevelSlider(
                label = stringResource(R.string.daily_report_autonomy),
                level = uiState.autonomyLevel,
                onLevelChanged = onAutonomyLevelChanged,
            )
            NotesAssistantCard(
                onDictateClicked = onDictateClicked,
                onScanNotesClicked = onScanNotesClicked,
            )
            OutlinedTextField(
                value = uiState.obstacles,
                onValueChange = onObstaclesChanged,
                label = { Text(text = stringResource(R.string.daily_report_obstacles)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.supportStrategies,
                onValueChange = onSupportStrategiesChanged,
                label = { Text(text = stringResource(R.string.daily_report_support_strategies)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.freeNotes,
                onValueChange = onFreeNotesChanged,
                label = { Text(text = stringResource(R.string.daily_report_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onSaveClicked,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.action_save))
        }
    }
}

/**
 * A read-only field that opens a [DatePickerDialog] on tap, since a plain
 * `OutlinedTextField` has no `onClick`. Restricted via
 * [notAfterTodaySelectableDates] to today or earlier: an observation can't
 * be logged for a day that hasn't happened yet, so this is how the form
 * lets a missed observation be backfilled for a previous day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPickerVisible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) {
        if (isPressed) isPickerVisible = true
    }

    OutlinedTextField(
        value = formattedDate(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(text = stringResource(R.string.daily_report_date)) },
        trailingIcon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = null) },
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
    )

    if (isPickerVisible) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = date.toUtcEpochMillis(),
                selectableDates = notAfterTodaySelectableDates,
            )
        DatePickerDialog(
            onDismissRequest = { isPickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(it.toUtcLocalDate()) }
                    isPickerVisible = false
                }) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickerVisible = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Restricts [DateField]'s picker so no day after today can be selected. */
private val notAfterTodaySelectableDates =
    object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toUtcLocalDate() <= LocalDate.now()
    }

/** Material3's [rememberDatePickerState] operates in UTC epoch millis regardless of the device's time zone. */
private fun LocalDate.toUtcEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDropdown(
    students: List<Student>,
    selectedStudent: Student?,
    onStudentSelected: (Student) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it },
    ) {
        OutlinedTextField(
            value = selectedStudent?.let { stringResource(R.string.student_display_name, it.firstName, it.className) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.label_student)) },
            placeholder = { Text(text = stringResource(R.string.placeholder_select_student)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            students.forEach { student ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.student_display_name, student.firstName, student.className)) },
                    onClick = {
                        onStudentSelected(student)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LevelSlider(
    label: String,
    level: Int,
    onLevelChanged: (Int) -> Unit,
) {
    Column {
        Text(text = "$label  ${levelEmoji(level)}", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChanged(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3,
        )
    }
}

@Composable
private fun SavedConfirmation(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Text(text = stringResource(R.string.daily_report_saved), style = MaterialTheme.typography.titleMedium)
    }
}

/** Maps a 1-5 observation level to an emoji, giving each slider an at-a-glance visual scale. */
private fun levelEmoji(level: Int): String =
    when (level) {
        1 -> "😞"
        2 -> "🙁"
        3 -> "😐"
        4 -> "🙂"
        else -> "😄"
    }

private fun formattedDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

@Preview(showBackground = true)
@Composable
private fun DailyReportFormScreenPreview() {
    AeshAssistantTheme {
        DailyReportFormScreen(
            uiState =
                DailyReportFormUiState(
                    students =
                        listOf(
                            Student(id = 1L, firstName = "Alice", className = "CE2"),
                            Student(id = 2L, firstName = "Amir", className = "CM2"),
                        ),
                    isLoading = false,
                ),
            onDateSelected = {},
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onAutonomyLevelChanged = {},
            onObstaclesChanged = {},
            onSupportStrategiesChanged = {},
            onFreeNotesChanged = {},
            onDictateClicked = {},
            onScanNotesClicked = {},
            onSkipSortingClicked = {},
            onStatusMessageShown = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyReportFormScreenFilledPreview() {
    AeshAssistantTheme {
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        DailyReportFormScreen(
            uiState =
                DailyReportFormUiState(
                    students = listOf(alice),
                    selectedStudent = alice,
                    moodLevel = 4,
                    focusLevel = 2,
                    socialInteractions = 5,
                    autonomyLevel = 3,
                    obstacles = "Struggled to stay seated during the reading exercise.",
                    supportStrategies = "A visual timer helped her refocus after each break.",
                    freeNotes = "Great focus after the morning break.",
                    isLoading = false,
                ),
            onDateSelected = {},
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onAutonomyLevelChanged = {},
            onObstaclesChanged = {},
            onSupportStrategiesChanged = {},
            onFreeNotesChanged = {},
            onDictateClicked = {},
            onScanNotesClicked = {},
            onSkipSortingClicked = {},
            onStatusMessageShown = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyReportFormScreenNoStudentsPreview() {
    AeshAssistantTheme {
        DailyReportFormScreen(
            uiState = DailyReportFormUiState(students = emptyList(), isLoading = false),
            onDateSelected = {},
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onAutonomyLevelChanged = {},
            onObstaclesChanged = {},
            onSupportStrategiesChanged = {},
            onFreeNotesChanged = {},
            onDictateClicked = {},
            onScanNotesClicked = {},
            onSkipSortingClicked = {},
            onStatusMessageShown = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DailyReportFormScreenSavedPreview() {
    AeshAssistantTheme {
        DailyReportFormScreen(
            uiState = DailyReportFormUiState(isLoading = false, isSaved = true),
            onDateSelected = {},
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onAutonomyLevelChanged = {},
            onObstaclesChanged = {},
            onSupportStrategiesChanged = {},
            onFreeNotesChanged = {},
            onDictateClicked = {},
            onScanNotesClicked = {},
            onSkipSortingClicked = {},
            onStatusMessageShown = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}
