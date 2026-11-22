package fr.alaedine.aesh.domain.model

import java.time.LocalDate

/**
 * Something that could be put in place for a student, as suggested by the
 * on-device AI model from their daily observations (see
 * [fr.alaedine.aesh.domain.usecase.SuggestSupportIdeasUseCase]).
 *
 * @property text What to put in place, in a sentence.
 * @property reason What in the observations led to the idea, so the AESH can
 * judge whether it holds; empty when the model didn't say.
 */
data class SupportIdea(
    val text: String,
    val reason: String = "",
)

/**
 * The [ideas] suggested for a student, with the observations they were drawn
 * from: only the most recent ones fit in a request to the on-device model, so
 * the user is told which.
 *
 * @property observationCount How many daily observations the ideas are based on.
 * @property firstObservationDate The date of the oldest of those observations.
 * @property lastObservationDate The date of the most recent one.
 */
data class SupportIdeas(
    val ideas: List<SupportIdea>,
    val observationCount: Int,
    val firstObservationDate: LocalDate,
    val lastObservationDate: LocalDate,
)
