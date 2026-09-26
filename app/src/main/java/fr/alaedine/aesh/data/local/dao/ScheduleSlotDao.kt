package fr.alaedine.aesh.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity
import kotlinx.coroutines.flow.Flow

/** Data Access Object for CRUD operations on [ScheduleSlotEntity] rows. */
@Dao
interface ScheduleSlotDao {

    /** Ordered by [ScheduleSlotEntity.dayOfWeek] (Monday first) then start time, matching how a weekly schedule reads. */
    @Query("SELECT * FROM schedule_slots ORDER BY dayOfWeek ASC, startTime ASC")
    fun observeAll(): Flow<List<ScheduleSlotEntity>>

    @Query("SELECT * FROM schedule_slots WHERE id = :id")
    suspend fun getById(id: Long): ScheduleSlotEntity?

    /** Inserts [scheduleSlot] and returns its generated row id. */
    @Insert
    suspend fun insert(scheduleSlot: ScheduleSlotEntity): Long

    /** Inserts every one of [scheduleSlots], preserving their ids; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Insert
    suspend fun insertAll(scheduleSlots: List<ScheduleSlotEntity>)

    @Update
    suspend fun update(scheduleSlot: ScheduleSlotEntity)

    @Delete
    suspend fun delete(scheduleSlot: ScheduleSlotEntity)

    /** Removes every row; used when restoring a backup (see `BackupRepositoryImpl`). */
    @Query("DELETE FROM schedule_slots")
    suspend fun deleteAll()
}
