package fr.alaedine.aesh.domain.repository

/**
 * Framework-agnostic contract for turning a text prompt into natural
 * language output using an on-device generative model, implemented by
 * [fr.alaedine.aesh.data.ai.GeminiNanoTextGenerationRepository] on top of
 * Gemini Nano (via ML Kit's GenAI Prompt API, running through Android's
 * AICore system service).
 *
 * Every prompt and generated response stays entirely on-device — nothing is
 * ever sent to a server, see the project README's privacy section. Kept
 * generic (raw prompt in, raw text out) rather than ESS-specific, mirroring
 * how [TextRecognitionRepository] only recognizes raw text and leaves
 * business-specific parsing to [fr.alaedine.aesh.domain.scanner.ScheduleTextParser];
 * here, the specialized prompt engineering lives in
 * [fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase] and
 * [fr.alaedine.aesh.domain.usecase.SortObservationNotesUseCase].
 */
interface AiTextGenerationRepository {
    /**
     * Runs [prompt] through the on-device generative model, transparently
     * downloading it first if it isn't already present on this device (only
     * needed the first time any app uses an on-device GenAI feature).
     *
     * @return the generated text, or a failed [Result] if this device
     * doesn't support the on-device model, or generation otherwise failed.
     */
    suspend fun generate(prompt: String): Result<String>
}

/**
 * Thrown by [AiTextGenerationRepository.generate] when the on-device generative model can't run on this device:
 * unsupported hardware, or system AI services (AICore) that are missing, outdated or not set up yet.
 */
class AiFeatureUnavailableException(
    cause: Throwable? = null,
) : Exception("On-device AI isn't available on this device.", cause)

/**
 * Thrown by [AiTextGenerationRepository.generate] when the on-device model
 * doesn't become ready (status check, download, or inference) within a
 * reasonable time, instead of leaving the caller suspended indefinitely —
 * see [fr.alaedine.aesh.data.ai.GeminiNanoTextGenerationRepository] for why
 * this can otherwise hang forever on some devices.
 */
class AiGenerationTimeoutException : Exception("On-device AI generation timed out.")

/** Thrown by [AiTextGenerationRepository.generate] when the prompt is longer than the on-device model accepts. */
class AiPromptTooLongException(
    cause: Throwable? = null,
) : Exception("The request is too long for the on-device AI model.", cause)

/**
 * Thrown by [AiTextGenerationRepository.generate] when the on-device model is busy, or when this app has used up
 * its share of on-device AI for now (Android's AICore rations it per app): trying again a bit later can work.
 */
class AiBusyException(
    cause: Throwable? = null,
) : Exception("The on-device AI model is busy.", cause)

/** Thrown by [AiTextGenerationRepository.generate] when the app left the screen: Android only lets the app on screen use on-device AI. */
class AiBackgroundUseBlockedException(
    cause: Throwable? = null,
) : Exception("On-device AI can only be used while the app is on screen.", cause)

/** Thrown by [AiTextGenerationRepository.generate] when there isn't enough free storage to download the on-device model. */
class AiNotEnoughStorageException(
    cause: Throwable? = null,
) : Exception("Not enough free storage to download the on-device AI model.", cause)
