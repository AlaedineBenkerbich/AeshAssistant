package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import java.time.LocalDate
import java.util.Locale

/**
 * The wording of what [GenerateEssReportUseCase] sends to the on-device
 * model, in one [ReportLanguage]: instructions, field labels and the
 * requested output language all match, since a small model tends to answer
 * in the language it is spoken to.
 *
 * Notes are kept compact (one short line of levels per day, empty fields
 * left out) because the model only takes a few thousand tokens per request.
 * Every prompt is built by appending the notes to an already trimmed
 * template, so the notes' own line breaks never end up re-indented.
 */
internal sealed class EssReportPrompts {
    protected abstract val levelsFormat: String
    protected abstract val obstaclesLabel: String
    protected abstract val supportStrategiesLabel: String
    protected abstract val freeNotesLabel: String

    /** One day of observations, as compact as possible without losing anything. */
    fun dailyNotes(report: DailyReport): String =
        buildString {
            append(
                levelsFormat.format(
                    Locale.ROOT,
                    report.date.toString(),
                    report.moodLevel,
                    report.focusLevel,
                    report.socialInteractions,
                    report.autonomyLevel,
                ),
            )
            appendField(obstaclesLabel, report.obstacles)
            appendField(supportStrategiesLabel, report.supportStrategies)
            appendField(freeNotesLabel, report.freeNotes)
        }

    /** A summary of the notes of [firstDate]..[lastDate], to be fed to a later prompt in place of those notes. */
    abstract fun periodSummaryNotes(
        firstDate: LocalDate,
        lastDate: LocalDate,
        summary: String,
    ): String

    /** Asks for a short summary of [notes] (daily notes or earlier summaries) about [firstDate]..[lastDate]. */
    abstract fun periodSummary(
        student: Student,
        firstDate: LocalDate,
        lastDate: LocalDate,
        notes: String,
    ): String

    /** Asks for the ESS report itself, from daily notes or, when [notesAreSummaries], from period summaries. */
    abstract fun report(
        student: Student,
        startDate: LocalDate,
        endDate: LocalDate,
        notes: String,
        notesAreSummaries: Boolean,
    ): String

    private fun StringBuilder.appendField(
        label: String,
        value: String,
    ) {
        val compactValue = value.trim().replace(WHITESPACE_REGEX, " ")
        if (compactValue.isNotEmpty()) append('\n').append(label).append(compactValue)
    }

    private data object French : EssReportPrompts() {
        override val levelsFormat = "%s : humeur %d/5, concentration %d/5, interactions sociales %d/5, autonomie %d/5"
        override val obstaclesLabel = "Obstacles : "
        override val supportStrategiesLabel = "Ce qui a aidé : "
        override val freeNotesLabel = "Notes : "

        override fun periodSummaryNotes(
            firstDate: LocalDate,
            lastDate: LocalDate,
            summary: String,
        ) = "Du $firstDate au $lastDate : $summary"

        override fun periodSummary(
            student: Student,
            firstDate: LocalDate,
            lastDate: LocalDate,
            notes: String,
        ) = buildString {
            append("Tu aides un ou une AESH à préparer la réunion d'ESS de ${student.firstName} (classe ${student.className}). ")
            append("Voici des notes sur cet élève du $firstDate au $lastDate.\n\n")
            append(PERIOD_SUMMARY_INSTRUCTIONS)
            append("\n\nNotes :\n")
            append(notes)
        }

        override fun report(
            student: Student,
            startDate: LocalDate,
            endDate: LocalDate,
            notes: String,
            notesAreSummaries: Boolean,
        ) = buildString {
            append(REPORT_CONTEXT)
            append("\n\nÉlève : ${student.firstName}, classe ${student.className}.")
            append("\nObjectifs du PPS : ${student.ppsGoals.trim().ifEmpty { "non renseignés" }}.")
            append("\nPériode : du $startDate au $endDate.\n\n")
            append(if (notesAreSummaries) REPORT_FROM_SUMMARIES_INSTRUCTIONS else REPORT_FROM_OBSERVATIONS_INSTRUCTIONS)
            append("\n\n")
            append(if (notesAreSummaries) SUMMARIES_HEADER else OBSERVATIONS_HEADER)
            append(notes)
        }

        private const val OBSERVATIONS_HEADER = "Observations quotidiennes (ordre chronologique) :\n"
        private const val SUMMARIES_HEADER = "Résumés par période (ordre chronologique) :\n"

        private val REPORT_CONTEXT =
            """
            Tu aides un ou une AESH (accompagnant d'élèves en situation de handicap) à rédiger la synthèse des
            observations d'un élève pour sa réunion d'ESS (équipe de suivi de la scolarisation).
            """.trimIndent()

        private val REPORT_FROM_OBSERVATIONS_INSTRUCTIONS =
            """
            À partir uniquement des observations quotidiennes ci-dessous, rédige en français une synthèse professionnelle,
            neutre et bienveillante de quelques paragraphes : progrès notables, difficultés récurrentes, aides et stratégies
            qui ont fonctionné, évolution de l'humeur, de la concentration, des interactions sociales et de l'autonomie.
            N'invente rien qui ne figure pas dans les notes. Écris en texte brut, sans mise en forme Markdown (ni astérisques,
            ni #). Les niveaux vont de 1 (le plus faible) à 5 (le plus élevé).
            """.trimIndent()

        private val REPORT_FROM_SUMMARIES_INSTRUCTIONS =
            """
            À partir uniquement des résumés ci-dessous, qui couvrent la période morceau par morceau, rédige en français une
            synthèse professionnelle, neutre et bienveillante de quelques paragraphes : progrès notables, difficultés
            récurrentes, aides et stratégies qui ont fonctionné, évolution de l'humeur, de la concentration, des interactions
            sociales et de l'autonomie. N'invente rien qui ne figure pas dans les résumés. Écris en texte brut, sans mise en
            forme Markdown (ni astérisques, ni #).
            """.trimIndent()

        private val PERIOD_SUMMARY_INSTRUCTIONS =
            """
            Résume-les en français en un seul paragraphe de 150 mots au plus : faits marquants, progrès, difficultés
            récurrentes, aides qui ont fonctionné et évolution des niveaux (1 est le plus faible, 5 le plus élevé).
            N'invente rien. Écris en texte brut, sans Markdown.
            """.trimIndent()
    }

    private data object English : EssReportPrompts() {
        override val levelsFormat = "%s: mood %d/5, focus %d/5, social interactions %d/5, autonomy %d/5"
        override val obstaclesLabel = "Obstacles: "
        override val supportStrategiesLabel = "What helped: "
        override val freeNotesLabel = "Notes: "

        override fun periodSummaryNotes(
            firstDate: LocalDate,
            lastDate: LocalDate,
            summary: String,
        ) = "$firstDate to $lastDate: $summary"

        override fun periodSummary(
            student: Student,
            firstDate: LocalDate,
            lastDate: LocalDate,
            notes: String,
        ) = buildString {
            append("You are helping a special needs teaching assistant (AESH) prepare the ESS school follow-up meeting of ")
            append("${student.firstName} (class ${student.className}). Below are notes about the student from $firstDate to $lastDate.\n\n")
            append(PERIOD_SUMMARY_INSTRUCTIONS)
            append("\n\nNotes:\n")
            append(notes)
        }

        override fun report(
            student: Student,
            startDate: LocalDate,
            endDate: LocalDate,
            notes: String,
            notesAreSummaries: Boolean,
        ) = buildString {
            append(REPORT_CONTEXT)
            append("\n\nStudent: ${student.firstName}, class ${student.className}.")
            append("\nPPS goals: ${student.ppsGoals.trim().ifEmpty { "not provided" }}.")
            append("\nPeriod: $startDate to $endDate.\n\n")
            append(if (notesAreSummaries) REPORT_FROM_SUMMARIES_INSTRUCTIONS else REPORT_FROM_OBSERVATIONS_INSTRUCTIONS)
            append("\n\n")
            append(if (notesAreSummaries) "Period summaries (chronological):\n" else "Daily observations (chronological):\n")
            append(notes)
        }

        private val REPORT_CONTEXT =
            """
            You are helping a special needs teaching assistant (AESH) write the summary of a student's observations for
            their ESS (Équipe de Suivi de Scolarisation) school follow-up meeting.
            """.trimIndent()

        private val REPORT_FROM_OBSERVATIONS_INSTRUCTIONS =
            """
            Using only the daily observations below, write in English a professional, neutral and caring summary of a few
            paragraphs: notable progress, recurring difficulties, support and strategies that worked, and how mood, focus,
            social interactions and autonomy evolved. Do not invent anything that isn't in the notes. Write plain text,
            without Markdown formatting (no asterisks, no #). Levels range from 1 (lowest) to 5 (highest).
            """.trimIndent()

        private val REPORT_FROM_SUMMARIES_INSTRUCTIONS =
            """
            Using only the summaries below, which cover the period piece by piece, write in English a professional, neutral
            and caring summary of a few paragraphs: notable progress, recurring difficulties, support and strategies that
            worked, and how mood, focus, social interactions and autonomy evolved. Do not invent anything that isn't in the
            summaries. Write plain text, without Markdown formatting (no asterisks, no #).
            """.trimIndent()

        private val PERIOD_SUMMARY_INSTRUCTIONS =
            """
            Summarize them in English in a single paragraph of at most 150 words: key facts, progress, recurring
            difficulties, support that worked, and level trends (1 is the lowest, 5 the highest). Do not invent anything.
            Write plain text, without Markdown.
            """.trimIndent()
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun of(language: ReportLanguage): EssReportPrompts =
            when (language) {
                ReportLanguage.French -> French
                ReportLanguage.English -> English
            }
    }
}
