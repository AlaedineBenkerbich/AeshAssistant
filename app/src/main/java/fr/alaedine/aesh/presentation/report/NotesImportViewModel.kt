package fr.alaedine.aesh.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.model.ExtractedObservationNotes
import fr.alaedine.aesh.domain.repository.NoSpeechDetectedException
import fr.alaedine.aesh.domain.repository.SpeechToTextRepository
import fr.alaedine.aesh.domain.usecase.ExtractObservationNotesUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Outcome messages shown under the voice/photo import buttons. */
enum class NotesImportMessage {
    NoSpeechDetected,
    SpeechFailed,
    NoTextFound,
    Filled,
    FilledWithoutSorting,
}

/**
 * @property isDictationAvailable Whether on-device speech recognition exists on this device.
 * @property isListening Whether a dictation is in progress.
 * @property isProcessing Whether OCR / AI sorting is in progress.
 */
data class NotesImportUiState(
    val isDictationAvailable: Boolean = false,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val message: NotesImportMessage? = null,
) {
    val isBusy: Boolean get() = isListening || isProcessing
}

/**
 * Drives "fill the free-text fields by voice or from photos of written
 * notes" for the daily observation form. Kept apart from
 * [DailyReportFormViewModel] so the form's persistence logic stays
 * unaware of speech/OCR/AI: extracted notes are emitted on [extractedNotes]
 * and [DailyReportFormRoute] forwards them to
 * [DailyReportFormViewModel.onNotesImported].
 */
class NotesImportViewModel(
    private val speechToTextRepository: SpeechToTextRepository,
    private val extractObservationNotes: ExtractObservationNotesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotesImportUiState(isDictationAvailable = speechToTextRepository.isAvailable))
    val uiState: StateFlow<NotesImportUiState> = _uiState.asStateFlow()

    private val _extractedNotes = Channel<ExtractedObservationNotes>(Channel.BUFFERED)
    val extractedNotes: Flow<ExtractedObservationNotes> = _extractedNotes.receiveAsFlow()

    /** Listens for speech (the caller must already hold the microphone permission) and imports the transcript. */
    fun onDictateClicked() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isListening = true, message = null) }
        viewModelScope.launch {
            val transcript = speechToTextRepository.listen()
            _uiState.update { it.copy(isListening = false) }
            transcript
                .onSuccess { import(spokenText = it, photos = emptyList()) }
                .onFailure { error ->
                    val message = if (error is NoSpeechDetectedException) NotesImportMessage.NoSpeechDetected else NotesImportMessage.SpeechFailed
                    _uiState.update { it.copy(message = message) }
                }
        }
    }

    /** Ends the current dictation early, importing what was heard so far. */
    fun onStopDictationClicked() {
        speechToTextRepository.stopListening()
    }

    /** Imports the text of [photos] (temporary files, deleted once read), in the given order. */
    fun onPhotosSelected(photos: List<File>) {
        if (photos.isEmpty()) return
        if (_uiState.value.isBusy) {
            photos.forEach(File::delete)
            return
        }
        _uiState.update { it.copy(message = null) }
        viewModelScope.launch { import(spokenText = null, photos = photos) }
    }

    fun onMessageDismissed() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun import(
        spokenText: String?,
        photos: List<File>,
    ) {
        _uiState.update { it.copy(isProcessing = true) }
        val result = extractObservationNotes(spokenText, photos)
        photos.forEach(File::delete)
        result
            .onSuccess { notes ->
                _extractedNotes.send(notes)
                val message = if (notes.sortedByAi) NotesImportMessage.Filled else NotesImportMessage.FilledWithoutSorting
                _uiState.update { it.copy(isProcessing = false, message = message) }
            }.onFailure {
                _uiState.update { it.copy(isProcessing = false, message = NotesImportMessage.NoTextFound) }
            }
    }
}
