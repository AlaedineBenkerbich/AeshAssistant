package fr.alaedine.aesh.presentation.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.alaedine.aesh.R
import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.presentation.theme.AeshAssistantTheme
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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
        onSaveClicked = viewModel::onSaveClicked,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFormScreen(
    uiState: ScheduleFormUiState,
    onDayOfWeekChanged: (DayOfWeek) -> Unit,
    onStartTimeChanged: (LocalTime) -> Unit,
    onEndTimeChanged: (LocalTime) -> Unit,
    onSubjectChanged: (String) -> Unit,
    onRoomChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (uiState.isEditing) R.string.schedule_form_title_edit else R.string.schedule_form_title_add,
                        ),
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
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DayOfWeekDropdown(
                selectedDayOfWeek = uiState.dayOfWeek,
                onDayOfWeekSelected = onDayOfWeekChanged,
            )
            TimeField(
                label = stringResource(R.string.schedule_field_start_time),
                time = uiState.startTime,
                onTimeSelected = onStartTimeChanged,
            )
            TimeField(
                label = stringResource(R.string.schedule_field_end_time),
                time = uiState.endTime,
                onTimeSelected = onEndTimeChanged,
            )
            OutlinedTextField(
                value = uiState.subject,
                onValueChange = onSubjectChanged,
                label = { Text(text = stringResource(R.string.schedule_field_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.room,
                onValueChange = onRoomChanged,
                label = { Text(text = stringResource(R.string.schedule_field_room)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSaveClicked,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        }
    }
}

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
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            DayOfWeek.entries.forEach { dayOfWeek ->
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
private fun TimeField(label: String, time: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
    var isDialogVisible by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = time.format(TIME_FORMATTER),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { isDialogVisible = true },
        )
    }
    if (isDialogVisible) {
        val timePickerState = rememberTimePickerState(
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

private fun DayOfWeek.displayName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

@Preview(showBackground = true)
@Composable
private fun ScheduleFormScreenAddPreview() {
    AeshAssistantTheme {
        ScheduleFormScreen(
            uiState = ScheduleFormUiState(),
            onDayOfWeekChanged = {},
            onStartTimeChanged = {},
            onEndTimeChanged = {},
            onSubjectChanged = {},
            onRoomChanged = {},
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
            uiState = ScheduleFormUiState(
                scheduleSlotId = 1L,
                dayOfWeek = DayOfWeek.TUESDAY,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(11, 0),
                subject = "Mathématiques",
                room = "B12",
            ),
            onDayOfWeekChanged = {},
            onStartTimeChanged = {},
            onEndTimeChanged = {},
            onSubjectChanged = {},
            onRoomChanged = {},
            onSaveClicked = {},
            onNavigateBack = {},
        )
    }
}
