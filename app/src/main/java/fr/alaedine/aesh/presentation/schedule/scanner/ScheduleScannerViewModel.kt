package fr.alaedine.aesh.presentation.schedule.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.repository.TextRecognitionRepository
import fr.alaedine.aesh.domain.scanner.ScheduleTextParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the "scan a physical schedule" flow: runs on-device OCR on a
 * captured photo (via [textRecognitionRepository]), parses the recognized
 * text with [ScheduleTextParser], and exposes the result for the user to
 * review before [ScheduleScannerRoute] hands it off to pre-fill
 * [fr.alaedine.aesh.presentation.schedule.ScheduleFormScreen].
 */
class ScheduleScannerViewModel(
    private val textRecognitionRepository: TextRecognitionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleScannerUiState())
    val uiState: StateFlow<ScheduleScannerUiState> = _uiState.asStateFlow()

    private var recognitionJob: Job? = null
    private var photoBeingRecognized: File? = null

    /**
     * Runs OCR + parsing on the just-captured photo at [imageFile]. The
     * file is only a temporary cache artifact (see `ScheduleScannerRoute`)
     * and is deleted once recognition completes, regardless of outcome —
     * including when it is abandoned (a newer photo, a retake, or the user
     * leaving the scanner), so a photo never outlives the scan.
     */
    fun onPhotoCaptured(imageFile: File) {
        // A newer photo (e.g. the shutter tapped twice) replaces the one being read: its result would be outdated.
        abandonRecognition()
        photoBeingRecognized = imageFile
        _uiState.update { it.copy(isProcessing = true, parsedScheduleSlot = null, error = null) }
        recognitionJob =
            viewModelScope.launch {
                try {
                    textRecognitionRepository
                        .recognizeText(imageFile)
                        .onSuccess { text ->
                            val parsed = ScheduleTextParser.parse(text)
                            _uiState.update {
                                if (parsed.isEmpty) {
                                    it.copy(isProcessing = false, error = ScheduleScannerError.NoTextRecognized)
                                } else {
                                    it.copy(isProcessing = false, parsedScheduleSlot = parsed)
                                }
                            }
                        }.onFailure {
                            _uiState.update {
                                it.copy(isProcessing = false, error = ScheduleScannerError.RecognitionFailed)
                            }
                        }
                } finally {
                    imageFile.delete()
                    if (photoBeingRecognized == imageFile) photoBeingRecognized = null
                }
            }
    }

    /** Discards the current result so the user can retake the photo. */
    fun onRetake() {
        abandonRecognition()
        _uiState.update { it.copy(isProcessing = false, parsedScheduleSlot = null, error = null) }
    }

    /** Dismisses the current error message, returning to the live viewfinder. */
    fun onErrorDismissed() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        // The recognition coroutine is cancelled with the scope; also covers one cancelled before it even started.
        photoBeingRecognized?.delete()
    }

    private fun abandonRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        photoBeingRecognized?.delete()
        photoBeingRecognized = null
    }
}
