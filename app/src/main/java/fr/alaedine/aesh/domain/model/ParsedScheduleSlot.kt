package fr.alaedine.aesh.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Best-effort [ScheduleSlot] fields recognized from a photo of a physical
 * schedule by [fr.alaedine.aesh.domain.scanner.ScheduleTextParser].
 *
 * Every field is independently nullable: OCR text is noisy and physical
 * schedules vary wildly in layout, so each field is only populated when
 * confidently recognized. `null` fields are simply left at their normal
 * default on the pre-filled [fr.alaedine.aesh.presentation.schedule.ScheduleFormScreen]
 * for the user to fill in manually.
 */
data class ParsedScheduleSlot(
    val dayOfWeek: DayOfWeek? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val subject: String? = null,
    val room: String? = null,
) {
    /** Whether nothing at all could be recognized from the scanned photo. */
    val isEmpty: Boolean
        get() = dayOfWeek == null && startTime == null && endTime == null && subject == null && room == null
}
