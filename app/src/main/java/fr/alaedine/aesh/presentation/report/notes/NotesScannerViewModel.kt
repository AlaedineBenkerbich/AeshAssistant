package fr.alaedine.aesh.presentation.report.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.usecase.NoTextRecognizedException
import fr.alaedine.aesh.domain.usecase.RecognizeNotesFromPhotosUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the "scan handwritten notes" flow: collects the photos the user
 * takes of their notes, reads them all with on-device OCR (via
 * [recognizeNotesFromPhotos]) and exposes the text for [NotesScannerRoute]
 * to hand back to the observation form, which sorts it into its fields.
 *
 * Owns the photo files it is given and never lets one outlive its use: they
 * are deleted once read, when removed, and when the scanner is left.
 */
class NotesScannerViewModel(
    private val recognizeNotesFromPhotos: RecognizeNotesFromPhotosUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotesScannerUiState())
    val uiState: StateFlow<NotesScannerUiState> = _uiState.asStateFlow()

    /** Adds the just-captured [photo] (a temporary cache file) after the ones already taken. */
    fun onPhotoCaptured(photo: File) {
        _uiState.update { it.copy(photos = it.photos + photo, error = null) }
    }

    /** Throws away the most recently taken photo, e.g. because it came out blurry. */
    fun onLastPhotoRemoved() {
        val lastPhoto = _uiState.value.photos.lastOrNull() ?: return
        lastPhoto.delete()
        _uiState.update { it.copy(photos = it.photos.dropLast(1), error = null) }
    }

    /**
     * Reads all the photos taken so far. On success they are deleted and the
     * text is exposed; on failure they are kept, so the user can take more
     * photos or remove the bad ones and try again.
     */
    fun onDoneClicked() {
        val state = _uiState.value
        if (!state.canFinish) return

        val photos = state.photos
        _uiState.update { it.copy(isProcessing = true, error = null) }
        viewModelScope.launch {
            recognizeNotesFromPhotos(photos)
                .onSuccess { notes ->
                    photos.forEach { it.delete() }
                    _uiState.update { it.copy(isProcessing = false, photos = emptyList(), recognizedNotes = notes) }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    val scannerError =
                        if (error is NoTextRecognizedException) NotesScannerError.NoTextRecognized else NotesScannerError.RecognitionFailed
                    _uiState.update { it.copy(isProcessing = false, error = scannerError) }
                }
        }
    }

    /** Clears the one-shot [NotesScannerUiState.recognizedNotes] once it has been handed back. */
    fun onRecognizedNotesConsumed() {
        _uiState.update { it.copy(recognizedNotes = null) }
    }

    /** Dismisses the current error message, returning to the live viewfinder. */
    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        _uiState.value.photos.forEach { it.delete() }
    }
}
