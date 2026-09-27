package fr.alaedine.aesh.presentation.schedule

import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.model.Student
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Immutable UI state rendered by [ScheduleListScreen].
 *
 * @property scheduleSlots Weekly class slots to display, kept in sync with
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository.observeScheduleSlots]
 * and already ordered Monday-first by day then start time.
 * @property studentsById Every known student keyed by [Student.id], kept in
 * sync with
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents];
 * used to resolve each slot's [ScheduleSlot.studentIds] into display names.
 * @property today Today's day of the week; highlights the matching column in
 * the screen's calendar-style weekly grid, the same way
 * [fr.alaedine.aesh.presentation.home.HomeUiState.date] highlights today in
 * the dashboard's week strip.
 * @property isLoading Whether the initial load from the repository is still
 * in flight; avoids flashing the empty-state message before the first
 * emission arrives.
 * @property pendingDeletion Schedule slot awaiting confirmation before
 * deletion, or `null` when no confirmation dialog should be shown.
 */
data class ScheduleListUiState(
    val scheduleSlots: List<ScheduleSlot> = emptyList(),
    val studentsById: Map<Long, Student> = emptyMap(),
    val today: DayOfWeek = LocalDate.now().dayOfWeek,
    val isLoading: Boolean = true,
    val pendingDeletion: ScheduleSlot? = null,
)
