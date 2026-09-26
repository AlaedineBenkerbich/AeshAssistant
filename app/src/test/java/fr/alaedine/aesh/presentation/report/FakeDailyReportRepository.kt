package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [DailyReportRepository] test double, avoiding the need for a
 * mocking library or a real Room database to unit test the daily report view
 * models.
 */
class FakeDailyReportRepository(
    initialReports: List<DailyReport> = emptyList(),
) : DailyReportRepository {

    private val reports = MutableStateFlow(initialReports)
    private var nextId = (initialReports.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeReports(): Flow<List<DailyReport>> = reports

    override fun observeReportsForStudent(studentId: Long): Flow<List<DailyReport>> =
        reports.map { list -> list.filter { it.studentId == studentId } }

    override suspend fun getReportById(id: Long): DailyReport? =
        reports.value.find { it.id == id }

    override suspend fun getReportByDateAndStudent(date: LocalDate, studentId: Long): DailyReport? =
        reports.value.find { it.date == date && it.studentId == studentId }

    override suspend fun addReport(report: DailyReport): Long {
        val id = nextId++
        reports.update { it + report.copy(id = id) }
        return id
    }

    override suspend fun updateReport(report: DailyReport) {
        reports.update { list -> list.map { if (it.id == report.id) report else it } }
    }

    override suspend fun deleteReport(report: DailyReport) {
        reports.update { list -> list.filterNot { it.id == report.id } }
    }
}
