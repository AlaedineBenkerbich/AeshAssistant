package fr.alaedine.aesh.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation-layer state holder for the add/edit schedule slot form.
 *
 * Backs both flows with a single class: when [scheduleSlotId] is `null` it
 * behaves as an "add slot" form; otherwise it loads the matching slot from
 * [scheduleSlotRepository] on init and behaves as an "edit slot" form.
 * `presentationModule` supplies [scheduleSlotId] as a Koin injection
 * parameter sourced from the navigation argument, see [ScheduleFormRoute].
 */
class ScheduleFormViewModel(
    private val scheduleSlotRepository: ScheduleSlotRepository,
    private val scheduleSlotId: Long?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleFormUiState(scheduleSlotId = scheduleSlotId))
    val uiState: StateFlow<ScheduleFormUiState> = _uiState.asStateFlow()

    init {
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
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onDayOfWeekChanged(dayOfWeek: DayOfWeek) {
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

    /** Persists the current field values, adding a new slot or updating the existing one. */
    fun onSaveClicked() {
        val state = _uiState.value
        if (!state.canSave) return
        val scheduleSlot = ScheduleSlot(
            id = scheduleSlotId ?: 0L,
            dayOfWeek = state.dayOfWeek,
            startTime = state.startTime,
            endTime = state.endTime,
            subject = state.subject.trim(),
            room = state.room.trim(),
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
