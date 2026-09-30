package fr.alaedine.aesh.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import fr.alaedine.aesh.domain.repository.NoSpeechDetectedException
import fr.alaedine.aesh.domain.repository.SpeechToTextRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * [SpeechToTextRepository] backed by Android's on-device [SpeechRecognizer]
 * (API 33+). Deliberately never uses the default recognizer, which may
 * stream audio to a server: if no on-device recognizer exists,
 * [isAvailable] is `false` and dictation isn't offered.
 *
 * [SpeechRecognizer] must be used from the main thread, hence the
 * [Dispatchers.Main] hop in [listen].
 */
class AndroidSpeechToTextRepository(
    private val context: Context,
) : SpeechToTextRepository {
    private var activeRecognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override suspend fun listen(): Result<String> {
        if (!isAvailable) return Result.failure(UnsupportedOperationException("On-device speech recognition is unavailable."))
        return withContext(Dispatchers.Main.immediate) { listenOnDevice() }
    }

    override fun stopListening() {
        activeRecognizer?.stopListening()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun listenOnDevice(): Result<String> =
        suspendCancellableCoroutine { continuation ->
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            activeRecognizer = recognizer

            fun finish(result: Result<String>) {
                if (activeRecognizer === recognizer) activeRecognizer = null
                recognizer.destroy()
                if (continuation.isActive) continuation.resume(result)
            }

            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onResults(results: Bundle?) {
                        val transcript =
                            results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.takeIf { it.isNotBlank() }
                        finish(transcript?.let { Result.success(it) } ?: Result.failure(NoSpeechDetectedException()))
                    }

                    override fun onError(error: Int) {
                        val failure =
                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                -> NoSpeechDetectedException()
                                else -> IllegalStateException("Speech recognition failed (error $error).")
                            }
                        finish(Result.failure(failure))
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit

                    override fun onBeginningOfSpeech() = Unit

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?,
                    ) = Unit
                },
            )
            continuation.invokeOnCancellation {
                if (activeRecognizer === recognizer) activeRecognizer = null
                recognizer.destroy()
            }

            val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            recognizer.startListening(intent)
        }
}
