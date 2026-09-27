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
 * @property error A recognition failure reason when the last capture
 * couldn't be turned into usable fields, `null` otherwise. Kept as a
 * semantic type rather than a raw message so [ScheduleScannerViewModel]
 * stays free of Android resources/`Context`; [ScheduleScannerScreen] maps
 * it to localized text via `stringResource`.
 */
data class ScheduleScannerUiState(
    val isProcessing: Boolean = false,
    val parsedScheduleSlot: ParsedScheduleSlot? = null,
    val error: ScheduleScannerError? = null,
) {
    /** Whether a captured photo has been recognized and is awaiting user confirmation. */
    val isReviewing: Boolean get() = parsedScheduleSlot != null
}

/** Reasons [ScheduleScannerViewModel] can fail to turn a captured photo into usable schedule fields. */
enum class ScheduleScannerError {
    /** OCR ran but found no usable text (e.g. blurry photo, poor lighting). */
    NoTextRecognized,

    /** OCR itself failed to run (e.g. I/O error reading the captured photo). */
    RecognitionFailed,
}
