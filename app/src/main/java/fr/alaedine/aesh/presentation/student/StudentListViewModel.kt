package fr.alaedine.aesh.presentation.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation-layer state holder for the student list screen.
 *
 * Keeps [uiState] in sync with [studentRepository] and drives the
 * delete-confirmation flow: [onDeleteRequested] stages a student for
 * removal so [StudentListScreen] can show a confirmation dialog, and the
 * actual deletion only happens once [onDeleteConfirmed] is called.
 */
class StudentListViewModel(
    private val studentRepository: StudentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudentListUiState())
    val uiState: StateFlow<StudentListUiState> = _uiState.asStateFlow()

    init {
        studentRepository
            .observeStudents()
            .onEach { students -> _uiState.update { it.copy(students = students, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** Stages [student] for deletion, prompting [StudentListScreen] to show a confirmation dialog. */
    fun onDeleteRequested(student: Student) {
        _uiState.update { it.copy(pendingDeletion = student) }
    }

    /** Dismisses the confirmation dialog without deleting anything. */
    fun onDeleteCancelled() {
        _uiState.update { it.copy(pendingDeletion = null) }
    }

    /** Deletes the student staged by [onDeleteRequested] and dismisses the confirmation dialog. */
    fun onDeleteConfirmed() {
        val student = _uiState.value.pendingDeletion ?: return
        viewModelScope.launch {
            studentRepository.deleteStudent(student)
            _uiState.update { it.copy(pendingDeletion = null) }
        }
    }
}
