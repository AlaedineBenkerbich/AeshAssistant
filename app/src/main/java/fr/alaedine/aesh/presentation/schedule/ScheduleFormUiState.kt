package fr.alaedine.aesh.presentation.schedule

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Immutable UI state rendered by [ScheduleFormScreen], shared by both the
 * add- and edit-schedule-slot flows.
 *
 * @property scheduleSlotId `null` while creating a new slot, or the id of
 * the slot being edited.
 * @property dayOfWeek Editable day-of-week field.
 * @property startTime Editable class start time field.
 * @property endTime Editable class end time field.
 * @property subject Editable subject/class name field.
 * @property room Editable room field.
 * @property isLoading Whether the existing slot is still being loaded (edit
 * flow only); avoids briefly showing default field values as if they were
 * genuinely chosen before the load completes.
 * @property isSaved Whether the current fields have just been persisted,
 * signalling [ScheduleFormRoute] to navigate back.
 */
data class ScheduleFormUiState(
    val scheduleSlotId: Long? = null,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val startTime: LocalTime = LocalTime.of(8, 0),
    val endTime: LocalTime = LocalTime.of(9, 0),
    val subject: String = "",
    val room: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
) {
    /** Whether this state represents editing an existing slot rather than adding a new one. */
    val isEditing: Boolean get() = scheduleSlotId != null

    /** Whether the required fields are filled in and valid so the form can be submitted. */
    val canSave: Boolean get() = subject.isNotBlank() && endTime.isAfter(startTime)
}
