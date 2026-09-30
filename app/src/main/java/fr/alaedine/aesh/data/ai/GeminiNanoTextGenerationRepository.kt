package fr.alaedine.aesh.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

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
     * model over a slow connection.
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
                    generativeModel
                        .generateContent(prompt)
                        .candidates
                        .first()
                        .text
                }
            } catch (timeout: TimeoutCancellationException) {
                throw AiGenerationTimeoutException()
            }
        }

    /** Suspends until the on-device model finishes downloading, throwing if the download itself fails. */
    private suspend fun awaitModelDownload() {
        generativeModel.download().collect { status ->
            if (status is DownloadStatus.DownloadFailed) throw status.e
        }
    }
}
