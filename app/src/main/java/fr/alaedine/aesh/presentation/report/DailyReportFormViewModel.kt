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
import java.time.LocalDate

/**
 * Presentation-layer state holder for the daily observation form.
 *
 * Keeps the student picker in sync with [studentRepository]. Once a student
 * is picked and/or a date is chosen via [onDateSelected] (defaults to
 * today, letting a missed observation be backfilled for a previous day),
 * that `(date, studentId)` pair's report is looked up via
 * [DailyReportRepository.getReportByDateAndStudent]: the pair is a unique
 * index allowing at most one report, so re-opening the form for the same
 * student/day edits that existing report instead of violating the
 * constraint with a duplicate insert.
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

    /** Picks [student] as the subject of this report, loading the currently selected date's existing report for them, if any. */
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
        loadReportFor(date = _uiState.value.date, student = student)
    }

    /**
     * Switches the report to cover [date] instead of the previously
     * selected one, letting a missed observation be backfilled for a
     * previous day. Dates after today are rejected — an observation can't
     * be logged for a day that hasn't happened yet — as a defensive
     * fallback; [DailyReportFormScreen]'s date picker already prevents
     * picking one.
     */
    fun onDateSelected(date: LocalDate) {
        if (date.isAfter(LocalDate.now())) return
        _uiState.update {
            it.copy(
                date = date,
                reportId = null,
                moodLevel = NEUTRAL_LEVEL,
                focusLevel = NEUTRAL_LEVEL,
                socialInteractions = NEUTRAL_LEVEL,
                freeNotes = "",
            )
        }
        val student = _uiState.value.selectedStudent ?: return
        loadReportFor(date = date, student = student)
    }

    /** Loads [student]'s existing report for [date], if any, into the current form fields. */
    private fun loadReportFor(
        date: LocalDate,
        student: Student,
    ) {
        viewModelScope.launch {
            val existingReport = dailyReportRepository.getReportByDateAndStudent(date, student.id)
            // Ignore a stale lookup if the user already switched to another student/date meanwhile.
            val currentState = _uiState.value
            if (existingReport == null || currentState.selectedStudent?.id != student.id || currentState.date != date) {
                return@launch
            }
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

    /** Persists the current field values, adding a new report or updating the selected date's existing one. */
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
