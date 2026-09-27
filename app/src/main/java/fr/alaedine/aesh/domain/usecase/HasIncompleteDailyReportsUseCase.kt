package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Business rule of whether at least one student scheduled on a given date's
 * day of week is still missing a [fr.alaedine.aesh.domain.model.DailyReport]
 * for that date.
 *
 * This is the same rule [fr.alaedine.aesh.presentation.home.HomeViewModel]
 * uses to warn about missing reports on the dashboard — solely the schedule
 * determines which students need an observation on a given day, not the
 * full student roster; it's extracted here as a reusable, framework-agnostic
 * use case so [fr.alaedine.aesh.data.reminder.DailyReportReminderWorker] can
 * apply the exact same definition of "completed" when deciding whether to
 * fire the daily reminder notification.
 */
class HasIncompleteDailyReportsUseCase(
    private val dailyReportRepository: DailyReportRepository,
    private val scheduleSlotRepository: ScheduleSlotRepository,
) {
    /** Returns `false` when no student has a class scheduled on [date]'s day of week. */
    suspend operator fun invoke(date: LocalDate): Boolean {
        val scheduledStudentIds =
            scheduleSlotRepository
                .observeScheduleSlots()
                .first()
                .filter { it.dayOfWeek == date.dayOfWeek }
                .flatMapTo(mutableSetOf()) { it.studentIds }
        if (scheduledStudentIds.isEmpty()) return false

        val studentIdsWithReport =
            dailyReportRepository
                .observeReports()
                .first()
                .filter { it.date == date }
                .mapTo(mutableSetOf()) { it.studentId }

        return scheduledStudentIds.any { it !in studentIdsWithReport }
    }
}
