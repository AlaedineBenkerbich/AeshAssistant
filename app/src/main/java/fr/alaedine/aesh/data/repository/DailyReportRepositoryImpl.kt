package fr.alaedine.aesh.data.repository

import fr.alaedine.aesh.data.local.dao.DailyReportDao
import fr.alaedine.aesh.data.local.entity.toDomain
import fr.alaedine.aesh.data.local.entity.toEntity
import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [DailyReportRepository] backed by Room via [dailyReportDao]. */
class DailyReportRepositoryImpl(
    private val dailyReportDao: DailyReportDao,
) : DailyReportRepository {

    override fun observeReports(): Flow<List<DailyReport>> =
        dailyReportDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeReportsForStudent(studentId: Long): Flow<List<DailyReport>> =
        dailyReportDao.observeForStudent(studentId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getReportById(id: Long): DailyReport? =
        dailyReportDao.getById(id)?.toDomain()

    override suspend fun getReportByDateAndStudent(date: LocalDate, studentId: Long): DailyReport? =
        dailyReportDao.getByDateAndStudent(date, studentId)?.toDomain()

    override suspend fun addReport(report: DailyReport): Long =
        dailyReportDao.insert(report.toEntity())

    override suspend fun updateReport(report: DailyReport) {
        dailyReportDao.update(report.toEntity())
    }

    override suspend fun deleteReport(report: DailyReport) {
        dailyReportDao.delete(report.toEntity())
    }
}
