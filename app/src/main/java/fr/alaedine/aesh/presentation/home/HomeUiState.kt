package fr.alaedine.aesh.presentation.home

import fr.alaedine.aesh.domain.model.Student
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Immutable UI state rendered by [HomeScreen].
 *
 * @property date Today's date, displayed as the dashboard's day summary.
 * @property lessonBlocks Today's [fr.alaedine.aesh.domain.model.ScheduleSlot]s,
 * ordered by start time, each resolved into a [LessonBlock] pairing it with
 * the completion status of every student assigned to it. Solely the
 * schedule — not the full student roster — determines which students need
 * an observation today, kept in sync with
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository.observeScheduleSlots],
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents] and
 * [fr.alaedine.aesh.domain.repository.DailyReportRepository.observeReports]
 * by [HomeViewModel]. A student who isn't scheduled today can still get an
 * observation logged manually via the dashboard's FAB, which opens
 * [fr.alaedine.aesh.presentation.report.DailyReportFormScreen] without
 * preselecting anyone, letting any known student be picked regardless of
 * [lessonBlocks].
 * @property daysWithScheduledClasses Which days of the week have at least
 * one [fr.alaedine.aesh.domain.model.ScheduleSlot], kept in sync with
 * [fr.alaedine.aesh.domain.repository.ScheduleSlotRepository.observeScheduleSlots]
 * by [HomeViewModel]. Drives the small event dot under those days in the
 * dashboard's calendar week strip.
 * @property isLoading Whether the schedule/students/reports are still being
 * loaded; avoids briefly flashing the "no classes today" empty state or the
 * "missing report" warning before the first emission arrives.
 */
data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val lessonBlocks: List<LessonBlock> = emptyList(),
    val daysWithScheduledClasses: Set<DayOfWeek> = emptySet(),
    val isLoading: Boolean = true,
) {
    /** Every student scheduled at least once today, deduplicated across [lessonBlocks] so back-to-back classes don't double-count them. */
    private val distinctScheduledStudentStatuses: List<StudentReportStatus>
        get() = lessonBlocks.flatMap { it.studentStatuses }.distinctBy { it.student.id }

    /** How many students scheduled today still don't have a report for [date]. */
    val missingReportCount: Int get() = distinctScheduledStudentStatuses.count { !it.hasReportToday }

    /** Whether the dashboard should warn the user: at least one student scheduled today is missing their report. */
    val hasMissingReports: Boolean get() = !isLoading && missingReportCount > 0
}

/**
 * One of today's [fr.alaedine.aesh.domain.model.ScheduleSlot]s, resolved for
 * display: its time range/subject/room, and every assigned student paired
 * with whether they already have a report for today (see
 * [StudentReportStatus]). Segmenting the dashboard's student list this way
 * lets the AESH move lesson by lesson through their day rather than
 * scanning one flat roster.
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

/** Pairs a [Student] with whether they already have a daily report for the dashboard's current [HomeUiState.date]. */
data class StudentReportStatus(
    val student: Student,
    val hasReportToday: Boolean,
)
