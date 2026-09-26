package fr.alaedine.aesh.presentation.schedule.scanner

import fr.alaedine.aesh.domain.model.ParsedScheduleSlot

/**
 * Immutable UI state for [ScheduleScannerScreen], covering the full flow:
 * live camera viewfinder → captured photo being recognized → reviewing the
 * parsed fields before they're handed off to pre-fill the schedule form.
 *
 * @property isProcessing Whether a just-captured photo is currently being
 * run through on-device OCR + parsing.
 * @property parsedScheduleSlot The fields recognized from the last
 * successfully processed photo, awaiting user confirmation; `null` before
 * any photo is captured or after the user retakes one.
 * @property errorMessage A user-facing message when recognition failed or
 * found no usable text, `null` otherwise.
 */
data class ScheduleScannerUiState(
    val isProcessing: Boolean = false,
    val parsedScheduleSlot: ParsedScheduleSlot? = null,
    val errorMessage: String? = null,
) {
    /** Whether a captured photo has been recognized and is awaiting user confirmation. */
    val isReviewing: Boolean get() = parsedScheduleSlot != null
}
