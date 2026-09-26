package fr.alaedine.aesh.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.PdfExportRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase
import fr.alaedine.aesh.domain.usecase.NoReportsInRangeException
import java.io.OutputStream
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Presentation-layer state holder for the ESS report generation screen:
 * lets the user pick a student and a date range, runs
 * [generateEssReportUseCase] to produce a draft report entirely on-device
 * (Gemini Nano), lets them edit the result, then exports the final text as
 * a PDF via [pdfExportRepository].
 */
class EssReportViewModel(
    private val studentRepository: StudentRepository,
    private val generateEssReportUseCase: GenerateEssReportUseCase,
    private val pdfExportRepository: PdfExportRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EssReportUiState())
    val uiState: StateFlow<EssReportUiState> = _uiState.asStateFlow()

    init {
        studentRepository.observeStudents()
            .onEach { students -> _uiState.update { it.copy(students = students, isLoadingStudents = false) } }
            .launchIn(viewModelScope)
    }

    /** Picks [student] as the report subject, discarding any previously generated draft. */
    fun onStudentSelected(student: Student) {
        _uiState.update { it.copy(selectedStudent = student, generatedText = null) }
    }

    fun onStartDateSelected(date: LocalDate) {
        _uiState.update { it.copy(startDate = date, generatedText = null) }
    }

    fun onEndDateSelected(date: LocalDate) {
        _uiState.update { it.copy(endDate = date, generatedText = null) }
    }

    /** Concatenates the selected student's notes in range and runs them through the on-device model. */
    fun onGenerateClicked() {
        val state = _uiState.value
        val student = state.selectedStudent
        if (student == null || !state.canGenerate) return

        _uiState.update { it.copy(isGenerating = true, generatedText = null) }
        viewModelScope.launch {
            generateEssReportUseCase(student, state.startDate, state.endDate)
                .onSuccess { text -> _uiState.update { it.copy(isGenerating = false, generatedText = text) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    val message = when (error) {
                        is NoReportsInRangeException -> EssReportStatusMessage.NoReportsInRange(error.studentFirstName)
                        is AiFeatureUnavailableException -> EssReportStatusMessage.AiFeatureUnavailable
                        else -> EssReportStatusMessage.GenerationFailed(error.message)
                    }
                    _uiState.update { it.copy(isGenerating = false, statusMessage = message) }
                }
        }
    }

    /** Applies the user's manual edits to the generated draft. */
    fun onReportTextChanged(text: String) {
        _uiState.update { it.copy(generatedText = text) }
    }

    /**
     * Renders the current (possibly user-edited) report text as a PDF into
     * [destination] (a SAF-picked file), under [title].
     *
     * @param title Precomputed by [EssReportScreen] (e.g. via
     * `stringResource`) rather than built here, so this class stays free of
     * Android resources/`Context`.
     */
    fun onExportRequested(destination: OutputStream, title: String) {
        val state = _uiState.value
        if (state.selectedStudent == null) return
        val text = state.generatedText ?: return

        viewModelScope.launch {
            runCatching {
                pdfExportRepository.exportTextAsPdf(title = title, body = text, destination = destination)
            }
                .onSuccess { _uiState.update { it.copy(statusMessage = EssReportStatusMessage.ExportSuccess) } }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(statusMessage = EssReportStatusMessage.ExportFailed(error.message.orEmpty()))
                    }
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
}
