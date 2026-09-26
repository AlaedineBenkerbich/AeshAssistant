package fr.alaedine.aesh.presentation.student

import fr.alaedine.aesh.domain.model.Student

/**
 * Immutable UI state rendered by [StudentListScreen].
 *
 * @property students Students to display, kept in sync with
 * [fr.alaedine.aesh.domain.repository.StudentRepository.observeStudents].
 * @property isLoading Whether the initial load from the repository is still
 * in flight; avoids flashing the empty-state message before the first
 * emission arrives.
 * @property pendingDeletion Student awaiting confirmation before deletion, or
 * `null` when no confirmation dialog should be shown.
 */
data class StudentListUiState(
    val students: List<Student> = emptyList(),
    val isLoading: Boolean = true,
    val pendingDeletion: Student? = null,
)
