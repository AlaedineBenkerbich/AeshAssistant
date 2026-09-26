package fr.alaedine.aesh.presentation.report

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
import androidx.compose.material3.Button
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/** How long the success state stays visible before [DailyReportFormRoute] navigates back. */
private val SAVED_STATE_DISPLAY_DURATION = 900.milliseconds

/**
 * Stateful entry point wired to [DailyReportFormViewModel]. Kept separate
 * from the stateless [DailyReportFormScreen] so the latter has no
 * Android/ViewModel dependencies and stays trivially previewable and
 * testable.
 */
@Composable
fun DailyReportFormRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyReportFormViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            // Briefly shows the success state (see `DailyReportFormScreen`) before leaving,
            // so the user gets visible confirmation the report was actually saved.
            delay(SAVED_STATE_DISPLAY_DURATION)
            onNavigateBack()
        }
    }

    DailyReportFormScreen(
        uiState = uiState,
        onStudentSelected = viewModel::onStudentSelected,
        onMoodLevelChanged = viewModel::onMoodLevelChanged,
        onFocusLevelChanged = viewModel::onFocusLevelChanged,
        onSocialInteractionsChanged = viewModel::onSocialInteractionsChanged,
        onFreeNotesChanged = viewModel::onFreeNotesChanged,
        onSaveClicked = viewModel::onSaveClicked,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReportFormScreen(
    uiState: DailyReportFormUiState,
    onStudentSelected: (Student) -> Unit,
    onMoodLevelChanged: (Int) -> Unit,
    onFocusLevelChanged: (Int) -> Unit,
    onSocialInteractionsChanged: (Int) -> Unit,
    onFreeNotesChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Daily observation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                onStudentSelected = onStudentSelected,
                onMoodLevelChanged = onMoodLevelChanged,
                onFocusLevelChanged = onFocusLevelChanged,
                onSocialInteractionsChanged = onSocialInteractionsChanged,
                onFreeNotesChanged = onFreeNotesChanged,
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
    onStudentSelected: (Student) -> Unit,
    onMoodLevelChanged: (Int) -> Unit,
    onFocusLevelChanged: (Int) -> Unit,
    onSocialInteractionsChanged: (Int) -> Unit,
    onFreeNotesChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = formattedDate(uiState.date), style = MaterialTheme.typography.bodyMedium)

        if (uiState.students.isEmpty() && !uiState.isLoading) {
            Text(
                text = "No students yet. Add one before filling out a report.",
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
            LevelSlider(label = "Mood", level = uiState.moodLevel, onLevelChanged = onMoodLevelChanged)
            LevelSlider(label = "Focus", level = uiState.focusLevel, onLevelChanged = onFocusLevelChanged)
            LevelSlider(
                label = "Social interactions",
                level = uiState.socialInteractions,
                onLevelChanged = onSocialInteractionsChanged,
            )
            OutlinedTextField(
                value = uiState.freeNotes,
                onValueChange = onFreeNotesChanged,
                label = { Text(text = "Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = onSaveClicked,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Save")
        }
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

@Composable
private fun LevelSlider(label: String, level: Int, onLevelChanged: (Int) -> Unit) {
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
        Text(text = "Report saved", style = MaterialTheme.typography.titleMedium)
    }
}

/** Maps a 1-5 observation level to an emoji, giving each slider an at-a-glance visual scale. */
private fun levelEmoji(level: Int): String = when (level) {
    1 -> "😞"
    2 -> "🙁"
    3 -> "😐"
    4 -> "🙂"
    else -> "😄"
}

private fun formattedDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

@Preview(showBackground = true)
@Composable
private fun DailyReportFormScreenPreview() {
    AeshAssistantTheme {
        DailyReportFormScreen(
            uiState = DailyReportFormUiState(
                students = listOf(
                    Student(id = 1L, firstName = "Alice", className = "CE2"),
                    Student(id = 2L, firstName = "Amir", className = "CM2"),
                ),
                isLoading = false,
            ),
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onFreeNotesChanged = {},
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
            uiState = DailyReportFormUiState(
                students = listOf(alice),
                selectedStudent = alice,
                moodLevel = 4,
                focusLevel = 2,
                socialInteractions = 5,
                freeNotes = "Great focus after the morning break.",
                isLoading = false,
            ),
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onFreeNotesChanged = {},
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
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onFreeNotesChanged = {},
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
            onStudentSelected = {},
            onMoodLevelChanged = {},
            onFocusLevelChanged = {},
            onSocialInteractionsChanged = {},
            onFreeNotesChanged = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}
