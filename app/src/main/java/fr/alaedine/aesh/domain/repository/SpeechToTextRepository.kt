package fr.alaedine.aesh.domain.repository

/**
 * Framework-agnostic contract for dictating text by voice, implemented by
 * [fr.alaedine.aesh.data.speech.AndroidSpeechToTextRepository] on top of
 * Android's **on-device** speech recognizer, so audio never leaves the
 * device (see the project README's privacy section).
 */
interface SpeechToTextRepository {
    /**
     * Whether an on-device speech recognizer is available. When `false`
     * (e.g. before Android 13, or no offline language pack installed),
     * dictation is not offered at all rather than silently falling back to
     * a cloud-backed recognizer.
     */
    val isAvailable: Boolean

    /**
     * Listens until the user stops speaking (or [stopListening] is called)
     * and returns the transcript, or a failed [Result] —
     * [NoSpeechDetectedException] when nothing intelligible was said.
     * Cancelling the calling coroutine aborts listening.
     */
    suspend fun listen(): Result<String>

    /** Ends the current [listen] call early, letting it return what was heard so far. */
    fun stopListening()
}

/** Thrown by [SpeechToTextRepository.listen] when no speech was detected or understood. */
class NoSpeechDetectedException : Exception("No speech was detected.")
