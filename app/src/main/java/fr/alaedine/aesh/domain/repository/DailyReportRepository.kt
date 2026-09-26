package fr.alaedine.aesh.domain.repository

import fr.alaedine.aesh.domain.model.DailyReport
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Framework-agnostic contract for persisting and retrieving [DailyReport]s.
 *
 * Implemented by [fr.alaedine.aesh.data.repository.DailyReportRepositoryImpl]
 * on top of Room. Presentation-layer view models (and future domain use
 * cases) depend on this interface only, never on the `data` layer directly,
 * keeping the Clean Architecture dependency rule (dependencies always point
 * inward) intact.
 */
interface DailyReportRepository {

    /** Emits the current list of reports every time the underlying data changes. */
    fun observeReports(): Flow<List<DailyReport>>

    /** Emits the reports for [studentId] every time the underlying data changes. */
    fun observeReportsForStudent(studentId: Long): Flow<List<DailyReport>>

    /** Returns the report with [id], or `null` if none exists. */
    suspend fun getReportById(id: Long): DailyReport?

    /** Returns the report filled out for [studentId] on [date], or `null` if none exists. */
    suspend fun getReportByDateAndStudent(date: LocalDate, studentId: Long): DailyReport?

    /** Persists a new [report] and returns its generated id. */
    suspend fun addReport(report: DailyReport): Long

    /** Persists changes to an existing [report]. */
    suspend fun updateReport(report: DailyReport)

    /** Removes [report] from persistence. */
    suspend fun deleteReport(report: DailyReport)
}
