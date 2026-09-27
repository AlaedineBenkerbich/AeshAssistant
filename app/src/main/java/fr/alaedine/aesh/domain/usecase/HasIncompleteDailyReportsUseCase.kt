package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Business rule of whether at least one known student is still missing a
 * [fr.alaedine.aesh.domain.model.DailyReport] for a given date.
 *
 * This is the same rule [fr.alaedine.aesh.presentation.home.HomeViewModel]
 * uses to warn about missing reports on the dashboard; it's extracted here
 * as a reusable, framework-agnostic use case so
 * [fr.alaedine.aesh.data.reminder.DailyReportReminderWorker] can apply the
 * exact same definition of "completed" when deciding whether to fire the
 * daily reminder notification.
 */
class HasIncompleteDailyReportsUseCase(
    private val studentRepository: StudentRepository,
    private val dailyReportRepository: DailyReportRepository,
) {
    /** Returns `false` when there are no students to report on. */
    suspend operator fun invoke(date: LocalDate): Boolean {
        val students = studentRepository.observeStudents().first()
        if (students.isEmpty()) return false

        val studentIdsWithReport =
            dailyReportRepository
                .observeReports()
                .first()
                .filter { it.date == date }
                .mapTo(mutableSetOf()) { it.studentId }

        return students.any { it.id !in studentIdsWithReport }
    }
}
