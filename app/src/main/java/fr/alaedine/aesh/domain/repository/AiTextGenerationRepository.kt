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
 * how [ScheduleScannerRepository] only recognizes raw text and leaves
 * business-specific parsing to [fr.alaedine.aesh.domain.scanner.ScheduleTextParser];
 * here, the specialized prompt engineering lives in
 * [fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase].
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

/** Thrown by [AiTextGenerationRepository.generate] when the on-device generative model isn't supported on this device. */
class AiFeatureUnavailableException : Exception("On-device AI isn't available on this device.")

/**
 * Thrown by [AiTextGenerationRepository.generate] when the on-device model
 * doesn't become ready (status check, download, or inference) within a
 * reasonable time, instead of leaving the caller suspended indefinitely —
 * see [fr.alaedine.aesh.data.ai.GeminiNanoTextGenerationRepository] for why
 * this can otherwise hang forever on some devices.
 */
class AiGenerationTimeoutException : Exception("On-device AI generation timed out.")
