package fr.alaedine.aesh.presentation.schedule

import fr.alaedine.aesh.domain.model.ScheduleSlot

/**
 * Immutable UI state rendered by [ScheduleListScreen].
 *
 * @property scheduleSlots Weekly class slots to display, kept in sync with
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository.observeScheduleSlots]
 * and already ordered Monday-first by day then start time.
 * @property isLoading Whether the initial load from the repository is still
 * in flight; avoids flashing the empty-state message before the first
 * emission arrives.
 * @property pendingDeletion Schedule slot awaiting confirmation before
 * deletion, or `null` when no confirmation dialog should be shown.
 */
data class ScheduleListUiState(
    val scheduleSlots: List<ScheduleSlot> = emptyList(),
    val isLoading: Boolean = true,
    val pendingDeletion: ScheduleSlot? = null,
)
