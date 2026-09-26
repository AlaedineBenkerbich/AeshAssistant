package fr.alaedine.aesh.presentation.schedule

import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [ScheduleSlotRepository] test double, avoiding the need for a
 * mocking library or a real Room database to unit test the schedule view
 * models.
 */
class FakeScheduleSlotRepository(
    initialScheduleSlots: List<ScheduleSlot> = emptyList(),
) : ScheduleSlotRepository {

    private val scheduleSlots = MutableStateFlow(initialScheduleSlots)
    private var nextId = (initialScheduleSlots.maxOfOrNull { it.id } ?: 0L) + 1

    override fun observeScheduleSlots(): Flow<List<ScheduleSlot>> = scheduleSlots

    override suspend fun getScheduleSlotById(id: Long): ScheduleSlot? =
        scheduleSlots.value.find { it.id == id }

    override suspend fun addScheduleSlot(scheduleSlot: ScheduleSlot): Long {
        val id = nextId++
        scheduleSlots.update { it + scheduleSlot.copy(id = id) }
        return id
    }

    override suspend fun updateScheduleSlot(scheduleSlot: ScheduleSlot) {
        scheduleSlots.update { list -> list.map { if (it.id == scheduleSlot.id) scheduleSlot else it } }
    }

    override suspend fun deleteScheduleSlot(scheduleSlot: ScheduleSlot) {
        scheduleSlots.update { list -> list.filterNot { it.id == scheduleSlot.id } }
    }
}
