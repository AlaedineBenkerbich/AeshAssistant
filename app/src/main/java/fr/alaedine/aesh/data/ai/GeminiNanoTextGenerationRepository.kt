package fr.alaedine.aesh.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository

/**
 * [AiTextGenerationRepository] backed by ML Kit's on-device GenAI Prompt
 * API, which runs Gemini Nano through Android's AICore system service — no
 * network calls, nothing ever leaves the device (see the project README's
 * privacy section).
 *
 * The underlying model client is lazily created and reused for the
 * lifetime of the process, mirroring how
 * [fr.alaedine.aesh.data.scanner.MlKitScheduleScannerRepository] reuses its
 * ML Kit recognizer.
 */
class GeminiNanoTextGenerationRepository : AiTextGenerationRepository {

    private val generativeModel by lazy { Generation.getClient() }

    override suspend fun generate(prompt: String): Result<String> = runCatching {
        when (generativeModel.checkStatus()) {
            FeatureStatus.UNAVAILABLE -> throw AiFeatureUnavailableException()
            FeatureStatus.AVAILABLE -> Unit
            // DOWNLOADABLE or DOWNLOADING: either way, awaiting the download
            // flow below suspends until the model is ready to run.
            else -> awaitModelDownload()
        }
        generativeModel.generateContent(prompt).candidates.first().text
    }

    /** Suspends until the on-device model finishes downloading, throwing if the download itself fails. */
    private suspend fun awaitModelDownload() {
        generativeModel.download().collect { status ->
            if (status is DownloadStatus.DownloadFailed) throw status.e
        }
    }
}
