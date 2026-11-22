package fr.alaedine.aesh.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import fr.alaedine.aesh.domain.repository.AiBackgroundUseBlockedException
import fr.alaedine.aesh.domain.repository.AiBusyException
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiNotEnoughStorageException
import fr.alaedine.aesh.domain.repository.AiPromptTooLongException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.AiWordChoice
import fr.alaedine.aesh.domain.repository.GeneratedText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

/**
 * [AiTextGenerationRepository] backed by ML Kit's on-device GenAI Prompt
 * API, which runs Gemini Nano through Android's AICore system service — no
 * network calls, nothing ever leaves the device (see the project README's
 * privacy section).
 *
 * The underlying model client is lazily created and reused for the
 * lifetime of the process, mirroring how
 * [fr.alaedine.aesh.data.scanner.MlKitTextRecognitionRepository] reuses its
 * ML Kit recognizer.
 *
 * ML Kit's errors ([GenAiException]) carry developer-facing English
 * messages, so they are turned into the domain's own exceptions here,
 * which the UI can explain in the user's language.
 */
class GeminiNanoTextGenerationRepository : AiTextGenerationRepository {
    private val generativeModel by lazy { Generation.getClient() }

    /**
     * Bounds how long a single [generate] call may take end-to-end (status
     * check + model download + inference). Without this, a stalled AICore
     * service — a documented risk with this beta API, e.g. a service that
     * "failed to bind" or a download whose progress silently stops — leaves
     * the caller (and the "generating" UI it drives) suspended forever
     * instead of failing with an actionable error. Generous on purpose: the
     * first-ever run on a device may need to download a multi-gigabyte
     * model over a slow connection. Timing out doesn't cancel that download
     * on AICore's side, so a later attempt picks up where it left off.
     */
    private val generationTimeout = 5.minutes

    override suspend fun generate(
        prompt: String,
        wordChoice: AiWordChoice,
    ): Result<GeneratedText> =
        runCatching {
            try {
                withTimeout(generationTimeout) {
                    when (generativeModel.checkStatus()) {
                        FeatureStatus.UNAVAILABLE -> throw AiFeatureUnavailableException()
                        FeatureStatus.AVAILABLE -> Unit
                        // DOWNLOADABLE or DOWNLOADING: either way, awaiting the download
                        // flow below suspends until the model is ready to run.
                        else -> awaitModelDownload()
                    }
                    generateRetryingWhileBusy(request(prompt, wordChoice))
                }
            } catch (timeout: TimeoutCancellationException) {
                throw AiGenerationTimeoutException()
            } catch (error: GenAiException) {
                throw error.toDomainException()
            }
        }

    /** Suspends until the on-device model finishes downloading, throwing if the download itself fails. */
    private suspend fun awaitModelDownload() {
        generativeModel.download().collect { status ->
            if (status is DownloadStatus.DownloadFailed) throw status.e
        }
    }

    /**
     * AICore answers [GenAiException.ErrorCode.BUSY] to bursts of requests (an ESS report over a long period sends
     * several in a row) and says how long to wait, so a couple of short waits are worth it before giving up.
     */
    private suspend fun generateRetryingWhileBusy(request: GenerateContentRequest): GeneratedText {
        repeat(BUSY_RETRY_COUNT) {
            try {
                return generateText(request)
            } catch (error: GenAiException) {
                if (error.errorCode != GenAiException.ErrorCode.BUSY) throw error
                delay(error.retryDelay?.toKotlinDuration()?.coerceIn(MIN_BUSY_RETRY_DELAY, MAX_BUSY_RETRY_DELAY) ?: MIN_BUSY_RETRY_DELAY)
            }
        }
        return generateText(request)
    }

    /**
     * ML Kit's defaults (temperature 0) always pick the most likely word, so the same prompt always gets the very same
     * answer: regenerating a draft would just write it again. [AiWordChoice.Varied] samples among the few likeliest
     * words instead, which rewords the answer without straying from the prompt.
     */
    private fun request(
        prompt: String,
        wordChoice: AiWordChoice,
    ): GenerateContentRequest =
        generateContentRequest(TextPart(prompt)) {
            if (wordChoice == AiWordChoice.Varied) {
                temperature = VARIED_WORDING_TEMPERATURE
                topK = VARIED_WORDING_TOP_K
            }
        }

    private suspend fun generateText(request: GenerateContentRequest): GeneratedText {
        val candidate = generativeModel.generateContent(request).candidates.firstOrNull()
        val text = candidate?.text
        if (text.isNullOrBlank()) throw IllegalStateException("The on-device AI model returned an empty answer.")
        // The model stops at the output length its configuration allows, and says so rather than failing.
        return GeneratedText(text = text, isCutOff = candidate.finishReason == Candidate.FinishReason.MAX_TOKENS)
    }

    private fun GenAiException.toDomainException(): Exception =
        when (errorCode) {
            GenAiException.ErrorCode.REQUEST_TOO_LARGE -> AiPromptTooLongException(this)
            GenAiException.ErrorCode.BUSY,
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
            -> AiBusyException(this)
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> AiBackgroundUseBlockedException(this)
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> AiNotEnoughStorageException(this)
            GenAiException.ErrorCode.NOT_AVAILABLE,
            GenAiException.ErrorCode.NOT_SUPPORTED,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
            -> AiFeatureUnavailableException(this)
            else -> this
        }

    private companion object {
        const val BUSY_RETRY_COUNT = 2
        val MIN_BUSY_RETRY_DELAY: Duration = 2.seconds
        val MAX_BUSY_RETRY_DELAY: Duration = 30.seconds

        /** Low, as in ML Kit's own examples: enough to reword an answer, not to make things up. */
        const val VARIED_WORDING_TEMPERATURE = 0.3f
        const val VARIED_WORDING_TOP_K = 10
    }
}
