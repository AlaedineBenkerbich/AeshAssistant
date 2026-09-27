package fr.alaedine.aesh.data.repository

import androidx.room.withTransaction
import fr.alaedine.aesh.data.local.AeshDatabase
import fr.alaedine.aesh.data.local.dao.ScheduleSlotDao
import fr.alaedine.aesh.data.local.entity.ScheduleSlotStudentCrossRef
import fr.alaedine.aesh.data.local.entity.toDomain
import fr.alaedine.aesh.data.local.entity.toEntity
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [ScheduleSlotRepository] backed by Room via [scheduleSlotDao].
 *
 * Needs [database] itself (in addition to [scheduleSlotDao]) to wrap each
 * add/update in [withTransaction]: persisting a slot's row and its student
 * cross-ref rows (see [ScheduleSlotStudentCrossRef]) is two separate writes,
 * and the transaction ensures a failure between them never leaves a slot
 * with a stale or missing set of assigned students.
 */
class ScheduleSlotRepositoryImpl(
    private val database: AeshDatabase,
    private val scheduleSlotDao: ScheduleSlotDao,
) : ScheduleSlotRepository {
    override fun observeScheduleSlots(): Flow<List<ScheduleSlot>> =
        scheduleSlotDao.observeAllWithStudents().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getScheduleSlotById(id: Long): ScheduleSlot? = scheduleSlotDao.getByIdWithStudents(id)?.toDomain()

    override suspend fun addScheduleSlot(scheduleSlot: ScheduleSlot): Long =
        database.withTransaction {
            val id = scheduleSlotDao.insert(scheduleSlot.toEntity())
            scheduleSlotDao.insertStudentCrossRefs(scheduleSlot.studentIds.toCrossRefs(scheduleSlotId = id))
            id
        }

    override suspend fun updateScheduleSlot(scheduleSlot: ScheduleSlot) {
        database.withTransaction {
            scheduleSlotDao.update(scheduleSlot.toEntity())
            scheduleSlotDao.deleteStudentCrossRefsForSlot(scheduleSlot.id)
            scheduleSlotDao.insertStudentCrossRefs(scheduleSlot.studentIds.toCrossRefs(scheduleSlotId = scheduleSlot.id))
        }
    }

    override suspend fun deleteScheduleSlot(scheduleSlot: ScheduleSlot) {
        // No explicit cross-ref cleanup needed: the foreign key cascades (see ScheduleSlotStudentCrossRef).
        scheduleSlotDao.delete(scheduleSlot.toEntity())
    }
}

private fun List<Long>.toCrossRefs(scheduleSlotId: Long): List<ScheduleSlotStudentCrossRef> =
    map { studentId -> ScheduleSlotStudentCrossRef(scheduleSlotId = scheduleSlotId, studentId = studentId) }
