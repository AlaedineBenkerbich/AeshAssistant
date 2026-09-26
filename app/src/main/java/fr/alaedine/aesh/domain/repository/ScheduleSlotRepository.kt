package fr.alaedine.aesh.domain.repository

import fr.alaedine.aesh.domain.model.ScheduleSlot
import kotlinx.coroutines.flow.Flow

/**
 * Framework-agnostic contract for persisting and retrieving [ScheduleSlot]s.
 *
 * Implemented by
 * [fr.alaedine.aesh.data.repository.ScheduleSlotRepositoryImpl] on top of
 * Room. Presentation-layer view models (and future domain use cases) depend
 * on this interface only, never on the `data` layer directly, keeping the
 * Clean Architecture dependency rule (dependencies always point inward)
 * intact.
 */
interface ScheduleSlotRepository {

    /** Emits the current weekly schedule every time the underlying data changes. */
    fun observeScheduleSlots(): Flow<List<ScheduleSlot>>

    /** Returns the schedule slot with [id], or `null` if none exists. */
    suspend fun getScheduleSlotById(id: Long): ScheduleSlot?

    /** Persists a new [scheduleSlot] and returns its generated id. */
    suspend fun addScheduleSlot(scheduleSlot: ScheduleSlot): Long

    /** Persists changes to an existing schedule slot. */
    suspend fun updateScheduleSlot(scheduleSlot: ScheduleSlot)

    /** Removes [scheduleSlot] from persistence. */
    suspend fun deleteScheduleSlot(scheduleSlot: ScheduleSlot)
}
