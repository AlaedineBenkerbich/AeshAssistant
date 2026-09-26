package fr.alaedine.aesh.presentation.student

/**
 * Immutable UI state rendered by [StudentFormScreen], shared by both the
 * add- and edit-student flows.
 *
 * @property studentId `null` while creating a new student, or the id of the
 * student being edited.
 * @property firstName Editable first name field.
 * @property className Editable class/group field.
 * @property ppsGoals Editable PPS goals field.
 * @property isLoading Whether the existing student is still being loaded
 * (edit flow only); avoids briefly showing blank fields as if they were
 * genuinely empty before the load completes.
 * @property isSaved Whether the current fields have just been persisted,
 * signalling [StudentFormRoute] to navigate back.
 */
data class StudentFormUiState(
    val studentId: Long? = null,
    val firstName: String = "",
    val className: String = "",
    val ppsGoals: String = "",
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
) {
    /** Whether this state represents editing an existing student rather than adding a new one. */
    val isEditing: Boolean get() = studentId != null

    /** Whether the required fields are filled in and the form can be submitted. */
    val canSave: Boolean get() = firstName.isNotBlank() && className.isNotBlank()
}
