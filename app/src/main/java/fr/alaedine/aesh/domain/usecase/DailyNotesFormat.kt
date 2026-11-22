package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How observations are written into a prompt for the on-device model, in one
 * [ReportLanguage]: each day as a short line of levels, then each free-text
 * field that isn't empty, its whitespace collapsed. As compact as possible
 * without losing anything, since the model only takes a few thousand tokens
 * per request. Dates are written out ("1er septembre 2025"), since the model
 * copies the dates it is given: ISO dates would end up as is in what it
 * writes. Shared by [EssReportPrompts] and [SupportIdeasPrompts].
 */
internal sealed class DailyNotesFormat {
    protected abstract val levelsFormat: String
    protected abstract val obstaclesLabel: String
    protected abstract val supportStrategiesLabel: String
    protected abstract val freeNotesLabel: String

    /** [date] written out in this language, e.g. "1er septembre 2025" or "September 1, 2025". */
    abstract fun writtenOut(date: LocalDate): String

    /** One day of observations, as compact as possible without losing anything. */
    fun format(report: DailyReport): String =
        buildString {
            append(
                levelsFormat.format(
                    Locale.ROOT,
                    writtenOut(report.date),
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

    private fun StringBuilder.appendField(
        label: String,
        value: String,
    ) {
        val compactValue = value.trim().replace(WHITESPACE_REGEX, " ")
        if (compactValue.isNotEmpty()) append('\n').append(label).append(compactValue)
    }

    private data object French : DailyNotesFormat() {
        override val levelsFormat = "%s : humeur %d/5, concentration %d/5, interactions sociales %d/5, autonomie %d/5"
        override val obstaclesLabel = "Obstacles : "
        override val supportStrategiesLabel = "Ce qui a aidé : "
        override val freeNotesLabel = "Notes : "

        private val MONTH_AND_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

        override fun writtenOut(date: LocalDate): String {
            val day = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()
            return "$day ${MONTH_AND_YEAR_FORMATTER.format(date)}"
        }
    }

    private data object English : DailyNotesFormat() {
        override val levelsFormat = "%s: mood %d/5, focus %d/5, social interactions %d/5, autonomy %d/5"
        override val obstaclesLabel = "Obstacles: "
        override val supportStrategiesLabel = "What helped: "
        override val freeNotesLabel = "Notes: "

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

        override fun writtenOut(date: LocalDate): String = DATE_FORMATTER.format(date)
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun of(language: ReportLanguage): DailyNotesFormat =
            when (language) {
                ReportLanguage.French -> French
                ReportLanguage.English -> English
            }
    }
}
