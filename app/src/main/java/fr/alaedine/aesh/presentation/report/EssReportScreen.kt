package fr.alaedine.aesh.presentation.report

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.usecase.EssReportProgress
import fr.alaedine.aesh.presentation.navigation.AeshBottomNavTab
import fr.alaedine.aesh.presentation.navigation.AeshBottomNavigationBar
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Stateful entry point wired to [EssReportViewModel]. Kept separate from
 * the stateless [EssReportScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 *
 * Owns the Storage Access Framework "save as" launcher, resolving the
 * user-picked `Uri` into an `OutputStream` via `ContentResolver` — same
 * pattern as `fr.alaedine.aesh.presentation.settings.SettingsRoute`'s
 * backup export — so [EssReportViewModel] itself never needs to know
 * `android.net.Uri`/`ContentResolver` exist.
 */
@Composable
fun EssReportRoute(
    onTabSelected: (AeshBottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EssReportViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Titled after what the draft is about, which is known even right after the process was recreated.
    val draft = uiState.draft
    val pdfTitle =
        stringResource(
            R.string.ess_report_pdf_title,
            draft?.studentFirstName.orEmpty(),
            (draft?.startDate ?: uiState.startDate).toString(),
            (draft?.endDate ?: uiState.endDate).toString(),
        )

    // Back would pop this tab and throw its state away; going back to the dashboard tab instead keeps the draft
    // (and a running generation) for when the user comes back, like switching tabs does.
    BackHandler(enabled = uiState.draft != null || uiState.isGenerating) {
        onTabSelected(AeshBottomNavTab.Dashboard)
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val destination = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            if (destination == null) {
                viewModel.onExportFailedToOpenFile()
            } else {
                viewModel.onExportRequested(destination, pdfTitle)
            }
        }

    EssReportScreen(
        uiState = uiState,
        onStudentSelected = viewModel::onStudentSelected,
        onStartDateSelected = viewModel::onStartDateSelected,
        onEndDateSelected = viewModel::onEndDateSelected,
        onGenerateClicked = viewModel::onGenerateClicked,
        onCancelGenerationClicked = viewModel::onCancelGenerationClicked,
        onRegenerateConfirmed = viewModel::onRegenerateConfirmed,
        onRegenerateDismissed = viewModel::onRegenerateDismissed,
        onReportTextChanged = viewModel::onReportTextChanged,
        onExportClicked = { exportLauncher.launch(uiState.suggestedPdfFileName) },
        onStatusMessageShown = viewModel::onStatusMessageShown,
        onTabSelected = onTabSelected,
        modifier = modifier,
    )
}

/**
 * Lets the user pick a student and a date range, generate an ESS report
 * draft from their daily notes entirely on-device, edit the result, and
 * export it as a PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssReportScreen(
    uiState: EssReportUiState,
    onStudentSelected: (Student) -> Unit,
    onStartDateSelected: (LocalDate) -> Unit,
    onEndDateSelected: (LocalDate) -> Unit,
    onGenerateClicked: () -> Unit,
    onCancelGenerationClicked: () -> Unit,
    onRegenerateConfirmed: () -> Unit,
    onRegenerateDismissed: () -> Unit,
    onReportTextChanged: (String) -> Unit,
    onExportClicked: () -> Unit,
    onStatusMessageShown: () -> Unit,
    onTabSelected: (AeshBottomNavTab) -> Unit,
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
                title = { Text(text = stringResource(R.string.ess_report_title)) },
            )
        },
        bottomBar = {
            AeshBottomNavigationBar(
                currentTab = AeshBottomNavTab.EssReport,
                onTabSelected = onTabSelected,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.ess_report_description),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (uiState.students.isEmpty() && !uiState.isLoadingStudents) {
                Text(
                    text = stringResource(R.string.ess_report_empty_students),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                StudentDropdown(
                    students = uiState.students,
                    selectedStudent = uiState.selectedStudent,
                    onStudentSelected = onStudentSelected,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DateField(
                    label = stringResource(R.string.ess_report_from),
                    date = uiState.startDate,
                    onDateSelected = onStartDateSelected,
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = stringResource(R.string.ess_report_to),
                    date = uiState.endDate,
                    onDateSelected = onEndDateSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            if (uiState.isDateRangeInvalid) {
                Text(
                    text = stringResource(R.string.ess_report_invalid_date_range),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onGenerateClicked,
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text =
                        stringResource(
                            if (uiState.draft == null) R.string.ess_report_generate else R.string.ess_report_regenerate,
                        ),
                )
            }

            if (uiState.isGenerating) {
                GeneratingIndicator(progress = uiState.generationProgress, onCancelClicked = onCancelGenerationClicked)
                // Android only lets the app on screen use on-device AI: a screen timeout would stop the generation.
                KeepScreenOn()
            }

            val generatedText = uiState.generatedText
            if (generatedText != null) {
                OutlinedTextField(
                    value = generatedText,
                    onValueChange = onReportTextChanged,
                    label = { Text(text = stringResource(R.string.ess_report_label)) },
                    minLines = 8,
                    // Read-only while a new draft is being generated, since that one will replace this text.
                    enabled = !uiState.isGenerating,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(onClick = onExportClicked, enabled = !uiState.isGenerating, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.ess_report_export_pdf))
                }
            }
        }
    }

    if (uiState.isRegenerateConfirmationVisible) {
        RegenerateConfirmationDialog(onConfirm = onRegenerateConfirmed, onDismiss = onRegenerateDismissed)
    }
}

/** Resolves a one-shot [EssReportStatusMessage] to its localized Snackbar text. */
@Composable
private fun resolvedStatusMessage(message: EssReportStatusMessage): String =
    when (message) {
        is EssReportStatusMessage.NoReportsInRange ->
            stringResource(R.string.ess_report_no_reports_in_range, message.studentFirstName)
        is EssReportStatusMessage.AiFeatureUnavailable -> stringResource(R.string.ess_report_ai_unavailable)
        is EssReportStatusMessage.GenerationTimeout -> stringResource(R.string.ess_report_generation_timeout)
        is EssReportStatusMessage.PromptTooLong -> stringResource(R.string.ess_report_prompt_too_long)
        is EssReportStatusMessage.AiBusy -> stringResource(R.string.ess_report_ai_busy)
        is EssReportStatusMessage.BackgroundUseBlocked -> stringResource(R.string.ess_report_background_use_blocked)
        is EssReportStatusMessage.NotEnoughStorage -> stringResource(R.string.ess_report_not_enough_storage)
        is EssReportStatusMessage.GenerationFailed -> stringResource(R.string.ess_report_generation_failed_fallback)
        is EssReportStatusMessage.ExportSuccess -> stringResource(R.string.ess_report_export_success)
        is EssReportStatusMessage.ExportFailed -> stringResource(R.string.ess_report_export_failed)
        is EssReportStatusMessage.ExportFileOpenFailed -> stringResource(R.string.error_export_file_open_failed)
    }

@Composable
private fun GeneratingIndicator(
    progress: EssReportProgress?,
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ess_report_generating),
                style = MaterialTheme.typography.bodySmall,
            )
            // A long period is summarized in several requests, each of which can take a while on-device.
            if (progress != null && progress.stepCount > 1) {
                Text(
                    text = stringResource(R.string.ess_report_generating_step, progress.step, progress.stepCount),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(onClick = onCancelClicked) {
            Text(text = stringResource(R.string.action_cancel))
        }
    }
}

/** Keeps the screen on for as long as this is composed. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun RegenerateConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ess_report_regenerate_confirmation_title)) },
        text = { Text(text = stringResource(R.string.ess_report_regenerate_confirmation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.ess_report_regenerate_confirmation_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

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

/** A read-only field that opens a [DatePickerDialog] on tap, since a plain `OutlinedTextField` has no `onClick`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
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
        label = { Text(text = label) },
        trailingIcon = { Icon(imageVector = Icons.Default.DateRange, contentDescription = null) },
        interactionSource = interactionSource,
        modifier = modifier,
    )

    if (isPickerVisible) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toUtcEpochMillis())
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

/** Material3's [rememberDatePickerState] operates in UTC epoch millis regardless of the device's time zone. */
private fun LocalDate.toUtcEpochMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

private fun formattedDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

@Preview(showBackground = true)
@Composable
private fun EssReportScreenPreview() {
    AeshAssistantTheme {
        EssReportScreen(
            uiState =
                EssReportUiState(
                    students =
                        listOf(
                            Student(id = 1L, firstName = "Alice", className = "CE2"),
                            Student(id = 2L, firstName = "Amir", className = "CM2"),
                        ),
                    isLoadingStudents = false,
                ),
            onStudentSelected = {},
            onStartDateSelected = {},
            onEndDateSelected = {},
            onGenerateClicked = {},
            onCancelGenerationClicked = {},
            onRegenerateConfirmed = {},
            onRegenerateDismissed = {},
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onTabSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EssReportScreenGeneratingPreview() {
    AeshAssistantTheme {
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        EssReportScreen(
            uiState =
                EssReportUiState(
                    students = listOf(alice),
                    selectedStudentId = alice.id,
                    isLoadingStudents = false,
                    isGenerating = true,
                    generationProgress = EssReportProgress(step = 2, stepCount = 4),
                ),
            onStudentSelected = {},
            onStartDateSelected = {},
            onEndDateSelected = {},
            onGenerateClicked = {},
            onCancelGenerationClicked = {},
            onRegenerateConfirmed = {},
            onRegenerateDismissed = {},
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onTabSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EssReportScreenWithReportPreview() {
    AeshAssistantTheme {
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        EssReportScreen(
            uiState =
                EssReportUiState(
                    students = listOf(alice),
                    selectedStudentId = alice.id,
                    isLoadingStudents = false,
                    draft =
                        EssReportDraft(
                            text =
                                "Alice showed steady improvement in focus over the period, with particularly " +
                                    "strong engagement during afternoon sessions. Social interactions remained positive " +
                                    "throughout, and mood stayed stable.",
                            studentFirstName = alice.firstName,
                            startDate = LocalDate.of(2026, 9, 1),
                            endDate = LocalDate.of(2026, 11, 20),
                        ),
                ),
            onStudentSelected = {},
            onStartDateSelected = {},
            onEndDateSelected = {},
            onGenerateClicked = {},
            onCancelGenerationClicked = {},
            onRegenerateConfirmed = {},
            onRegenerateDismissed = {},
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onTabSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EssReportScreenNoStudentsPreview() {
    AeshAssistantTheme {
        EssReportScreen(
            uiState = EssReportUiState(students = emptyList(), isLoadingStudents = false),
            onStudentSelected = {},
            onStartDateSelected = {},
            onEndDateSelected = {},
            onGenerateClicked = {},
            onCancelGenerationClicked = {},
            onRegenerateConfirmed = {},
            onRegenerateDismissed = {},
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onTabSelected = {},
        )
    }
}
