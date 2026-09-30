package fr.alaedine.aesh.presentation.report

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Most photos of written notes that can be picked at once. */
private const val MAX_PICKED_PHOTOS = 5

/** How long the success state stays visible before [DailyReportFormRoute] navigates back. */
private val SAVED_STATE_DISPLAY_DURATION = 900.milliseconds

/**
 * Stateful entry point wired to [DailyReportFormViewModel]. Kept separate
 * from the stateless [DailyReportFormScreen] so the latter has no
 * Android/ViewModel dependencies and stays trivially previewable and
 * testable.
 *
 * @param studentId `null` when reached from the dashboard's "new report"
 * FAB (the student is picked from within the form), or the id of the
 * student to preselect when reached by tapping their row on the dashboard.
 * @param date The dashboard's currently selected date when reached by
 * tapping a student row there, so editing/completing that day's
 * observation opens the form already on the right day; `null` when
 * reached from the "new report" FAB, in which case the form defaults to
 * today (see [DailyReportFormViewModel]).
 */
@Composable
fun DailyReportFormRoute(
    studentId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    date: LocalDate? = null,
    viewModel: DailyReportFormViewModel = koinViewModel(parameters = { parametersOf(studentId, date) }),
    notesImportViewModel: NotesImportViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notesImportState by notesImportViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            // Briefly shows the success state (see `DailyReportFormScreen`) before leaving,
            // so the user gets visible confirmation the report was actually saved.
            delay(SAVED_STATE_DISPLAY_DURATION)
            onNavigateBack()
        }
    }

    LaunchedEffect(notesImportViewModel) {
        notesImportViewModel.extractedNotes.collect(viewModel::onNotesImported)
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) notesImportViewModel.onDictateClicked()
        }
    val choosePhotosLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_PHOTOS)) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                val files = withContext(Dispatchers.IO) { uris.mapNotNull { context.copyToCache(it) } }
                notesImportViewModel.onPhotosSelected(files)
            }
        }
    var pendingCameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhotoLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { isSaved ->
            val file = pendingCameraFile?.let(::File)
            pendingCameraFile = null
            when {
                file == null -> Unit
                isSaved -> notesImportViewModel.onPhotosSelected(listOf(file))
                else -> file.delete()
            }
        }

    DailyReportFormScreen(
        uiState = uiState,
        notesImportState = notesImportState,
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
            val hasPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                notesImportViewModel.onDictateClicked()
            } else {
                microphonePermissionLauncher.launch(
                    Manifest.permission.RECORD_AUDIO,
                )
            }
        },
        onStopDictationClicked = notesImportViewModel::onStopDictationClicked,
        onTakePhotoClicked = {
            val photoFile = File(context.cacheDir, "observation_note_${System.currentTimeMillis()}.jpg")
            pendingCameraFile = photoFile.absolutePath
            takePhotoLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile))
        },
        onChoosePhotosClicked = {
            choosePhotosLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onNotesImportMessageDismissed = notesImportViewModel::onMessageDismissed,
        onSaveClicked = viewModel::onSaveClicked,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/** Copies the picked image at [uri] to a temporary cache file for OCR, or `null` if it can't be read. */
private fun Context.copyToCache(uri: Uri): File? =
    runCatching {
        val file = File(cacheDir, "observation_note_${System.nanoTime()}.jpg")
        contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: return null
        file
    }.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportFormScreen(
    uiState: DailyReportFormUiState,
    notesImportState: NotesImportUiState,
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
    onStopDictationClicked: () -> Unit,
    onTakePhotoClicked: () -> Unit,
    onChoosePhotosClicked: () -> Unit,
    onNotesImportMessageDismissed: () -> Unit,
    onSaveClicked: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    ) { contentPadding ->
        if (uiState.isSaved) {
            SavedConfirmation(modifier = Modifier.padding(contentPadding))
        } else {
            DailyReportForm(
                uiState = uiState,
                notesImportState = notesImportState,
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
                onStopDictationClicked = onStopDictationClicked,
                onTakePhotoClicked = onTakePhotoClicked,
                onChoosePhotosClicked = onChoosePhotosClicked,
                onNotesImportMessageDismissed = onNotesImportMessageDismissed,
                onSaveClicked = onSaveClicked,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyReportForm(
    uiState: DailyReportFormUiState,
    notesImportState: NotesImportUiState,
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
    onStopDictationClicked: () -> Unit,
    onTakePhotoClicked: () -> Unit,
    onChoosePhotosClicked: () -> Unit,
    onNotesImportMessageDismissed: () -> Unit,
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
            NotesImportSection(
                state = notesImportState,
                onDictateClicked = onDictateClicked,
                onStopDictationClicked = onStopDictationClicked,
                onTakePhotoClicked = onTakePhotoClicked,
                onChoosePhotosClicked = onChoosePhotosClicked,
                onMessageDismissed = onNotesImportMessageDismissed,
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
 * Lets the user fill the free-text fields below by dictating (only offered
 * when on-device speech recognition exists) or from one or more photos of
 * handwritten notes; imported text is appended to, never replaces, what's
 * already typed.
 */
@Composable
private fun NotesImportSection(
    state: NotesImportUiState,
    onDictateClicked: () -> Unit,
    onStopDictationClicked: () -> Unit,
    onTakePhotoClicked: () -> Unit,
    onChoosePhotosClicked: () -> Unit,
    onMessageDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.notes_import_title), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.notes_import_description), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.isListening) {
                    Button(onClick = onStopDictationClicked, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.notes_import_stop_dictation))
                    }
                } else if (state.isDictationAvailable) {
                    OutlinedButton(onClick = onDictateClicked, enabled = !state.isBusy, modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.notes_import_dictate))
                    }
                }
                OutlinedButton(onClick = onTakePhotoClicked, enabled = !state.isBusy, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.notes_import_take_photo))
                }
                OutlinedButton(onClick = onChoosePhotosClicked, enabled = !state.isBusy, modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.notes_import_choose_photos))
                }
            }
            when {
                state.isListening ->
                    Text(
                        text = stringResource(R.string.notes_import_listening),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                state.isProcessing ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(text = stringResource(R.string.notes_import_processing), style = MaterialTheme.typography.bodyMedium)
                    }
                state.message != null ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(state.message.textRes()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onMessageDismissed) { Text(text = stringResource(R.string.action_ok)) }
                    }
            }
        }
    }
}

private fun NotesImportMessage.textRes(): Int =
    when (this) {
        NotesImportMessage.NoSpeechDetected -> R.string.notes_import_no_speech
        NotesImportMessage.SpeechFailed -> R.string.notes_import_speech_failed
        NotesImportMessage.NoTextFound -> R.string.notes_import_no_text
        NotesImportMessage.Filled -> R.string.notes_import_filled
        NotesImportMessage.FilledWithoutSorting -> R.string.notes_import_filled_unsorted
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
            notesImportState = NotesImportUiState(isDictationAvailable = true),
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
            onStopDictationClicked = {},
            onTakePhotoClicked = {},
            onChoosePhotosClicked = {},
            onNotesImportMessageDismissed = {},
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
            notesImportState = NotesImportUiState(isDictationAvailable = true),
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
            onStopDictationClicked = {},
            onTakePhotoClicked = {},
            onChoosePhotosClicked = {},
            onNotesImportMessageDismissed = {},
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
            notesImportState = NotesImportUiState(isDictationAvailable = true),
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
            onStopDictationClicked = {},
            onTakePhotoClicked = {},
            onChoosePhotosClicked = {},
            onNotesImportMessageDismissed = {},
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
            notesImportState = NotesImportUiState(isDictationAvailable = true),
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
            onStopDictationClicked = {},
            onTakePhotoClicked = {},
            onChoosePhotosClicked = {},
            onNotesImportMessageDismissed = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}
