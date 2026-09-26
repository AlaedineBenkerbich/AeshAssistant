package fr.alaedine.aesh.presentation.report

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel

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
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EssReportViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(PDF_MIME_TYPE),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val destination = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (destination == null) {
            viewModel.onExportFailedToOpenFile()
        } else {
            viewModel.onExportRequested(destination)
        }
    }

    EssReportScreen(
        uiState = uiState,
        onStudentSelected = viewModel::onStudentSelected,
        onStartDateSelected = viewModel::onStartDateSelected,
        onEndDateSelected = viewModel::onEndDateSelected,
        onGenerateClicked = viewModel::onGenerateClicked,
        onReportTextChanged = viewModel::onReportTextChanged,
        onExportClicked = { exportLauncher.launch(uiState.suggestedPdfFileName) },
        onStatusMessageShown = viewModel::onStatusMessageShown,
        onNavigateBack = onNavigateBack,
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
    onReportTextChanged: (String) -> Unit,
    onExportClicked: () -> Unit,
    onStatusMessageShown: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onStatusMessageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "ESS report") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Summarize a student's daily notes into a professional ESS report, entirely on-device.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (uiState.students.isEmpty() && !uiState.isLoadingStudents) {
                Text(
                    text = "No students yet. Add one before generating a report.",
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
                    label = "From",
                    date = uiState.startDate,
                    onDateSelected = onStartDateSelected,
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = "To",
                    date = uiState.endDate,
                    onDateSelected = onEndDateSelected,
                    modifier = Modifier.weight(1f),
                )
            }

            if (uiState.isDateRangeInvalid) {
                Text(
                    text = "The start date must be on or before the end date.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onGenerateClicked,
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (uiState.generatedText == null) "Generate report" else "Regenerate report")
            }

            if (uiState.isGenerating) {
                GeneratingIndicator()
            }

            val generatedText = uiState.generatedText
            if (generatedText != null) {
                OutlinedTextField(
                    value = generatedText,
                    onValueChange = onReportTextChanged,
                    label = { Text(text = "Report") },
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(onClick = onExportClicked, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "Export as PDF")
                }
            }
        }
    }
}

@Composable
private fun GeneratingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = "Generating report… the on-device AI model may need to download the first time.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
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
            value = selectedStudent?.let { "${it.firstName} — ${it.className}" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "Student") },
            placeholder = { Text(text = "Select a student") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            students.forEach { student ->
                DropdownMenuItem(
                    text = { Text(text = "${student.firstName} — ${student.className}") },
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
                    Text(text = "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { isPickerVisible = false }) {
                    Text(text = "Cancel")
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

private fun formattedDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

@Preview(showBackground = true)
@Composable
private fun EssReportScreenPreview() {
    AeshAssistantTheme {
        EssReportScreen(
            uiState = EssReportUiState(
                students = listOf(
                    Student(id = 1L, firstName = "Alice", className = "CE2"),
                    Student(id = 2L, firstName = "Amir", className = "CM2"),
                ),
                isLoadingStudents = false,
            ),
            onStudentSelected = {},
            onStartDateSelected = {},
            onEndDateSelected = {},
            onGenerateClicked = {},
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EssReportScreenGeneratingPreview() {
    AeshAssistantTheme {
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        EssReportScreen(
            uiState = EssReportUiState(
                students = listOf(alice),
                selectedStudent = alice,
                isLoadingStudents = false,
                isGenerating = true,
            ),
            onStudentSelected = {},
            onStartDateSelected = {},
            onEndDateSelected = {},
            onGenerateClicked = {},
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun EssReportScreenWithReportPreview() {
    AeshAssistantTheme {
        val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
        EssReportScreen(
            uiState = EssReportUiState(
                students = listOf(alice),
                selectedStudent = alice,
                isLoadingStudents = false,
                generatedText = "Alice showed steady improvement in focus over the period, with particularly " +
                    "strong engagement during afternoon sessions. Social interactions remained positive " +
                    "throughout, and mood stayed stable.",
            ),
            onStudentSelected = {},
            onStartDateSelected = {},
            onEndDateSelected = {},
            onGenerateClicked = {},
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onNavigateBack = {},
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
            onReportTextChanged = {},
            onExportClicked = {},
            onStatusMessageShown = {},
            onNavigateBack = {},
        )
    }
}
