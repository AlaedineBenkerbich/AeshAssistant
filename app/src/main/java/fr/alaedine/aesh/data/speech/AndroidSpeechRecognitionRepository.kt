package fr.alaedine.aesh.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import fr.alaedine.aesh.domain.repository.SpeechRecognitionException
import fr.alaedine.aesh.domain.repository.SpeechRecognitionRepository
import fr.alaedine.aesh.domain.repository.SpeechTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.Locale

/**
 * [SpeechRecognitionRepository] backed by Android's *on-device* speech
 * recognizer ([SpeechRecognizer.createOnDeviceSpeechRecognizer], API 31+),
 * so what the user says never leaves the device — see the project README's
 * privacy section.
 *
 * Deliberately never falls back to the default, possibly cloud-based
 * [SpeechRecognizer]: a device without an on-device recognizer simply can't
 * dictate, which the UI explains, rather than silently streaming a child's
 * observations to a server.
 *
 * Each [listen] session gets its own recognizer, created when collection
 * starts and destroyed when it ends. Recognizers are flaky to reuse
 * (`ERROR_RECOGNIZER_BUSY` right after a session) and this way a session
 * can never leak one.
 */
class AndroidSpeechRecognitionRepository(
    private val context: Context,
) : SpeechRecognitionRepository {
    // Only ever touched on the main thread, which is where SpeechRecognizer must be used from.
    private var activeRecognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun listen(): Flow<SpeechTranscript> =
        callbackFlow {
            val recognizer = createOnDeviceRecognizer() ?: throw SpeechRecognitionException.Unavailable()
            activeRecognizer = recognizer
            recognizer.setRecognitionListener(TranscriptListener(producer = this))
            recognizer.startListening(recognizerIntent())
            awaitClose {
                activeRecognizer = null
                // Also aborts the session when the collector was cancelled mid-way.
                recognizer.destroy()
            }
            // Not `Dispatchers.Main.immediate`: the teardown above must be posted rather than run inline, since
            // it is triggered from the recognizer's own `onResults`/`onError` callback.
        }.flowOn(Dispatchers.Main)

    override fun stopListening() {
        activeRecognizer?.stopListening()
    }

    private fun createOnDeviceRecognizer(): SpeechRecognizer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            null
        }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Hints that let the user pause to think without ending the session; recognizers may ignore them.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MILLIS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
            }
        }

    /** Forwards the recognizer's callbacks to [producer]; a session ends with its first result or error. */
    private class TranscriptListener(
        private val producer: ProducerScope<SpeechTranscript>,
    ) : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.firstTranscript()?.let { producer.trySend(SpeechTranscript(text = it, isFinal = false)) }
        }

        override fun onResults(results: Bundle?) {
            producer.trySend(SpeechTranscript(text = results.firstTranscript().orEmpty(), isFinal = true))
            producer.close()
        }

        override fun onError(error: Int) {
            producer.close(error.toSpeechRecognitionException())
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(
            eventType: Int,
            params: Bundle?,
        ) = Unit
    }

    private companion object {
        // An Int, not a Long: Google's recognition service reads these extras with `getIntExtra`, which silently
        // ignores (and logs a ClassCastException for) a Long, leaving the hint without any effect.
        const val SILENCE_TIMEOUT_MILLIS = 2_500
    }
}

private fun Bundle?.firstTranscript(): String? = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

private fun Int.toSpeechRecognitionException(): SpeechRecognitionException =
    when (this) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> SpeechRecognitionException.NoSpeechDetected()
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        -> SpeechRecognitionException.LanguageNotInstalled()
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechRecognitionException.MicrophonePermissionDenied()
        else -> SpeechRecognitionException.Failed(errorCode = this)
    }
