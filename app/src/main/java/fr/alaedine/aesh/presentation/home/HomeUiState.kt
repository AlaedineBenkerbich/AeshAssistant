package fr.alaedine.aesh.presentation.home

import fr.alaedine.aesh.domain.model.Student
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Immutable UI state rendered by [HomeScreen].
 *
 * @property date Today's date, displayed as the dashboard's day summary.
 * @property studentStatuses One entry per known student, pairing them with
 * whether they already have a [fr.alaedine.aesh.domain.model.DailyReport]
 * for [date] (see [StudentReportStatus]), kept in sync with
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents] and
 * [fr.alaedine.aesh.domain.repository.DailyReportRepository.observeReports]
 * by [HomeViewModel].
 * @property daysWithScheduledClasses Which days of the week have at least
 * one [fr.alaedine.aesh.domain.model.ScheduleSlot], kept in sync with
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository.observeScheduleSlots]
 * by [HomeViewModel]. Drives the small event dot under those days in the
 * dashboard's calendar week strip.
 * @property isLoading Whether students/reports are still being loaded;
 * avoids briefly flashing the "missing report" warning before the first
 * emission arrives.
 */
data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val studentStatuses: List<StudentReportStatus> = emptyList(),
    val daysWithScheduledClasses: Set<DayOfWeek> = emptySet(),
    val isLoading: Boolean = true,
) {
    /** How many students still don't have a report for [date]. */
    val missingReportCount: Int get() = studentStatuses.count { !it.hasReportToday }

    /** Whether the dashboard should warn the user: at least one student is missing today's report. */
    val hasMissingReports: Boolean get() = !isLoading && missingReportCount > 0
}

/** Pairs a [Student] with whether they already have a daily report for the dashboard's current [HomeUiState.date]. */
data class StudentReportStatus(
    val student: Student,
    val hasReportToday: Boolean,
)
