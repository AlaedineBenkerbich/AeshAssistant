package fr.alaedine.aesh.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.ParsedScheduleSlot
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Presentation-layer state holder for the add/edit schedule slot form.
 *
 * Backs both flows with a single class: when [scheduleSlotId] is `null` it
 * behaves as an "add slot" form; otherwise it loads the matching slot from
 * [scheduleSlotRepository] on init and behaves as an "edit slot" form.
 * `presentationModule` supplies [scheduleSlotId] as a Koin injection
 * parameter sourced from the navigation argument, see [ScheduleFormRoute].
 *
 * Also keeps the student picker in sync with [studentRepository], since
 * every slot must be assigned at least one student (see
 * [ScheduleFormUiState.canSave]).
 *
 * @param prefill Fields recognized by the schedule photo scanner (see
 * `presentation.schedule.scanner`), applied on top of the default "add
 * slot" field values. Only meaningful when [scheduleSlotId] is `null`;
 * ignored otherwise, since an edited slot always loads its own values.
 */
class ScheduleFormViewModel(
    private val scheduleSlotRepository: ScheduleSlotRepository,
    private val studentRepository: StudentRepository,
    private val scheduleSlotId: Long?,
    prefill: ParsedScheduleSlot? = null,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            ScheduleFormUiState(scheduleSlotId = scheduleSlotId).withPrefill(prefill),
        )
    val uiState: StateFlow<ScheduleFormUiState> = _uiState.asStateFlow()

    init {
        studentRepository
            .observeStudents()
            .onEach { students -> _uiState.update { it.copy(students = students) } }
            .launchIn(viewModelScope)
        loadExistingScheduleSlot()
    }

    private fun loadExistingScheduleSlot() {
        val id = scheduleSlotId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val scheduleSlot = scheduleSlotRepository.getScheduleSlotById(id)
            _uiState.update { state ->
                if (scheduleSlot == null) {
                    state.copy(isLoading = false)
                } else {
                    state.copy(
                        dayOfWeek = scheduleSlot.dayOfWeek,
                        startTime = scheduleSlot.startTime,
                        endTime = scheduleSlot.endTime,
                        subject = scheduleSlot.subject,
                        room = scheduleSlot.room,
                        selectedStudentIds = scheduleSlot.studentIds.toSet(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    /** Updates the day of week, ignoring [DayOfWeek.SUNDAY] since the AESH doesn't work Sundays and a class can't be scheduled that day; [DayOfWeekDropdown] already excludes it from the options offered, this is a defensive fallback. */
    fun onDayOfWeekChanged(dayOfWeek: DayOfWeek) {
        if (dayOfWeek == DayOfWeek.SUNDAY) return
        _uiState.update { it.copy(dayOfWeek = dayOfWeek) }
    }

    fun onStartTimeChanged(startTime: LocalTime) {
        _uiState.update { it.copy(startTime = startTime) }
    }

    fun onEndTimeChanged(endTime: LocalTime) {
        _uiState.update { it.copy(endTime = endTime) }
    }

    fun onSubjectChanged(subject: String) {
        _uiState.update { it.copy(subject = subject) }
    }

    fun onRoomChanged(room: String) {
        _uiState.update { it.copy(room = room) }
    }

    /** Toggles whether the student identified by [studentId] is assigned to this slot. */
    fun onStudentToggled(studentId: Long) {
        _uiState.update { state ->
            val selectedStudentIds =
                if (studentId in state.selectedStudentIds) {
                    state.selectedStudentIds - studentId
                } else {
                    state.selectedStudentIds + studentId
                }
            state.copy(selectedStudentIds = selectedStudentIds)
        }
    }

    /** Persists the current field values, adding a new slot or updating the existing one. */
    fun onSaveClicked() {
        val state = _uiState.value
        if (!state.canSave) return
        val scheduleSlot =
            ScheduleSlot(
                id = scheduleSlotId ?: 0L,
                dayOfWeek = state.dayOfWeek,
                startTime = state.startTime,
                endTime = state.endTime,
                subject = state.subject.trim(),
                room = state.room.trim(),
                studentIds = state.selectedStudentIds.toList(),
            )
        viewModelScope.launch {
            if (scheduleSlotId == null) {
                scheduleSlotRepository.addScheduleSlot(scheduleSlot)
            } else {
                scheduleSlotRepository.updateScheduleSlot(scheduleSlot)
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}

/** Overlays every recognized (non-null) [prefill] field onto this state, leaving the rest at their defaults. */
private fun ScheduleFormUiState.withPrefill(prefill: ParsedScheduleSlot?): ScheduleFormUiState {
    if (prefill == null) return this
    return copy(
        // A scanned physical schedule could recognize "dimanche"/"sunday" text, but classes can't be scheduled
        // on Sundays (see ScheduleFormViewModel.onDayOfWeekChanged), so that particular recognized value is
        // dropped here and the default day of week is kept instead.
        dayOfWeek = prefill.dayOfWeek?.takeIf { it != DayOfWeek.SUNDAY } ?: dayOfWeek,
        startTime = prefill.startTime ?: startTime,
        endTime = prefill.endTime ?: endTime,
        subject = prefill.subject ?: subject,
        room = prefill.room ?: room,
    )
}
