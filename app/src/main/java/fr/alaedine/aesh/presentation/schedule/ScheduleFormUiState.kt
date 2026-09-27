package fr.alaedine.aesh.presentation.schedule

import fr.alaedine.aesh.domain.model.Student
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
 * @property students Students to choose from when assigning this slot, kept
 * in sync with
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents].
 * @property selectedStudentIds [Student.id]s currently assigned to this
 * slot; at least one is required to save (see [canSave]) so every class
 * always has a clear answer to "who is this observation for".
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
    val students: List<Student> = emptyList(),
    val selectedStudentIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
) {
    /** Whether this state represents editing an existing slot rather than adding a new one. */
    val isEditing: Boolean get() = scheduleSlotId != null

    /** Whether the required fields are filled in and valid so the form can be submitted. */
    val canSave: Boolean get() = subject.isNotBlank() && endTime.isAfter(startTime) && selectedStudentIds.isNotEmpty()
}
