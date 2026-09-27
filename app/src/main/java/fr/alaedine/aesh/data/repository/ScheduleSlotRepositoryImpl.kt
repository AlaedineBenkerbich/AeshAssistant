package fr.alaedine.aesh.data.repository

import fr.alaedine.aesh.data.local.dao.ScheduleSlotDao
import fr.alaedine.aesh.data.local.entity.toDomain
import fr.alaedine.aesh.data.local.entity.toEntity
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** [ScheduleSlotRepository] backed by Room via [scheduleSlotDao]. */
class ScheduleSlotRepositoryImpl(
    private val scheduleSlotDao: ScheduleSlotDao,
) : ScheduleSlotRepository {
    override fun observeScheduleSlots(): Flow<List<ScheduleSlot>> =
        scheduleSlotDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getScheduleSlotById(id: Long): ScheduleSlot? = scheduleSlotDao.getById(id)?.toDomain()

    override suspend fun addScheduleSlot(scheduleSlot: ScheduleSlot): Long = scheduleSlotDao.insert(scheduleSlot.toEntity())

    override suspend fun updateScheduleSlot(scheduleSlot: ScheduleSlot) {
        scheduleSlotDao.update(scheduleSlot.toEntity())
    }

    override suspend fun deleteScheduleSlot(scheduleSlot: ScheduleSlot) {
        scheduleSlotDao.delete(scheduleSlot.toEntity())
    }
}
