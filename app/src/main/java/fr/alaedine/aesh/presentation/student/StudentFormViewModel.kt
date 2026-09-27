package fr.alaedine.aesh.presentation.student

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation-layer state holder for the add/edit student form.
 *
 * Backs both flows with a single class: when [studentId] is `null` it
 * behaves as an "add student" form; otherwise it loads the matching student
 * from [studentRepository] on init and behaves as an "edit student" form.
 * `presentationModule` supplies [studentId] as a Koin injection parameter
 * sourced from the navigation argument, see [StudentFormRoute].
 */
class StudentFormViewModel(
    private val studentRepository: StudentRepository,
    private val studentId: Long?,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudentFormUiState(studentId = studentId))
    val uiState: StateFlow<StudentFormUiState> = _uiState.asStateFlow()

    init {
        loadExistingStudent()
    }

    private fun loadExistingStudent() {
        val id = studentId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val student = studentRepository.getStudentById(id)
            _uiState.update { state ->
                if (student == null) {
                    state.copy(isLoading = false)
                } else {
                    state.copy(
                        firstName = student.firstName,
                        className = student.className,
                        ppsGoals = student.ppsGoals,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onFirstNameChanged(firstName: String) {
        _uiState.update { it.copy(firstName = firstName) }
    }

    fun onClassNameChanged(className: String) {
        _uiState.update { it.copy(className = className) }
    }

    fun onPpsGoalsChanged(ppsGoals: String) {
        _uiState.update { it.copy(ppsGoals = ppsGoals) }
    }

    /** Persists the current field values, adding a new student or updating the existing one. */
    fun onSaveClicked() {
        val state = _uiState.value
        if (!state.canSave) return
        val student =
            Student(
                id = studentId ?: 0L,
                firstName = state.firstName.trim(),
                className = state.className.trim(),
                ppsGoals = state.ppsGoals.trim(),
            )
        viewModelScope.launch {
            if (studentId == null) {
                studentRepository.addStudent(student)
            } else {
                studentRepository.updateStudent(student)
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
