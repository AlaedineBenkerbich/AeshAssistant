package fr.alaedine.aesh.presentation.home

import fr.alaedine.aesh.domain.model.Student
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Immutable UI state rendered by [HomeScreen].
 *
 * @property date The date currently displayed on the dashboard. Defaults to
 * [today] but can be changed to any day — not just the present — via
 * [HomeViewModel.onDateSelected], [HomeViewModel.onPreviousWeekClicked] and
 * [HomeViewModel.onNextWeekClicked], letting the AESH freely browse the
 * calendar instead of being stuck looking at the present day.
 * @property today The real current date, independent of [date]; lets the
 * dashboard's calendar week strip distinguish "today" from whichever day is
 * currently selected, even once they differ.
 * @property lessonBlocks [date]'s [fr.alaedine.aesh.domain.model.ScheduleSlot]s,
 * ordered by start time, each resolved into a [LessonBlock] pairing it with
 * the completion status of every student assigned to it. Solely the
 * schedule — not the full student roster — determines which students need
 * an observation on [date], kept in sync with
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository.observeScheduleSlots],
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents] and
 * [fr.alaedine.aesh.domain.repository.DailyReportRepository.observeReports]
 * by [HomeViewModel]. A student who isn't scheduled on [date] can still get
 * an observation logged manually via the dashboard's FAB, which opens
 * [fr.alaedine.aesh.presentation.report.DailyReportFormScreen] without
 * preselecting anyone, letting any known student be picked regardless of
 * [lessonBlocks].
 * @property daysWithScheduledClasses Which days of the week have at least
 * one [fr.alaedine.aesh.domain.model.ScheduleSlot], kept in sync with
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository.observeScheduleSlots]
 * by [HomeViewModel]. Drives the small event dot under those days in the
 * dashboard's calendar week strip.
 * @property isLoading Whether the schedule/students/reports are still being
 * loaded; avoids briefly flashing the "no classes" empty state or the
 * "missing report" warning before the first emission arrives.
 */
data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val lessonBlocks: List<LessonBlock> = emptyList(),
    val daysWithScheduledClasses: Set<DayOfWeek> = emptySet(),
    val isLoading: Boolean = true,
) {
    /** Every student scheduled at least once on [date], deduplicated across [lessonBlocks] so back-to-back classes don't double-count them. */
    private val distinctScheduledStudentStatuses: List<StudentReportStatus>
        get() = lessonBlocks.flatMap { it.studentStatuses }.distinctBy { it.student.id }

    /** How many students scheduled on [date] still don't have a report for it. */
    val missingReportCount: Int get() = distinctScheduledStudentStatuses.count { !it.hasReport }

    /** Whether the dashboard should warn the user: at least one student scheduled on [date] is missing their report. */
    val hasMissingReports: Boolean get() = !isLoading && missingReportCount > 0
}

/**
 * One of the selected day's [fr.alaedine.aesh.domain.model.ScheduleSlot]s,
 * resolved for display: its time range/subject/room, and every assigned
 * student paired with whether they already have a report for that day (see
 * [StudentReportStatus]). Segmenting the dashboard's student list this way
 * lets the AESH move lesson by lesson through the day rather than scanning
 * one flat roster.
 *
 * @property scheduleSlotId The originating [fr.alaedine.aesh.domain.model.ScheduleSlot.id].
 */
data class LessonBlock(
    val scheduleSlotId: Long,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val room: String,
    val studentStatuses: List<StudentReportStatus>,
)

/** Pairs a [Student] with whether they already have a daily report for the dashboard's currently selected [HomeUiState.date]. */
data class StudentReportStatus(
    val student: Student,
    val hasReport: Boolean,
)
