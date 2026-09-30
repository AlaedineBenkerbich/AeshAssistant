package fr.alaedine.aesh.presentation.report.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.repository.SpeechRecognitionException
import fr.alaedine.aesh.domain.repository.SpeechRecognitionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives dictating observation notes: listens to the microphone through
 * [speechRecognitionRepository] (on-device only), shows what is being
 * recognized live, and exposes the complete transcript for
 * [fr.alaedine.aesh.presentation.report.DailyReportFormRoute] to hand to the
 * form, which sorts it into its fields.
 *
 * Errors never discard what was already heard: if the recognizer fails after
 * producing partial results, those are kept as the transcript.
 */
class DictationViewModel(
    private val speechRecognitionRepository: SpeechRecognitionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DictationUiState(isAvailable = speechRecognitionRepository.isAvailable()))
    val uiState: StateFlow<DictationUiState> = _uiState.asStateFlow()

    private var listeningJob: Job? = null

    /**
     * Starts listening, unless a session is already running. The
     * microphone permission must have been granted by the caller first.
     */
    fun onStartClicked() {
        if (_uiState.value.isListening) return
        if (!speechRecognitionRepository.isAvailable()) {
            _uiState.update { it.copy(error = DictationError.Unavailable) }
            return
        }
        _uiState.update { it.copy(isListening = true, partialTranscript = "", transcript = null, error = null) }
        listeningJob = viewModelScope.launch { listen() }
    }

    /** Ends the session early; the recognizer then delivers what it heard so far, which becomes the transcript. */
    fun onStopClicked() {
        speechRecognitionRepository.stopListening()
    }

    /** Abandons the session, discarding whatever was heard. */
    fun onCancelled() {
        listeningJob?.cancel()
        listeningJob = null
        _uiState.update { it.copy(isListening = false, partialTranscript = "") }
    }

    /** Reports that the user refused the microphone permission dictation needs. */
    fun onMicrophonePermissionDenied() {
        _uiState.update { it.copy(error = DictationError.MicrophonePermissionDenied) }
    }

    /** Clears the one-shot [DictationUiState.transcript] once it has been handed on. */
    fun onTranscriptConsumed() {
        _uiState.update { it.copy(transcript = null) }
    }

    /** Clears the one-shot [DictationUiState.error] once it has been shown. */
    fun onErrorShown() {
        _uiState.update { it.copy(error = null) }
    }

    private suspend fun listen() {
        var heardSoFar = ""
        val failure =
            try {
                speechRecognitionRepository.listen().collect { transcript ->
                    heardSoFar = transcript.text
                    if (!transcript.isFinal) _uiState.update { it.copy(partialTranscript = transcript.text) }
                }
                null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error
            }
        onListeningEnded(heardSoFar = heardSoFar.trim(), failure = failure)
    }

    private fun onListeningEnded(
        heardSoFar: String,
        failure: Exception?,
    ) {
        _uiState.update {
            if (heardSoFar.isNotEmpty()) {
                it.copy(isListening = false, partialTranscript = "", transcript = heardSoFar)
            } else {
                it.copy(isListening = false, partialTranscript = "", error = failure.toDictationError())
            }
        }
    }

    /** A session that ends with neither a transcript nor a failure simply heard nothing. */
    private fun Exception?.toDictationError(): DictationError =
        when (this) {
            null, is SpeechRecognitionException.NoSpeechDetected -> DictationError.NoSpeechDetected
            is SpeechRecognitionException.Unavailable -> DictationError.Unavailable
            is SpeechRecognitionException.LanguageNotInstalled -> DictationError.LanguageNotInstalled
            is SpeechRecognitionException.MicrophonePermissionDenied -> DictationError.MicrophonePermissionDenied
            else -> DictationError.Failed
        }
}
