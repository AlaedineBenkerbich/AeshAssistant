package fr.alaedine.aesh.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Framework-agnostic contract for turning the user's voice into text,
 * implemented by [fr.alaedine.aesh.data.speech.AndroidSpeechRecognitionRepository]
 * on top of Android's *on-device* speech recognizer.
 *
 * Audio never leaves the device — see the project README's privacy section.
 * Implementations must therefore never fall back to a network-based
 * recognizer: when no on-device one is available, [isAvailable] is `false`
 * and [listen] fails with [SpeechRecognitionException.Unavailable].
 */
interface SpeechRecognitionRepository {
    /** Whether this device has an on-device speech recognizer, i.e. whether [listen] can work at all. */
    fun isAvailable(): Boolean

    /**
     * Listens to the microphone, in the device's language, until the user
     * stops talking or [stopListening] is called.
     *
     * Emits progressively refined transcripts of what was heard so far
     * (`isFinal = false`), then exactly one final transcript
     * (`isFinal = true`), and completes. Fails with a
     * [SpeechRecognitionException] when nothing could be recognized.
     * Cancelling the collector aborts listening and discards what was heard.
     *
     * Only one [listen] may be active at a time.
     */
    fun listen(): Flow<SpeechTranscript>

    /** Ends [listen] early: it then emits the final transcript of what was heard so far. */
    fun stopListening()
}

/**
 * What [SpeechRecognitionRepository.listen] heard so far.
 *
 * @property text The whole transcript of the current session (not just the
 * latest words), so a newer emission always replaces the previous one.
 * @property isFinal Whether the recognizer is done and [text] will no
 * longer change.
 */
data class SpeechTranscript(
    val text: String,
    val isFinal: Boolean,
)

/** Why [SpeechRecognitionRepository.listen] couldn't produce a transcript. */
sealed class SpeechRecognitionException(
    message: String,
) : Exception(message) {
    /** No on-device speech recognizer exists on this device. */
    class Unavailable : SpeechRecognitionException("On-device speech recognition isn't available on this device.")

    /** The on-device recognizer is there, but its model for the device's language isn't installed. */
    class LanguageNotInstalled : SpeechRecognitionException("The on-device speech model for this language isn't installed.")

    /** The microphone picked up nothing that could be recognized as speech. */
    class NoSpeechDetected : SpeechRecognitionException("No speech was detected.")

    /** The microphone permission is missing (never granted, or revoked meanwhile). */
    class MicrophonePermissionDenied : SpeechRecognitionException("Microphone access was denied.")

    /** The recognizer failed for any other reason; [errorCode] is the platform's raw error code. */
    class Failed(
        val errorCode: Int,
    ) : SpeechRecognitionException("Speech recognition failed (error code $errorCode).")
}
