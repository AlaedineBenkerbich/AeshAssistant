package fr.alaedine.aesh.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Room-persisted representation of a weekly recurring class slot. Kept
 * separate from [fr.alaedine.aesh.domain.model.ScheduleSlot] so persistence
 * annotations never leak into the domain layer (see [toDomain]/[toEntity]
 * for the mapping).
 */
@Entity(tableName = "schedule_slots")
data class ScheduleSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val room: String = "",
)
