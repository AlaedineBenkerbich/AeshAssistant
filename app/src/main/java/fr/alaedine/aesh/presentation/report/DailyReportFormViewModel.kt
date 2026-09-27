package fr.alaedine.aesh.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation-layer state holder for the daily observation form.
 *
 * Keeps the student picker in sync with [studentRepository]. Once a student
 * is picked, [onStudentSelected] looks up that student's report for today
 * via [DailyReportRepository.getReportByDateAndStudent]: the
 * `(studentId, date)` unique index allows at most one, so re-opening the
 * form for the same student later the same day edits that existing report
 * instead of violating the constraint with a duplicate insert.
 */
class DailyReportFormViewModel(
    private val dailyReportRepository: DailyReportRepository,
    private val studentRepository: StudentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DailyReportFormUiState())
    val uiState: StateFlow<DailyReportFormUiState> = _uiState.asStateFlow()

    init {
        studentRepository
            .observeStudents()
            .onEach { students -> _uiState.update { it.copy(students = students, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** Picks [student] as the subject of this report, loading today's existing report for them, if any. */
    fun onStudentSelected(student: Student) {
        _uiState.update {
            it.copy(
                selectedStudent = student,
                reportId = null,
                moodLevel = NEUTRAL_LEVEL,
                focusLevel = NEUTRAL_LEVEL,
                socialInteractions = NEUTRAL_LEVEL,
                freeNotes = "",
            )
        }
        loadTodaysReportFor(student)
    }

    private fun loadTodaysReportFor(student: Student) {
        viewModelScope.launch {
            val date = _uiState.value.date
            val existingReport = dailyReportRepository.getReportByDateAndStudent(date, student.id)
            // Ignore a stale lookup if the user already switched to another student meanwhile.
            if (existingReport == null || _uiState.value.selectedStudent?.id != student.id) return@launch
            _uiState.update {
                it.copy(
                    reportId = existingReport.id,
                    moodLevel = existingReport.moodLevel,
                    focusLevel = existingReport.focusLevel,
                    socialInteractions = existingReport.socialInteractions,
                    freeNotes = existingReport.freeNotes,
                )
            }
        }
    }

    fun onMoodLevelChanged(moodLevel: Int) {
        _uiState.update { it.copy(moodLevel = moodLevel) }
    }

    fun onFocusLevelChanged(focusLevel: Int) {
        _uiState.update { it.copy(focusLevel = focusLevel) }
    }

    fun onSocialInteractionsChanged(socialInteractions: Int) {
        _uiState.update { it.copy(socialInteractions = socialInteractions) }
    }

    fun onFreeNotesChanged(freeNotes: String) {
        _uiState.update { it.copy(freeNotes = freeNotes) }
    }

    /** Persists the current field values, adding a new report or updating today's existing one. */
    fun onSaveClicked() {
        val state = _uiState.value
        val student = state.selectedStudent ?: return
        val report =
            DailyReport(
                id = state.reportId ?: 0L,
                date = state.date,
                studentId = student.id,
                moodLevel = state.moodLevel,
                focusLevel = state.focusLevel,
                socialInteractions = state.socialInteractions,
                freeNotes = state.freeNotes.trim(),
            )
        viewModelScope.launch {
            if (state.reportId == null) {
                dailyReportRepository.addReport(report)
            } else {
                dailyReportRepository.updateReport(report)
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
