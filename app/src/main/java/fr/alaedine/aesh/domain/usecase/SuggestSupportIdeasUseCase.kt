package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.model.SupportIdea
import fr.alaedine.aesh.domain.model.SupportIdeas
import fr.alaedine.aesh.domain.repository.AiPromptTooLongException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.AiWordChoice
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.GeneratedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Suggests what could be put in place for a student — keep up what helped,
 * address what keeps getting in the way, work towards their PPS goals — from
 * their daily observations, written by the on-device
 * [aiTextGenerationRepository] (Gemini Nano), entirely offline, so notes never
 * leave the device.
 *
 * The ideas come from a single request to the model, holding the student's
 * most recent observations that fit in it: they are the most telling about
 * what to do next, and one request keeps the wait short. An ESS report, which
 * must cover a whole period, takes as many requests as that period needs
 * instead (see [GenerateEssReportUseCase]).
 *
 * Every request asks for [AiWordChoice.Varied] wording, so asking again gets
 * other ideas rather than the very same ones.
 */
class SuggestSupportIdeasUseCase(
    private val dailyReportRepository: DailyReportRepository,
    private val aiTextGenerationRepository: AiTextGenerationRepository,
) {
    /**
     * @param language The language the ideas are written in.
     * @return the ideas, or a failed [Result] with [NoObservationsException] when [student] has no daily
     * observations yet, [UnrecognizedSupportIdeasResponseException] when the model's answer holds no idea, or
     * whatever [AiTextGenerationRepository.generate] failed with otherwise.
     */
    suspend operator fun invoke(
        student: Student,
        language: ReportLanguage,
    ): Result<SupportIdeas> {
        val observationsNewestFirst =
            dailyReportRepository
                .observeReportsForStudent(student.id)
                .first()
                .sortedByDescending { it.date }

        if (observationsNewestFirst.isEmpty()) {
            return Result.failure(NoObservationsException(student.firstName))
        }

        val request = IdeasRequest(student, observationsNewestFirst, SupportIdeasPrompts.of(language))
        return try {
            Result.success(suggest(request))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun suggest(request: IdeasRequest): SupportIdeas =
        try {
            suggestWithin(MAX_PROMPT_LENGTH, request)
        } catch (tooLong: AiPromptTooLongException) {
            // The length limit only estimates the model's token limit, which digit-heavy notes reach sooner.
            suggestWithin(MAX_PROMPT_LENGTH / 2, request)
        }

    private suspend fun suggestWithin(
        maxPromptLength: Int,
        request: IdeasRequest,
    ): SupportIdeas {
        val observations = latestObservationsFitting(maxPromptLength, request)
        val prompt = request.prompts.prompt(request.student, observations).cutTo(maxPromptLength)

        currentCoroutineContext().ensureActive()
        val answer = aiTextGenerationRepository.generate(prompt, AiWordChoice.Varied).getOrThrow()
        val ideas = answer.ideas()
        if (ideas.isEmpty()) throw UnrecognizedSupportIdeasResponseException()

        return SupportIdeas(
            ideas = ideas,
            observationCount = observations.size,
            firstObservationDate = observations.first().date,
            lastObservationDate = observations.last().date,
        )
    }

    /**
     * The ideas in the answer. When the model was cut off, it stopped in the middle of the last one, which is left
     * out, unless it's the only one: a shorter list beats an idea that ends mid-sentence.
     */
    private fun GeneratedText.ideas(): List<SupportIdea> {
        val ideas = SupportIdeasResponseParser.parse(text)
        return if (isCutOff && ideas.size > 1) ideas.dropLast(1) else ideas
    }

    /**
     * The most recent observations whose prompt stays within [maxPromptLength], in chronological order. Always at
     * least the latest one, even when it's too long on its own: its prompt is then cut (see [cutTo]).
     */
    private fun latestObservationsFitting(
        maxPromptLength: Int,
        request: IdeasRequest,
    ): List<DailyReport> {
        val observations = ArrayDeque<DailyReport>()
        for (observation in request.observationsNewestFirst) {
            observations.addFirst(observation)
            if (observations.size > 1 && request.prompts.prompt(request.student, observations).length > maxPromptLength) {
                observations.removeFirst()
                break
            }
        }
        return observations.toList()
    }

    /** The prompt cut at a word boundary to [maxLength]: the observations come last, so only the end of the latest notes can be lost. */
    private fun String.cutTo(maxLength: Int): String {
        if (length <= maxLength) return this
        val cut = lastIndexOf(' ', maxLength).takeIf { it > 0 } ?: maxLength
        return substring(0, cut)
    }

    private data class IdeasRequest(
        val student: Student,
        val observationsNewestFirst: List<DailyReport>,
        val prompts: SupportIdeasPrompts,
    )

    private companion object {
        /** Same budget as an ESS report's requests, see [GenerateEssReportUseCase]: well under the model's input limit. */
        const val MAX_PROMPT_LENGTH = 6_000
    }
}

/** Thrown by [SuggestSupportIdeasUseCase] when the student has no daily observations to draw ideas from yet. */
class NoObservationsException(
    val studentFirstName: String,
) : Exception("No daily observations found for $studentFirstName yet.")

/** Thrown by [SuggestSupportIdeasUseCase] when the on-device model's answer holds no idea at all. */
class UnrecognizedSupportIdeasResponseException : Exception("The on-device AI model's answer held no support idea.")
