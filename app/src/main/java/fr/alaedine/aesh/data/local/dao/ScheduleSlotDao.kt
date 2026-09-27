package fr.alaedine.aesh.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity
import fr.alaedine.aesh.data.local.entity.ScheduleSlotStudentCrossRef
import fr.alaedine.aesh.data.local.entity.ScheduleSlotWithStudents
import kotlinx.coroutines.flow.Flow

/** Data Access Object for CRUD operations on [ScheduleSlotEntity] rows and their student assignments. */
@Dao
interface ScheduleSlotDao {
    /** Ordered by [ScheduleSlotEntity.dayOfWeek] (Monday first) then start time, matching how a weekly schedule reads. */
    @Query("SELECT * FROM schedule_slots ORDER BY dayOfWeek ASC, startTime ASC")
    fun observeAll(): Flow<List<ScheduleSlotEntity>>

    /**
     * Emits every slot joined with its assigned students (see
     * [ScheduleSlotWithStudents]), same ordering as [observeAll]. This is
     * what the rest of the app observes (see
     * [fr.alaedine.aesh.data.repository.ScheduleSlotRepositoryImpl]); plain
     * [observeAll] only remains for backup export, which serializes slots
     * and cross-refs as separate lists (see `BackupRepositoryImpl`).
     */
    @Transaction
    @Query("SELECT * FROM schedule_slots ORDER BY dayOfWeek ASC, startTime ASC")
    fun observeAllWithStudents(): Flow<List<ScheduleSlotWithStudents>>

    @Query("SELECT * FROM schedule_slots WHERE id = :id")
    suspend fun getById(id: Long): ScheduleSlotEntity?

    /** As [getById], but joined with the slot's assigned students (see [ScheduleSlotWithStudents]). */
    @Transaction
    @Query("SELECT * FROM schedule_slots WHERE id = :id")
    suspend fun getByIdWithStudents(id: Long): ScheduleSlotWithStudents?

    /** Inserts [scheduleSlot] and returns its generated row id. */
    @Insert
    suspend fun insert(scheduleSlot: ScheduleSlotEntity): Long

    /** Inserts every one of [scheduleSlots], preserving their ids; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Insert
    suspend fun insertAll(scheduleSlots: List<ScheduleSlotEntity>)

    @Update
    suspend fun update(scheduleSlot: ScheduleSlotEntity)

    /** Cascades to remove [scheduleSlot]'s student cross-ref rows (see [ScheduleSlotStudentCrossRef]'s foreign key). */
    @Delete
    suspend fun delete(scheduleSlot: ScheduleSlotEntity)

    /** Removes every row; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Query("DELETE FROM schedule_slots")
    suspend fun deleteAll()

    /** Raw cross-ref rows across every slot; used for backup export (see `BackupRepositoryImpl`). */
    @Query("SELECT * FROM schedule_slot_student_cross_ref")
    suspend fun getAllStudentCrossRefs(): List<ScheduleSlotStudentCrossRef>

    /**
     * Inserts every one of [crossRefs]; used both for a single slot's
     * assignments (see
     * [fr.alaedine.aesh.data.repository.ScheduleSlotRepositoryImpl]) and
     * when restoring a backup.
     */
    @Insert
    suspend fun insertStudentCrossRefs(crossRefs: List<ScheduleSlotStudentCrossRef>)

    /** Removes every cross-ref row for [scheduleSlotId], in preparation for replacing them with a new set of assignments. */
    @Query("DELETE FROM schedule_slot_student_cross_ref WHERE scheduleSlotId = :scheduleSlotId")
    suspend fun deleteStudentCrossRefsForSlot(scheduleSlotId: Long)

    /** Removes every cross-ref row; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Query("DELETE FROM schedule_slot_student_cross_ref")
    suspend fun deleteAllStudentCrossRefs()
}
