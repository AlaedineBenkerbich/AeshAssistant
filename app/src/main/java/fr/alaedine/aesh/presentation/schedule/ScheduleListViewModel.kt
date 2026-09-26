package fr.alaedine.aesh.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation-layer state holder for the weekly schedule list screen.
 *
 * Keeps [uiState] in sync with [scheduleSlotRepository] and drives the
 * delete-confirmation flow: [onDeleteRequested] stages a slot for removal
 * so [ScheduleListScreen] can show a confirmation dialog, and the actual
 * deletion only happens once [onDeleteConfirmed] is called.
 */
class ScheduleListViewModel(
    private val scheduleSlotRepository: ScheduleSlotRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleListUiState())
    val uiState: StateFlow<ScheduleListUiState> = _uiState.asStateFlow()

    init {
        scheduleSlotRepository.observeScheduleSlots()
            .onEach { scheduleSlots -> _uiState.update { it.copy(scheduleSlots = scheduleSlots, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** Stages [scheduleSlot] for deletion, prompting [ScheduleListScreen] to show a confirmation dialog. */
    fun onDeleteRequested(scheduleSlot: ScheduleSlot) {
        _uiState.update { it.copy(pendingDeletion = scheduleSlot) }
    }

    /** Dismisses the confirmation dialog without deleting anything. */
    fun onDeleteCancelled() {
        _uiState.update { it.copy(pendingDeletion = null) }
    }

    /** Deletes the schedule slot staged by [onDeleteRequested] and dismisses the confirmation dialog. */
    fun onDeleteConfirmed() {
        val scheduleSlot = _uiState.value.pendingDeletion ?: return
        viewModelScope.launch {
            scheduleSlotRepository.deleteScheduleSlot(scheduleSlot)
            _uiState.update { it.copy(pendingDeletion = null) }
        }
    }
}
