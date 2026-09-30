package fr.alaedine.aesh.presentation.report.notes

/**
 * Immutable UI state for dictating observation notes, shown by
 * [fr.alaedine.aesh.presentation.report.DailyReportFormRoute]'s dictation
 * dialog.
 *
 * @property isAvailable Whether this device has an on-device speech
 * recognizer at all. When it doesn't, dictation can only report that, so
 * callers shouldn't bother the user with a microphone permission request first.
 * @property isListening Whether the microphone is being listened to right
 * now, i.e. whether the dictation dialog is open.
 * @property partialTranscript What has been recognized so far while
 * [isListening], shown live so the user can see they are being understood.
 * @property transcript Everything the user said, once they are done, waiting
 * to be handed on and cleared with [DictationViewModel.onTranscriptConsumed];
 * `null` otherwise.
 * @property error Why dictation couldn't produce a [transcript], waiting to
 * be shown and cleared with [DictationViewModel.onErrorShown]; `null`
 * otherwise. Kept as a semantic type rather than a raw message so
 * [DictationViewModel] stays free of Android resources/`Context`.
 */
data class DictationUiState(
    val isAvailable: Boolean = true,
    val isListening: Boolean = false,
    val partialTranscript: String = "",
    val transcript: String? = null,
    val error: DictationError? = null,
)

/** Reasons dictation can fail to turn the user's voice into a transcript. */
enum class DictationError {
    /** No on-device speech recognizer exists on this device. */
    Unavailable,

    /** The on-device recognizer is there, but its model for the device's language isn't installed. */
    LanguageNotInstalled,

    /** The microphone picked up nothing that could be recognized as speech. */
    NoSpeechDetected,

    /** The user didn't grant (or has revoked) the microphone permission. */
    MicrophonePermissionDenied,

    /** The recognizer failed for any other reason. */
    Failed,
}
