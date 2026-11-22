package fr.alaedine.aesh.presentation.student.ideas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.SupportIdea
import fr.alaedine.aesh.domain.model.SupportIdeas
import fr.alaedine.aesh.domain.repository.AiBackgroundUseBlockedException
import fr.alaedine.aesh.domain.repository.AiBusyException
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiNotEnoughStorageException
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.StudentRepository
import fr.alaedine.aesh.domain.usecase.SuggestSupportIdeasUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Presentation-layer state holder for the support ideas screen of the
 * student identified by [studentId]: keeps the student and how many daily
 * observations they have in sync, and runs [suggestSupportIdeasUseCase] to
 * have the on-device model (Gemini Nano) suggest what could be put in place.
 *
 * The ideas are kept in [savedStateHandle], so they survive the process
 * being killed while the user is in another app.
 *
 * @param ideasLanguage The language to write the ideas in: the app's.
 */
class SupportIdeasViewModel(
    private val studentId: Long,
    studentRepository: StudentRepository,
    dailyReportRepository: DailyReportRepository,
    private val suggestSupportIdeasUseCase: SuggestSupportIdeasUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val ideasLanguage: () -> ReportLanguage = { ReportLanguage.fromLanguageCode(Locale.getDefault().language) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(SupportIdeasUiState(ideas = savedStateHandle.restoredIdeas()))
    val uiState: StateFlow<SupportIdeasUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        combine(
            studentRepository.observeStudents().map { students -> students.firstOrNull { it.id == studentId } },
            dailyReportRepository.observeReportsForStudent(studentId).map { it.size },
            ::Pair,
        ).onEach { (student, observationCount) ->
            _uiState.update { it.copy(student = student, observationCount = observationCount, isLoading = false) }
        }.launchIn(viewModelScope)
        _uiState
            .map { it.ideas }
            .distinctUntilChanged()
            .onEach { savedStateHandle.saveIdeas(it) }
            .launchIn(viewModelScope)
    }

    /** Asks the on-device model for ideas, replacing the previous ones only once new ones are ready. */
    fun onSuggestClicked() {
        val state = _uiState.value
        val student = state.student ?: return
        if (!state.canSuggest) return

        _uiState.update { it.copy(isGenerating = true, failure = null) }
        generationJob =
            viewModelScope.launch {
                // Cancelling the request cancels this coroutine, which the use case rethrows instead of returning a
                // result, so a cancelled request never lands on screen.
                suggestSupportIdeasUseCase(student, ideasLanguage())
                    .onSuccess { ideas -> _uiState.update { it.copy(isGenerating = false, ideas = ideas) } }
                    .onFailure { error -> _uiState.update { it.copy(isGenerating = false, failure = error.toFailure()) } }
            }
    }

    /** Stops the running request, keeping the previous ideas if there were any. */
    fun onCancelClicked() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false) }
    }

    private fun Throwable.toFailure(): SupportIdeasFailure =
        when (this) {
            is AiFeatureUnavailableException -> SupportIdeasFailure.AiFeatureUnavailable
            is AiGenerationTimeoutException -> SupportIdeasFailure.GenerationTimeout
            is AiBusyException -> SupportIdeasFailure.AiBusy
            is AiBackgroundUseBlockedException -> SupportIdeasFailure.BackgroundUseBlocked
            is AiNotEnoughStorageException -> SupportIdeasFailure.NotEnoughStorage
            else -> SupportIdeasFailure.GenerationFailed
        }
}

private const val KEY_IDEA_TEXTS = "ideaTexts"
private const val KEY_IDEA_REASONS = "ideaReasons"
private const val KEY_OBSERVATION_COUNT = "observationCount"
private const val KEY_FIRST_OBSERVATION_DATE = "firstObservationDate"
private const val KEY_LAST_OBSERVATION_DATE = "lastObservationDate"

/** The ideas saved by [saveIdeas], if any; a running request can't survive the process. */
private fun SavedStateHandle.restoredIdeas(): SupportIdeas? {
    val texts = get<List<String>>(KEY_IDEA_TEXTS) ?: return null
    val reasons = get<List<String>>(KEY_IDEA_REASONS) ?: return null
    val observationCount = get<Int>(KEY_OBSERVATION_COUNT) ?: return null
    val firstObservationDate = get<String>(KEY_FIRST_OBSERVATION_DATE)?.let(LocalDate::parse) ?: return null
    val lastObservationDate = get<String>(KEY_LAST_OBSERVATION_DATE)?.let(LocalDate::parse) ?: return null
    return SupportIdeas(
        ideas = texts.zip(reasons) { text, reason -> SupportIdea(text, reason) },
        observationCount = observationCount,
        firstObservationDate = firstObservationDate,
        lastObservationDate = lastObservationDate,
    )
}

private fun SavedStateHandle.saveIdeas(ideas: SupportIdeas?) {
    set(KEY_IDEA_TEXTS, ideas?.ideas?.mapTo(ArrayList()) { it.text })
    set(KEY_IDEA_REASONS, ideas?.ideas?.mapTo(ArrayList()) { it.reason })
    set(KEY_OBSERVATION_COUNT, ideas?.observationCount)
    set(KEY_FIRST_OBSERVATION_DATE, ideas?.firstObservationDate?.toString())
    set(KEY_LAST_OBSERVATION_DATE, ideas?.lastObservationDate?.toString())
}
