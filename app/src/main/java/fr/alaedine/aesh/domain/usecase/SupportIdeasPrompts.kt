package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import java.util.Locale

/**
 * The wording of what [SuggestSupportIdeasUseCase] sends to the on-device
 * model, in one [ReportLanguage]: instructions, labels and the requested
 * output language all match, since a small model tends to answer in the
 * language it is spoken to.
 *
 * The answer is asked for as a short list, one idea per line followed by what
 * in the observations led to it, which [SupportIdeasResponseParser] reads
 * back; short, because the model is meant for answers of up to a couple of
 * hundred tokens. The average levels are worked out here rather than left to
 * the model, which is poor at arithmetic. The PPS goals are written as typed,
 * on a line of their own, so their own punctuation isn't doubled. The
 * observations come last, so a prompt cut to fit the model only loses the end
 * of the latest notes, never the instructions.
 */
internal sealed class SupportIdeasPrompts(
    private val dailyNotesFormat: DailyNotesFormat,
) {
    protected abstract val averagesFormat: String
    protected abstract val instructions: String
    protected abstract val observationsHeader: String

    protected abstract fun studentContext(student: Student): String

    /** Asks for support ideas for [student] from their [observations], in chronological order. */
    fun prompt(
        student: Student,
        observations: List<DailyReport>,
    ): String =
        buildString {
            append(studentContext(student))
            if (observations.isNotEmpty()) append('\n').append(averageLevels(observations))
            append("\n\n")
            append(instructions)
            append("\n\n")
            append(observationsHeader)
            observations.joinTo(this, separator = OBSERVATIONS_SEPARATOR) { dailyNotesFormat.format(it) }
        }

    private fun averageLevels(observations: List<DailyReport>): String =
        averagesFormat.format(
            Locale.ROOT,
            observations.map { it.moodLevel }.average(),
            observations.map { it.focusLevel }.average(),
            observations.map { it.socialInteractions }.average(),
            observations.map { it.autonomyLevel }.average(),
        )

    private data object French : SupportIdeasPrompts(DailyNotesFormat.of(ReportLanguage.French)) {
        override val averagesFormat =
            "Niveaux moyens sur ces observations (de 1, le plus faible, à 5, le plus élevé) : " +
                "humeur %.1f/5, concentration %.1f/5, interactions sociales %.1f/5, autonomie %.1f/5."

        override val observationsHeader = "Observations (ordre chronologique) :\n"

        override fun studentContext(student: Student) =
            "Tu aides un ou une AESH (accompagnant d'élèves en situation de handicap) à préparer l'accompagnement de " +
                "${student.firstName} (classe ${student.className}).\n" +
                "Objectifs du PPS : ${student.ppsGoals.trim().ifEmpty { "non renseignés" }}"

        override val instructions =
            """
            À partir uniquement des observations ci-dessous, propose 3 à 5 pistes concrètes à mettre en place en classe
            dans les prochaines semaines : poursuivre ce qui a aidé, répondre aux difficultés qui reviennent, avancer vers
            les objectifs du PPS.

            Règles :
            - Chaque piste s'appuie sur ce que disent les observations : n'invente rien.
            - Reste dans le rôle de l'AESH et de l'équipe enseignante : aucun diagnostic, aucun conseil médical.
            - Écris en français, en texte brut, sans Markdown.

            Réponds uniquement par la liste des pistes, une par ligne et en 30 mots au plus, sous cette forme :
            - <la piste, en une phrase> Pourquoi : <ce que montrent les observations, en une phrase courte>
            """.trimIndent()
    }

    private data object English : SupportIdeasPrompts(DailyNotesFormat.of(ReportLanguage.English)) {
        override val averagesFormat =
            "Average levels over these observations (from 1, the lowest, to 5, the highest): " +
                "mood %.1f/5, focus %.1f/5, social interactions %.1f/5, autonomy %.1f/5."

        override val observationsHeader = "Observations (chronological):\n"

        override fun studentContext(student: Student) =
            "You are helping a special needs teaching assistant (AESH) plan how to support ${student.firstName} " +
                "(class ${student.className}).\n" +
                "PPS goals: ${student.ppsGoals.trim().ifEmpty { "not provided" }}"

        override val instructions =
            """
            Using only the observations below, suggest 3 to 5 concrete things to put in place in class over the coming
            weeks: keep doing what helped, address the difficulties that keep coming back, and work towards the PPS goals.

            Rules:
            - Base every idea on what the observations say: do not invent anything.
            - Stay within the role of the AESH and the teaching team: no diagnosis, no medical advice.
            - Write in English, in plain text, without Markdown.

            Answer only with the list of ideas, one per line and at most 30 words each, in this form:
            - <the idea, in one sentence> Why: <what the observations show, in one short sentence>
            """.trimIndent()
    }

    companion object {
        private const val OBSERVATIONS_SEPARATOR = "\n\n"

        fun of(language: ReportLanguage): SupportIdeasPrompts =
            when (language) {
                ReportLanguage.French -> French
                ReportLanguage.English -> English
            }
    }
}
