package fr.alaedine.aesh.presentation.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiBackgroundUseBlockedException
import fr.alaedine.aesh.domain.repository.AiBusyException
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiNotEnoughStorageException
import fr.alaedine.aesh.domain.repository.AiPromptTooLongException
import fr.alaedine.aesh.domain.repository.PdfExportRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase
import fr.alaedine.aesh.domain.usecase.NoReportsInRangeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

/**
 * Presentation-layer state holder for the ESS report generation screen:
 * lets the user pick a student and a date range, runs
 * [generateEssReportUseCase] to produce a draft report entirely on-device
 * (Gemini Nano), lets them edit the result, then exports the final text as
 * a PDF via [pdfExportRepository].
 *
 * The selection and the draft are kept in [savedStateHandle], so they
 * survive the process being killed while the user is in another app.
 *
 * @param reportLanguage The language to write the report in: the app's.
 */
class EssReportViewModel(
    private val studentRepository: StudentRepository,
    private val generateEssReportUseCase: GenerateEssReportUseCase,
    private val pdfExportRepository: PdfExportRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val reportLanguage: () -> ReportLanguage = { ReportLanguage.fromLanguageCode(Locale.getDefault().language) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(savedStateHandle.restoredUiState())
    val uiState: StateFlow<EssReportUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        studentRepository
            .observeStudents()
            .onEach { students -> _uiState.update { it.copy(students = students, isLoadingStudents = false) } }
            .launchIn(viewModelScope)
        _uiState.onEach { savedStateHandle.save(it) }.launchIn(viewModelScope)
    }

    /**
     * Picks [student] as the report subject, discarding any previously generated draft and abandoning a running
     * generation, whose report would be about the previous student. Picking the same student again changes nothing.
     */
    fun onStudentSelected(student: Student) {
        if (student.id == _uiState.value.selectedStudentId) return
        abandonGeneration()
        _uiState.update { it.copy(selectedStudentId = student.id, draft = null) }
    }

    /** Same as [onStudentSelected], for the start of the period. */
    fun onStartDateSelected(date: LocalDate) {
        if (date == _uiState.value.startDate) return
        abandonGeneration()
        _uiState.update { it.copy(startDate = date, draft = null) }
    }

    /** Same as [onStudentSelected], for the end of the period. */
    fun onEndDateSelected(date: LocalDate) {
        if (date == _uiState.value.endDate) return
        abandonGeneration()
        _uiState.update { it.copy(endDate = date, draft = null) }
    }

    /**
     * Generates a report from the selected student's notes in range. When it would replace a draft the user edited,
     * asks for confirmation first (see [onRegenerateConfirmed]).
     */
    fun onGenerateClicked() {
        val state = _uiState.value
        if (!state.canGenerate) return
        if (state.draft?.isEdited == true) {
            _uiState.update { it.copy(isRegenerateConfirmationVisible = true) }
        } else {
            startGeneration()
        }
    }

    /** Replaces the edited draft with a newly generated one, as the user confirmed. */
    fun onRegenerateConfirmed() {
        _uiState.update { it.copy(isRegenerateConfirmationVisible = false) }
        startGeneration()
    }

    /** Keeps the edited draft. */
    fun onRegenerateDismissed() {
        _uiState.update { it.copy(isRegenerateConfirmationVisible = false) }
    }

    /** Stops the running generation, keeping the previous draft if there was one. */
    fun onCancelGenerationClicked() {
        abandonGeneration()
    }

    /** Applies the user's manual edits to the generated draft. */
    fun onReportTextChanged(text: String) {
        _uiState.update { state -> state.copy(draft = state.draft?.copy(text = text, isEdited = true)) }
    }

    /**
     * Renders the current (possibly user-edited) report text as a PDF into
     * [destination] (a SAF-picked file), under [title].
     *
     * @param title Precomputed by [EssReportScreen] (e.g. via
     * `stringResource`) rather than built here, so this class stays free of
     * Android resources/`Context`.
     */
    fun onExportRequested(
        destination: OutputStream,
        title: String,
    ) {
        val draft = _uiState.value.draft
        if (draft == null) {
            // The picker already created the file: close it, and say so rather than silently leaving it empty.
            runCatching { destination.close() }
            _uiState.update { it.copy(statusMessage = EssReportStatusMessage.ExportFailed) }
            return
        }

        viewModelScope.launch {
            runCatching {
                pdfExportRepository.exportTextAsPdf(title = title, body = draft.text, destination = destination)
            }.onSuccess { _uiState.update { it.copy(statusMessage = EssReportStatusMessage.ExportSuccess) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(statusMessage = EssReportStatusMessage.ExportFailed) }
                }
        }
    }

    /** Reports that the SAF-picked export destination couldn't be opened for writing. */
    fun onExportFailedToOpenFile() {
        _uiState.update { it.copy(statusMessage = EssReportStatusMessage.ExportFileOpenFailed) }
    }

    /** Clears the one-shot [EssReportUiState.statusMessage] once [EssReportScreen] has shown it. */
    fun onStatusMessageShown() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun startGeneration() {
        val state = _uiState.value
        val student = state.selectedStudent ?: return
        if (!state.canGenerate) return
        val startDate = state.startDate
        val endDate = state.endDate

        _uiState.update { it.copy(isGenerating = true, generationProgress = null) }
        generationJob =
            viewModelScope.launch {
                // Abandoning the generation cancels this coroutine, which the use case rethrows instead of returning
                // a result, so a report about a previous selection can never land in the current one.
                generateEssReportUseCase(student, startDate, endDate, reportLanguage()) { progress ->
                    _uiState.update { it.copy(generationProgress = progress) }
                }.onSuccess { text ->
                    val draft = EssReportDraft(text = text, studentFirstName = student.firstName, startDate = startDate, endDate = endDate)
                    _uiState.update { it.copy(isGenerating = false, generationProgress = null, draft = draft) }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(isGenerating = false, generationProgress = null, statusMessage = error.toStatusMessage())
                    }
                }
            }
    }

    private fun abandonGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, generationProgress = null) }
    }

    private fun Throwable.toStatusMessage(): EssReportStatusMessage =
        when (this) {
            is NoReportsInRangeException -> EssReportStatusMessage.NoReportsInRange(studentFirstName)
            is AiFeatureUnavailableException -> EssReportStatusMessage.AiFeatureUnavailable
            is AiGenerationTimeoutException -> EssReportStatusMessage.GenerationTimeout
            is AiPromptTooLongException -> EssReportStatusMessage.PromptTooLong
            is AiBusyException -> EssReportStatusMessage.AiBusy
            is AiBackgroundUseBlockedException -> EssReportStatusMessage.BackgroundUseBlocked
            is AiNotEnoughStorageException -> EssReportStatusMessage.NotEnoughStorage
            else -> EssReportStatusMessage.GenerationFailed
        }
}

private const val KEY_SELECTED_STUDENT_ID = "selectedStudentId"
private const val KEY_START_DATE = "startDate"
private const val KEY_END_DATE = "endDate"
private const val KEY_DRAFT_TEXT = "draftText"
private const val KEY_DRAFT_STUDENT_FIRST_NAME = "draftStudentFirstName"
private const val KEY_DRAFT_START_DATE = "draftStartDate"
private const val KEY_DRAFT_END_DATE = "draftEndDate"
private const val KEY_DRAFT_IS_EDITED = "draftIsEdited"

/** The selection and draft saved by [save], over the defaults; a running generation can't survive the process. */
private fun SavedStateHandle.restoredUiState(): EssReportUiState {
    val defaults = EssReportUiState()
    return defaults.copy(
        selectedStudentId = get<Long>(KEY_SELECTED_STUDENT_ID),
        startDate = get<String>(KEY_START_DATE)?.let(LocalDate::parse) ?: defaults.startDate,
        endDate = get<String>(KEY_END_DATE)?.let(LocalDate::parse) ?: defaults.endDate,
        draft = restoredDraft(),
    )
}

private fun SavedStateHandle.restoredDraft(): EssReportDraft? {
    val text = get<String>(KEY_DRAFT_TEXT) ?: return null
    val studentFirstName = get<String>(KEY_DRAFT_STUDENT_FIRST_NAME) ?: return null
    val startDate = get<String>(KEY_DRAFT_START_DATE)?.let(LocalDate::parse) ?: return null
    val endDate = get<String>(KEY_DRAFT_END_DATE)?.let(LocalDate::parse) ?: return null
    return EssReportDraft(text, studentFirstName, startDate, endDate, isEdited = get<Boolean>(KEY_DRAFT_IS_EDITED) == true)
}

private fun SavedStateHandle.save(state: EssReportUiState) {
    set(KEY_SELECTED_STUDENT_ID, state.selectedStudentId)
    set(KEY_START_DATE, state.startDate.toString())
    set(KEY_END_DATE, state.endDate.toString())
    val draft = state.draft
    set(KEY_DRAFT_TEXT, draft?.text)
    set(KEY_DRAFT_STUDENT_FIRST_NAME, draft?.studentFirstName)
    set(KEY_DRAFT_START_DATE, draft?.startDate?.toString())
    set(KEY_DRAFT_END_DATE, draft?.endDate?.toString())
    set(KEY_DRAFT_IS_EDITED, draft?.isEdited)
}
