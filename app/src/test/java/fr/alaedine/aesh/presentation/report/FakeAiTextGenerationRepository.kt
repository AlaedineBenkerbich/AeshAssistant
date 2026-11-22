package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.AiWordChoice
import fr.alaedine.aesh.domain.repository.GeneratedText

/**
 * In-memory [AiTextGenerationRepository] test double, avoiding the need for
 * a mocking library or a real on-device Gemini Nano model to unit test
 * [fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase] and
 * [EssReportViewModel].
 *
 * @param result What every request returns, unless [respond] is given.
 * @param respond Answers each prompt instead of [result]: lets a test
 * answer differently per prompt, or suspend until it decides to answer.
 * @param isCutOff Whether the answer to a prompt reached the model's
 * longest answer before it was done, as the real model reports it.
 */
class FakeAiTextGenerationRepository(
    private val result: Result<String> = Result.success("Generated report text."),
    private val respond: (suspend (prompt: String) -> Result<String>)? = null,
    private val isCutOff: (prompt: String) -> Boolean = { false },
) : AiTextGenerationRepository {
    /** Every prompt received, in order. */
    val prompts = mutableListOf<String>()

    /** The word choice each of [prompts] was sent with. */
    val wordChoices = mutableListOf<AiWordChoice>()

    var lastPrompt: String? = null
        private set

    override suspend fun generate(
        prompt: String,
        wordChoice: AiWordChoice,
    ): Result<GeneratedText> {
        lastPrompt = prompt
        prompts += prompt
        wordChoices += wordChoice
        return (respond?.invoke(prompt) ?: result).map { GeneratedText(text = it, isCutOff = isCutOff(prompt)) }
    }
}
