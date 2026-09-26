package fr.alaedine.aesh.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * Presentation-layer state holder for the dashboard/home screen.
 *
 * Combines [studentRepository] and [dailyReportRepository] to derive, for
 * every known student, whether they already have a report for today (see
 * [StudentReportStatus]), which [HomeScreen] uses to warn about students
 * still missing one.
 */
class HomeViewModel(
    private val studentRepository: StudentRepository,
    private val dailyReportRepository: DailyReportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val today = _uiState.value.date
        combine(
            studentRepository.observeStudents(),
            dailyReportRepository.observeReports(),
        ) { students, reports ->
            val studentIdsWithReportToday = reports
                .filter { it.date == today }
                .mapTo(mutableSetOf()) { it.studentId }
            students.map { student ->
                StudentReportStatus(
                    student = student,
                    hasReportToday = student.id in studentIdsWithReportToday,
                )
            }
        }.onEach { statuses ->
            _uiState.update { it.copy(studentStatuses = statuses, isLoading = false) }
        }.launchIn(viewModelScope)
    }
}
