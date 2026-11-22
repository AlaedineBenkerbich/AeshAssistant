package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import java.time.LocalDate

/**
 * The wording of what [GenerateEssReportUseCase] sends to the on-device
 * model, in one [ReportLanguage]: instructions, field labels and the
 * requested output language all match, since a small model tends to answer
 * in the language it is spoken to.
 *
 * Notes are written by [dailyNotesFormat]: compact, because the model only
 * takes a few thousand tokens per request, and with their dates written out
 * the way the report should write them ("1er septembre 2025"), since the
 * model copies the dates it is given. Every prompt is built by appending the
 * notes to an already trimmed template, so the notes' own line breaks never
 * end up re-indented.
 */
internal sealed class EssReportPrompts(
    private val dailyNotesFormat: DailyNotesFormat,
) {
    /** [date] written out in this language, e.g. "1er septembre 2025" or "September 1, 2025". */
    protected fun writtenOut(date: LocalDate): String = dailyNotesFormat.writtenOut(date)

    /** One day of observations, as compact as possible without losing anything. */
    fun dailyNotes(report: DailyReport): String = dailyNotesFormat.format(report)

    /** A summary of the notes of [firstDate]..[lastDate], to be fed to a later prompt in place of those notes. */
    abstract fun periodSummaryNotes(
        firstDate: LocalDate,
        lastDate: LocalDate,
        summary: String,
    ): String

    /**
     * Asks for a short summary of [notes] (daily notes or earlier summaries). The notes carry their own dates, so the
     * prompt's length only depends on them: the use case can tell how many notes fit before picking them.
     */
    abstract fun periodSummary(
        student: Student,
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

    private data object French : EssReportPrompts(DailyNotesFormat.of(ReportLanguage.French)) {
        override fun periodSummaryNotes(
            firstDate: LocalDate,
            lastDate: LocalDate,
            summary: String,
        ) = if (firstDate == lastDate) {
            "Le ${writtenOut(firstDate)} : $summary"
        } else {
            "Du ${writtenOut(firstDate)} au ${writtenOut(lastDate)} : $summary"
        }

        override fun periodSummary(
            student: Student,
            notes: String,
        ) = buildString {
            append("Tu aides un ou une AESH à préparer la réunion d'ESS de ${student.firstName} (classe ${student.className}). ")
            append("Voici des notes sur cet élève, dans l'ordre chronologique.\n\n")
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
            append("\nPériode : du ${writtenOut(startDate)} au ${writtenOut(endDate)}.\n\n")
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
            neutre et bienveillante de quelques paragraphes, en $REPORT_MAX_WORDS mots au plus : progrès notables,
            difficultés récurrentes, aides et stratégies qui ont fonctionné, évolution de l'humeur, de la concentration,
            des interactions sociales et de l'autonomie. N'invente rien qui ne figure pas dans les notes. Commence
            directement par la synthèse, sans titre ni phrase d'introduction, et écris en texte brut, sans mise en forme
            Markdown (ni astérisques, ni #). Les niveaux vont de 1 (le plus faible) à 5 (le plus élevé).
            """.trimIndent()

        private val REPORT_FROM_SUMMARIES_INSTRUCTIONS =
            """
            À partir uniquement des résumés ci-dessous, qui couvrent la période morceau par morceau, rédige en français une
            synthèse professionnelle, neutre et bienveillante de quelques paragraphes, en $REPORT_MAX_WORDS mots au plus :
            progrès notables, difficultés récurrentes, aides et stratégies qui ont fonctionné, évolution de l'humeur, de la
            concentration, des interactions sociales et de l'autonomie. N'invente rien qui ne figure pas dans les résumés.
            Commence directement par la synthèse, sans titre ni phrase d'introduction, et écris en texte brut, sans mise en
            forme Markdown (ni astérisques, ni #).
            """.trimIndent()

        private val PERIOD_SUMMARY_INSTRUCTIONS =
            """
            Résume-les en français en un seul paragraphe de $PERIOD_SUMMARY_MAX_WORDS mots au plus : faits marquants,
            progrès, difficultés récurrentes, aides qui ont fonctionné et évolution des niveaux (1 est le plus faible, 5 le
            plus élevé). N'invente rien. Commence directement par le résumé et écris en texte brut, sans Markdown.
            """.trimIndent()
    }

    private data object English : EssReportPrompts(DailyNotesFormat.of(ReportLanguage.English)) {
        override fun periodSummaryNotes(
            firstDate: LocalDate,
            lastDate: LocalDate,
            summary: String,
        ) = if (firstDate == lastDate) {
            "On ${writtenOut(firstDate)}: $summary"
        } else {
            "From ${writtenOut(firstDate)} to ${writtenOut(lastDate)}: $summary"
        }

        override fun periodSummary(
            student: Student,
            notes: String,
        ) = buildString {
            append("You are helping a special needs teaching assistant (AESH) prepare the ESS school follow-up meeting of ")
            append("${student.firstName} (class ${student.className}). Below are notes about the student, in chronological order.\n\n")
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
            append("\nPeriod: ${writtenOut(startDate)} to ${writtenOut(endDate)}.\n\n")
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
            paragraphs, in at most $REPORT_MAX_WORDS words: notable progress, recurring difficulties, support and strategies
            that worked, and how mood, focus, social interactions and autonomy evolved. Do not invent anything that isn't
            in the notes. Start directly with the summary, without a title or an introductory sentence, and write plain
            text, without Markdown formatting (no asterisks, no #). Levels range from 1 (lowest) to 5 (highest).
            """.trimIndent()

        private val REPORT_FROM_SUMMARIES_INSTRUCTIONS =
            """
            Using only the summaries below, which cover the period piece by piece, write in English a professional, neutral
            and caring summary of a few paragraphs, in at most $REPORT_MAX_WORDS words: notable progress, recurring
            difficulties, support and strategies that worked, and how mood, focus, social interactions and autonomy
            evolved. Do not invent anything that isn't in the summaries. Start directly with the summary, without a title
            or an introductory sentence, and write plain text, without Markdown formatting (no asterisks, no #).
            """.trimIndent()

        private val PERIOD_SUMMARY_INSTRUCTIONS =
            """
            Summarize them in English in a single paragraph of at most $PERIOD_SUMMARY_MAX_WORDS words: key facts, progress,
            recurring difficulties, support that worked, and level trends (1 is the lowest, 5 the highest). Do not invent
            anything. Start directly with the summary and write plain text, without Markdown.
            """.trimIndent()
    }

    companion object {
        /**
         * The on-device model stops at a length set by its configuration, which can be shorter than "a few
         * paragraphs" left unbounded; asking for a bounded length keeps the report, and each summary, within it.
         */
        private const val REPORT_MAX_WORDS = 350
        private const val PERIOD_SUMMARY_MAX_WORDS = 150

        fun of(language: ReportLanguage): EssReportPrompts =
            when (language) {
                ReportLanguage.French -> French
                ReportLanguage.English -> English
            }
    }
}
