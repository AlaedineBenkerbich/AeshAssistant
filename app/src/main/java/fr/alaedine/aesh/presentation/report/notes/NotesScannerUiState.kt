package fr.alaedine.aesh.presentation.report.notes

import java.io.File

/**
 * Immutable UI state for [NotesScannerScreen], covering the full flow:
 * photographing one or more pages of handwritten notes → reading them with
 * on-device OCR → handing the recognized text back to the observation form.
 *
 * @property photos The photos taken so far, in order. Temporary cache files
 * owned by [NotesScannerViewModel], which deletes them as soon as they have
 * been read or the scanner is left, since they can show a child's personal
 * information.
 * @property isProcessing Whether the [photos] are currently being read.
 * @property recognizedNotes The text read from all [photos], waiting to be
 * handed back and cleared with [NotesScannerViewModel.onRecognizedNotesConsumed];
 * `null` otherwise.
 * @property error Why the last attempt to read the [photos] failed, `null`
 * otherwise. Kept as a semantic type rather than a raw message so
 * [NotesScannerViewModel] stays free of Android resources/`Context`;
 * [NotesScannerScreen] maps it to localized text via `stringResource`.
 */
data class NotesScannerUiState(
    val photos: List<File> = emptyList(),
    val isProcessing: Boolean = false,
    val recognizedNotes: String? = null,
    val error: NotesScannerError? = null,
) {
    /** Whether there is at least one photo to read and nothing is being read already. */
    val canFinish: Boolean get() = photos.isNotEmpty() && !isProcessing
}

/** Reasons [NotesScannerViewModel] can fail to turn the photos into text. */
enum class NotesScannerError {
    /** OCR ran but found no usable text (e.g. blurry photos, poor lighting). */
    NoTextRecognized,

    /** OCR itself failed to run (e.g. I/O error reading a photo). */
    RecognitionFailed,
}
