package fr.alaedine.aesh.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * A single recurring weekly class slot, framework-agnostic so it can be
 * used throughout `domain` and `presentation` without leaking persistence
 * details (see [fr.alaedine.aesh.data.local.entity.ScheduleSlotEntity] for
 * the Room-mapped counterpart).
 *
 * @property id Unique identifier; `0` for a slot not yet persisted.
 * @property dayOfWeek The day this slot recurs on every week.
 * @property startTime The time the class starts.
 * @property endTime The time the class ends; expected to be after [startTime].
 * @property subject The class/subject name (e.g. "Mathématiques").
 * @property room Where the class takes place, if tracked.
 */
data class ScheduleSlot(
    val id: Long = 0L,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val room: String = "",
)
