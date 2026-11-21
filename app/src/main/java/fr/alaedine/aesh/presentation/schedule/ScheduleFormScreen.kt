package fr.alaedine.aesh.presentation.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Stateful entry point wired to [ScheduleFormViewModel]. Kept separate from
 * the stateless [ScheduleFormScreen] so the latter has no Android/ViewModel
 * dependencies and stays trivially previewable and testable.
 *
 * @param scheduleSlotId `null` to add a new slot, or the id of the slot to edit.
 * @param prefill Fields recognized by the schedule photo scanner to seed
 * the form with; only meaningful together with `scheduleSlotId = null`
 * (see `ScheduleScannerRoute` and `AeshNavHost`).
 */
@Composable
fun ScheduleFormRoute(
    scheduleSlotId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    prefill: ParsedScheduleSlot? = null,
    viewModel: ScheduleFormViewModel = koinViewModel(parameters = { parametersOf(scheduleSlotId, prefill) }),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    ScheduleFormScreen(
        uiState = uiState,
        onDayOfWeekChanged = viewModel::onDayOfWeekChanged,
        onStartTimeChanged = viewModel::onStartTimeChanged,
        onEndTimeChanged = viewModel::onEndTimeChanged,
        onSubjectChanged = viewModel::onSubjectChanged,
        onRoomChanged = viewModel::onRoomChanged,
        onStudentToggled = viewModel::onStudentToggled,
        onSaveClicked = viewModel::onSaveClicked,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

/**
 * Form to add or edit a class slot, in three rounded sections on a tinted
 * background: when (day, start and end times side by side), what (subject
 * and room), and who (the students, as chips to toggle). Save sits in the
 * top bar, always within reach however long the student list gets, and the
 * form says why it can't be saved yet (an end time not after the start
 * time, no student picked) instead of only greying the button out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFormScreen(
    uiState: ScheduleFormUiState,
    onDayOfWeekChanged: (DayOfWeek) -> Unit,
    onStartTimeChanged: (LocalTime) -> Unit,
    onEndTimeChanged: (LocalTime) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onRoomChanged: (String) -> Unit,
    onStudentToggled: (Long) -> Unit,
    onSaveClicked: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text =
                            stringResource(
                                if (uiState.isEditing) R.string.schedule_form_title_edit else R.string.schedule_form_title_add,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveClicked,
                        enabled = uiState.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(text = stringResource(R.string.action_save))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormSection(title = stringResource(R.string.schedule_section_when)) {
                DayOfWeekDropdown(
                    selectedDayOfWeek = uiState.dayOfWeek,
                    onDayOfWeekSelected = onDayOfWeekChanged,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(
                        label = stringResource(R.string.schedule_field_start_time),
                        time = uiState.startTime,
                        onTimeSelected = onStartTimeChanged,
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = stringResource(R.string.schedule_field_end_time),
                        time = uiState.endTime,
                        onTimeSelected = onEndTimeChanged,
                        isError = !uiState.endTime.isAfter(uiState.startTime),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!uiState.endTime.isAfter(uiState.startTime)) {
                    Text(
                        text = stringResource(R.string.schedule_end_time_before_start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            FormSection(title = stringResource(R.string.schedule_section_class)) {
                OutlinedTextField(
                    value = uiState.subject,
                    onValueChange = onSubjectChanged,
                    label = { Text(text = stringResource(R.string.schedule_field_subject)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.room,
                    onValueChange = onRoomChanged,
                    label = { Text(text = stringResource(R.string.schedule_field_room)) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FormSection(title = stringResource(R.string.schedule_field_students)) {
                StudentAssignmentSection(
                    students = uiState.students,
                    selectedStudentIds = uiState.selectedStudentIds,
                    onStudentToggled = onStudentToggled,
                )
            }
        }
    }
}

/** A titled, rounded group of form fields on the brightest surface color, standing out from the screen's tinted background. */
@Composable
private fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/**
 * Chips assigning zero or more [students] to this slot. At least one is
 * required to save (see [ScheduleFormUiState.canSave]), so it's always clear
 * which student(s) an observation logged from this slot is for; a hint says
 * so while none is picked.
 */
@Composable
private fun StudentAssignmentSection(
    students: List<Student>,
    selectedStudentIds: Set<Long>,
    onStudentToggled: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (students.isEmpty()) {
        Text(
            text = stringResource(R.string.schedule_no_students_available),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            students.forEach { student ->
                val isSelected = student.id in selectedStudentIds
                FilterChip(
                    selected = isSelected,
                    onClick = { onStudentToggled(student.id) },
                    label = { Text(text = stringResource(R.string.student_display_name, student.firstName, student.className)) },
                    leadingIcon =
                        if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                )
            }
        }
        if (selectedStudentIds.isEmpty()) {
            Text(
                text = stringResource(R.string.schedule_pick_a_student),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Dropdown restricted to Monday through Saturday: Sunday is excluded since
 * the AESH doesn't work Sundays, so a class can't be scheduled that day
 * (see [ScheduleFormViewModel.onDayOfWeekChanged] for the matching
 * defensive guard against setting it any other way).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfWeekDropdown(
    selectedDayOfWeek: DayOfWeek,
    onDayOfWeekSelected: (DayOfWeek) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = it },
    ) {
        OutlinedTextField(
            value = selectedDayOfWeek.displayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = stringResource(R.string.schedule_field_day)) },
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
            DayOfWeek.entries.filter { it != DayOfWeek.SUNDAY }.forEach { dayOfWeek ->
                DropdownMenuItem(
                    text = { Text(text = dayOfWeek.displayName()) },
                    onClick = {
                        onDayOfWeekSelected(dayOfWeek)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

/**
 * Read-only field showing [time], formatted `HH:mm`, that opens a
 * [TimePickerDialog] on tap. The transparent [Box] overlay (rather than a
 * `clickable` modifier directly on the text field) is what reliably
 * captures the tap: a read-only [OutlinedTextField] still handles touches
 * itself (to focus/place the cursor) before a sibling modifier would see them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var isDialogVisible by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = time.format(TIME_FORMATTER),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable { isDialogVisible = true },
        )
    }
    if (isDialogVisible) {
        val timePickerState =
            rememberTimePickerState(
                initialHour = time.hour,
                initialMinute = time.minute,
                is24Hour = true,
            )
        TimePickerDialog(
            onDismissRequest = { isDialogVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        isDialogVisible = false
                    },
                ) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { isDialogVisible = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
            title = { Text(text = label) },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

/** E.g. "Monday" (or "Lundi" in French), capitalized regardless of the locale's own casing convention. */
private fun DayOfWeek.displayName(): String {
    val locale = Locale.getDefault()
    return getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleFormScreenAddPreview() {
    AeshAssistantTheme {
        ScheduleFormScreen(
            uiState =
                ScheduleFormUiState(
                    students =
                        listOf(
                            Student(id = 1L, firstName = "Alice", className = "CE2"),
                            Student(id = 2L, firstName = "Amir", className = "CM2"),
                        ),
                ),
            onDayOfWeekChanged = {},
            onStartTimeChanged = {},
            onEndTimeChanged = {},
            onSubjectChanged = {},
            onRoomChanged = {},
            onStudentToggled = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleFormScreenEditPreview() {
    AeshAssistantTheme {
        ScheduleFormScreen(
            uiState =
                ScheduleFormUiState(
                    scheduleSlotId = 1L,
                    dayOfWeek = DayOfWeek.TUESDAY,
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(11, 0),
                    subject = "Mathématiques",
                    room = "B12",
                    students =
                        listOf(
                            Student(id = 1L, firstName = "Alice", className = "CE2"),
                            Student(id = 2L, firstName = "Amir", className = "CM2"),
                        ),
                    selectedStudentIds = setOf(1L),
                ),
            onDayOfWeekChanged = {},
            onStartTimeChanged = {},
            onEndTimeChanged = {},
            onSubjectChanged = {},
            onRoomChanged = {},
            onStudentToggled = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}
