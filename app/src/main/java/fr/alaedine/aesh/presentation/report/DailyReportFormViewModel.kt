package fr.alaedine.aesh.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ObservationNotes
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import fr.alaedine.aesh.domain.usecase.NotesSortingResult
import fr.alaedine.aesh.domain.usecase.SortObservationNotesUseCase
import fr.alaedine.aesh.presentation.report.notes.DictationError
import kotlinx.coroutines.Job
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
 * [prefilledDate], letting a missed observation be backfilled for a
 * previous day), that `(date, studentId)` pair's report is looked up via
 * [DailyReportRepository.getReportByDateAndStudent]: the pair is a unique
 * index allowing at most one report, so re-opening the form for the same
 * student/day edits that existing report instead of violating the
 * constraint with a duplicate insert.
 *
 * Also fills the free-text fields from notes that were dictated or
 * photographed instead of typed: [onRawNotesReceived] sorts them with
 * [sortObservationNotes] (on-device AI) and adds them to the fields.
 *
 * @param preselectedStudentId When non-null, the matching student is
 * selected automatically the first time [studentRepository]'s student list
 * loads (see [preselectInitialStudentIfNeeded]) — supplied as a Koin
 * injection parameter sourced from the navigation argument when this form
 * is reached by tapping a student on the dashboard rather than its "new
 * report" FAB, see `DailyReportFormRoute`.
 * @param prefilledDate The initial [DailyReportFormUiState.date], sourced
 * the same way as [preselectedStudentId] — the dashboard's currently
 * selected date when reached by tapping a student row, so editing/
 * completing that day's observation opens the form already on the right
 * day instead of always defaulting to today. Clamped to today when it's in
 * the future (the dashboard, unlike this form, allows browsing forward in
 * time) since an observation can't be logged for a day that hasn't
 * happened yet. Defaults to today when `null`, i.e. when reached from the
 * "new report" FAB.
 */
class DailyReportFormViewModel(
    private val dailyReportRepository: DailyReportRepository,
    private val studentRepository: StudentRepository,
    private val sortObservationNotes: SortObservationNotesUseCase,
    private val preselectedStudentId: Long? = null,
    prefilledDate: LocalDate? = null,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(DailyReportFormUiState(date = (prefilledDate ?: LocalDate.now()).coerceAtMost(LocalDate.now())))
    val uiState: StateFlow<DailyReportFormUiState> = _uiState.asStateFlow()

    private var sortingJob: Job? = null
    private var notesBeingSorted = ""

    init {
        studentRepository
            .observeStudents()
            .onEach { students ->
                _uiState.update { it.copy(students = students, isLoading = false) }
                preselectInitialStudentIfNeeded(students)
            }.launchIn(viewModelScope)
    }

    /**
     * Selects [preselectedStudentId]'s matching student the first time the
     * student list loads, so the form opens ready to fill out rather than
     * requiring the student to be picked again from the dropdown. A no-op
     * once a student has already been selected (manually or by this very
     * call) or if [preselectedStudentId] doesn't match any known student.
     */
    private fun preselectInitialStudentIfNeeded(students: List<Student>) {
        if (preselectedStudentId == null || _uiState.value.selectedStudent != null) return
        val student = students.find { it.id == preselectedStudentId } ?: return
        onStudentSelected(student)
    }

    /** Picks [student] as the subject of this report, loading the currently selected date's existing report for them, if any. */
    fun onStudentSelected(student: Student) {
        abandonSortingNotes()
        _uiState.update {
            it.copy(
                selectedStudent = student,
                reportId = null,
                moodLevel = NEUTRAL_LEVEL,
                focusLevel = NEUTRAL_LEVEL,
                socialInteractions = NEUTRAL_LEVEL,
                autonomyLevel = NEUTRAL_LEVEL,
                obstacles = "",
                supportStrategies = "",
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
        abandonSortingNotes()
        _uiState.update {
            it.copy(
                date = date,
                reportId = null,
                moodLevel = NEUTRAL_LEVEL,
                focusLevel = NEUTRAL_LEVEL,
                socialInteractions = NEUTRAL_LEVEL,
                autonomyLevel = NEUTRAL_LEVEL,
                obstacles = "",
                supportStrategies = "",
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
                    autonomyLevel = existingReport.autonomyLevel,
                    obstacles = existingReport.obstacles,
                    supportStrategies = existingReport.supportStrategies,
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

    fun onAutonomyLevelChanged(autonomyLevel: Int) {
        _uiState.update { it.copy(autonomyLevel = autonomyLevel) }
    }

    fun onObstaclesChanged(obstacles: String) {
        _uiState.update { it.copy(obstacles = obstacles) }
    }

    fun onSupportStrategiesChanged(supportStrategies: String) {
        _uiState.update { it.copy(supportStrategies = supportStrategies) }
    }

    fun onFreeNotesChanged(freeNotes: String) {
        _uiState.update { it.copy(freeNotes = freeNotes) }
    }

    /**
     * Fills the free-text fields from [rawNotes] that were dictated or
     * recognized from photos: the on-device AI model sorts them into
     * obstacles / what helped / notes, and each part is *added after* what
     * the field already holds, so nothing the user typed is overwritten.
     * When sorting isn't possible the notes are added to the free notes
     * field as they are (see [NotesSortingResult.Unsorted]).
     *
     * Ignored without a selected student, for blank notes, or while another
     * batch is being sorted.
     */
    fun onRawNotesReceived(rawNotes: String) {
        val notes = rawNotes.trim()
        val state = _uiState.value
        if (notes.isEmpty() || state.selectedStudent == null || state.isSortingNotes) return

        notesBeingSorted = notes
        _uiState.update { it.copy(isSortingNotes = true) }
        sortingJob =
            viewModelScope.launch {
                when (val result = sortObservationNotes(notes)) {
                    is NotesSortingResult.Sorted -> addNotes(result.notes, DailyReportStatusMessage.NotesFilledIn)
                    is NotesSortingResult.Unsorted ->
                        addNotes(
                            result.notes,
                            DailyReportStatusMessage.NotesAddedUnsorted(result.cause.toUnsortedNotesReason()),
                        )
                }
            }
    }

    /** Stops waiting for the AI model and adds the notes being sorted to the free notes field as they are. */
    fun onSkipSortingClicked() {
        if (!_uiState.value.isSortingNotes) return
        sortingJob?.cancel()
        sortingJob = null
        addNotes(
            ObservationNotes.unsorted(notesBeingSorted),
            DailyReportStatusMessage.NotesAddedUnsorted(UnsortedNotesReason.Skipped),
        )
    }

    /** Reports that dictation couldn't produce any notes, for [error]. */
    fun onDictationFailed(error: DictationError) {
        _uiState.update { it.copy(statusMessage = DailyReportStatusMessage.DictationFailed(error)) }
    }

    /** Clears the one-shot [DailyReportFormUiState.statusMessage] once [DailyReportFormScreen] has shown it. */
    fun onStatusMessageShown() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun addNotes(
        notes: ObservationNotes,
        message: DailyReportStatusMessage,
    ) {
        _uiState.update {
            it.copy(
                obstacles = it.obstacles.withAppended(notes.obstacles),
                supportStrategies = it.supportStrategies.withAppended(notes.supportStrategies),
                freeNotes = it.freeNotes.withAppended(notes.freeNotes),
                isSortingNotes = false,
                statusMessage = message,
            )
        }
    }

    /**
     * Drops the notes being sorted, if any, when the form switches to another
     * student or day: they were meant for the report being left, and adding
     * them to the next one would put a child's notes in someone else's report.
     */
    private fun abandonSortingNotes() {
        sortingJob?.cancel()
        sortingJob = null
        _uiState.update { it.copy(isSortingNotes = false) }
    }

    private fun Throwable.toUnsortedNotesReason(): UnsortedNotesReason =
        when (this) {
            is AiFeatureUnavailableException -> UnsortedNotesReason.AiUnavailable
            is AiGenerationTimeoutException -> UnsortedNotesReason.Timeout
            else -> UnsortedNotesReason.Failed
        }

    private fun String.withAppended(addition: String): String =
        when {
            addition.isBlank() -> this
            isBlank() -> addition
            else -> "${trimEnd()}\n$addition"
        }

    /**
     * Persists the current field values, adding a new report or updating the selected date's existing one.
     * Ignored while notes are being sorted, since they would otherwise be added after the report was saved.
     */
    fun onSaveClicked() {
        val state = _uiState.value
        if (state.isSortingNotes) return
        val student = state.selectedStudent ?: return
        val report =
            DailyReport(
                id = state.reportId ?: 0L,
                date = state.date,
                studentId = student.id,
                moodLevel = state.moodLevel,
                focusLevel = state.focusLevel,
                socialInteractions = state.socialInteractions,
                autonomyLevel = state.autonomyLevel,
                obstacles = state.obstacles.trim(),
                supportStrategies = state.supportStrategies.trim(),
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
