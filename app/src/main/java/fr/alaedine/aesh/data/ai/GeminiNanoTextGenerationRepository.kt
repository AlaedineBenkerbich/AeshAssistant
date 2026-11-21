package fr.alaedine.aesh.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import fr.alaedine.aesh.domain.repository.AiBackgroundUseBlockedException
import fr.alaedine.aesh.domain.repository.AiBusyException
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiNotEnoughStorageException
import fr.alaedine.aesh.domain.repository.AiPromptTooLongException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
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

    override suspend fun generate(prompt: String): Result<String> =
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
                    generateRetryingWhileBusy(prompt)
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
    private suspend fun generateRetryingWhileBusy(prompt: String): String {
        repeat(BUSY_RETRY_COUNT) {
            try {
                return generateText(prompt)
            } catch (error: GenAiException) {
                if (error.errorCode != GenAiException.ErrorCode.BUSY) throw error
                delay(error.retryDelay?.toKotlinDuration()?.coerceIn(MIN_BUSY_RETRY_DELAY, MAX_BUSY_RETRY_DELAY) ?: MIN_BUSY_RETRY_DELAY)
            }
        }
        return generateText(prompt)
    }

    private suspend fun generateText(prompt: String): String {
        val text =
            generativeModel
                .generateContent(prompt)
                .candidates
                .firstOrNull()
                ?.text
        if (text.isNullOrBlank()) throw IllegalStateException("The on-device AI model returned an empty answer.")
        return text
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
    }
}
