package fr.alaedine.aesh.presentation.student.ideas

import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.model.SupportIdeas

/**
 * Immutable UI state rendered by [SupportIdeasScreen].
 *
 * @property student The student the ideas are for, as currently stored;
 * `null` while loading, or if they no longer exist.
 * @property observationCount How many daily observations the student has,
 * kept in sync so the screen knows whether there's anything to draw ideas
 * from.
 * @property isLoading Whether the student and their observations are still
 * being loaded.
 * @property isGenerating Whether the on-device model is working on ideas
 * (including any first-time model download, which can take a moment).
 * @property ideas The ideas last suggested; `null` before the first
 * successful suggestion. Kept while new ones are being suggested, so a
 * failed attempt loses nothing.
 * @property failure Why the last attempt failed, shown until the next one;
 * `null` when it didn't.
 */
data class SupportIdeasUiState(
    val student: Student? = null,
    val observationCount: Int = 0,
    val isLoading: Boolean = true,
    val isGenerating: Boolean = false,
    val ideas: SupportIdeas? = null,
    val failure: SupportIdeasFailure? = null,
) {
    /** Whether the student no longer exists, once loaded. */
    val isStudentMissing: Boolean get() = !isLoading && student == null

    /** Whether the student has no daily observation to draw ideas from yet. */
    val hasNoObservations: Boolean get() = !isLoading && student != null && observationCount == 0

    /** Whether ideas can be asked for: there are observations, and no request is already running. */
    val canSuggest: Boolean get() = student != null && observationCount > 0 && !isGenerating
}

/**
 * Why [SupportIdeasViewModel] couldn't suggest ideas.
 *
 * Kept as a semantic type rather than a raw `String` so the view model stays
 * free of Android resources; [SupportIdeasScreen] maps each variant to
 * localized text. Underlying exception messages are never shown: they are in
 * English and meant for developers.
 */
sealed interface SupportIdeasFailure {
    /** The on-device generative model can't run on this device, at least for now. */
    data object AiFeatureUnavailable : SupportIdeasFailure

    /** The model didn't answer (status check, download, or inference) within a reasonable time. */
    data object GenerationTimeout : SupportIdeasFailure

    /** The on-device model is busy, or this app used up its share of it for now. */
    data object AiBusy : SupportIdeasFailure

    /** The request stopped because the app left the screen, where Android doesn't let it use on-device AI. */
    data object BackgroundUseBlocked : SupportIdeasFailure

    /** There isn't enough free storage to download the on-device model. */
    data object NotEnoughStorage : SupportIdeasFailure

    /** Suggesting ideas failed for another, unexpected reason. */
    data object GenerationFailed : SupportIdeasFailure
}
