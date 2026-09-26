package fr.alaedine.aesh.presentation.schedule.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.alaedine.aesh.domain.repository.ScheduleScannerRepository
import fr.alaedine.aesh.domain.scanner.ScheduleTextParser
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the "scan a physical schedule" flow: runs on-device OCR on a
 * captured photo (via [scheduleScannerRepository]), parses the recognized
 * text with [ScheduleTextParser], and exposes the result for the user to
 * review before [ScheduleScannerRoute] hands it off to pre-fill
 * [fr.alaedine.aesh.presentation.schedule.ScheduleFormScreen].
 */
class ScheduleScannerViewModel(
    private val scheduleScannerRepository: ScheduleScannerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleScannerUiState())
    val uiState: StateFlow<ScheduleScannerUiState> = _uiState.asStateFlow()

    /**
     * Runs OCR + parsing on the just-captured photo at [imageFile]. The
     * file is only a temporary cache artifact (see `ScheduleScannerRoute`)
     * and is deleted once recognition completes, regardless of outcome.
     */
    fun onPhotoCaptured(imageFile: File) {
        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }
        viewModelScope.launch {
            scheduleScannerRepository.recognizeText(imageFile)
                .onSuccess { text ->
                    val parsed = ScheduleTextParser.parse(text)
                    _uiState.update {
                        if (parsed.isEmpty) {
                            it.copy(
                                isProcessing = false,
                                errorMessage = "No text recognized. Try retaking the photo with better lighting or focus.",
                            )
                        } else {
                            it.copy(isProcessing = false, parsedScheduleSlot = parsed)
                        }
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = "Couldn't read the photo. Please try again.",
                        )
                    }
                }
            imageFile.delete()
        }
    }

    /** Discards the current result so the user can retake the photo. */
    fun onRetake() {
        _uiState.update { it.copy(parsedScheduleSlot = null, errorMessage = null) }
    }

    /** Dismisses the current error message, returning to the live viewfinder. */
    fun onErrorDismissed() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
