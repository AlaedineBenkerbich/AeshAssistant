package fr.alaedine.aesh.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import fr.alaedine.aesh.data.local.entity.DailyReportEntity
import fr.alaedine.aesh.data.local.entity.DailyReportWithStudent
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Data Access Object for CRUD operations on [DailyReportEntity] rows. */
@Dao
interface DailyReportDao {
    @Query("SELECT * FROM daily_reports ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyReportEntity>>

    @Query("SELECT * FROM daily_reports WHERE studentId = :studentId ORDER BY date DESC")
    fun observeForStudent(studentId: Long): Flow<List<DailyReportEntity>>

    /** Emits every report joined with its associated student (see [DailyReportWithStudent]). */
    @Transaction
    @Query("SELECT * FROM daily_reports ORDER BY date DESC")
    fun observeAllWithStudent(): Flow<List<DailyReportWithStudent>>

    @Query("SELECT * FROM daily_reports WHERE id = :id")
    suspend fun getById(id: Long): DailyReportEntity?

    @Query("SELECT * FROM daily_reports WHERE date = :date AND studentId = :studentId")
    suspend fun getByDateAndStudent(
        date: LocalDate,
        studentId: Long,
    ): DailyReportEntity?

    /** Inserts [dailyReport] and returns its generated row id. */
    @Insert
    suspend fun insert(dailyReport: DailyReportEntity): Long

    /** Inserts every one of [dailyReports], preserving their ids; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Insert
    suspend fun insertAll(dailyReports: List<DailyReportEntity>)

    @Update
    suspend fun update(dailyReport: DailyReportEntity)

    @Delete
    suspend fun delete(dailyReport: DailyReportEntity)

    /** Removes every row; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Query("DELETE FROM daily_reports")
    suspend fun deleteAll()
}
