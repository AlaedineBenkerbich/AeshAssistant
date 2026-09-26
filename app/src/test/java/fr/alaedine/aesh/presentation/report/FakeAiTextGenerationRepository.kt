package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository

/**
 * In-memory [AiTextGenerationRepository] test double, avoiding the need for
 * a mocking library or a real on-device Gemini Nano model to unit test
 * [fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase] and
 * [EssReportViewModel].
 */
class FakeAiTextGenerationRepository(
    private val result: Result<String> = Result.success("Generated report text."),
) : AiTextGenerationRepository {

    var lastPrompt: String? = null
        private set

    override suspend fun generate(prompt: String): Result<String> {
        lastPrompt = prompt
        return result
    }
}
