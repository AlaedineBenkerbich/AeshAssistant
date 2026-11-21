package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository

/**
 * In-memory [AiTextGenerationRepository] test double, avoiding the need for
 * a mocking library or a real on-device Gemini Nano model to unit test
 * [fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase] and
 * [EssReportViewModel].
 *
 * @param result What every request returns, unless [respond] is given.
 * @param respond Answers each prompt instead of [result]: lets a test
 * answer differently per prompt, or suspend until it decides to answer.
 */
class FakeAiTextGenerationRepository(
    private val result: Result<String> = Result.success("Generated report text."),
    private val respond: (suspend (prompt: String) -> Result<String>)? = null,
) : AiTextGenerationRepository {
    /** Every prompt received, in order. */
    val prompts = mutableListOf<String>()

    var lastPrompt: String? = null
        private set

    override suspend fun generate(prompt: String): Result<String> {
        lastPrompt = prompt
        prompts += prompt
        return respond?.invoke(prompt) ?: result
    }
}
