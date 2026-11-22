package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiPromptTooLongException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.AiWordChoice
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.GeneratedText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Turns a student's daily observation notes over a date range into a
 * professional ESS (*Équipe de Suivi de Scolarisation*, the French school
 * follow-up meeting for students with disabilities) report, written by the
 * on-device [aiTextGenerationRepository] (Gemini Nano) — entirely offline,
 * so notes never leave the device.
 *
 * The on-device model only takes a few thousand tokens per request
 * (documented as "under 4000 tokens"), while an ESS usually covers a term
 * or a whole school year. When the notes of the period don't fit in one
 * prompt, they are summarized period by period first, and the report is
 * then written from those summaries (summarizing the summaries again if
 * needed), so any period can be reported on.
 *
 * Every request asks for [AiWordChoice.Varied] wording, so generating the
 * report again gives the user another draft rather than the same one.
 */
class GenerateEssReportUseCase(
    private val dailyReportRepository: DailyReportRepository,
    private val aiTextGenerationRepository: AiTextGenerationRepository,
) {
    /**
     * @param language The language the report is written in.
     * @param onProgress Told before each request to the model, so a report that needs several of them (each can take
     * a while on-device) can show how far along it is.
     * @return the generated report, as plain text, or a failed [Result] with [NoReportsInRangeException] when
     * [student] has no daily reports between [startDate] and [endDate] (inclusive), or whatever
     * [AiTextGenerationRepository.generate] failed with otherwise.
     */
    suspend operator fun invoke(
        student: Student,
        startDate: LocalDate,
        endDate: LocalDate,
        language: ReportLanguage,
        onProgress: (EssReportProgress) -> Unit = {},
    ): Result<GeneratedEssReport> {
        val reports =
            dailyReportRepository
                .observeReportsForStudent(student.id)
                .first()
                .filter { it.date in startDate..endDate }
                .sortedBy { it.date }

        if (reports.isEmpty()) {
            return Result.failure(NoReportsInRangeException(student.firstName))
        }

        return try {
            Result.success(writeReport(ReportRequest(student, startDate, endDate, EssReportPrompts.of(language)), reports, onProgress))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun writeReport(
        request: ReportRequest,
        reports: List<DailyReport>,
        onProgress: (EssReportProgress) -> Unit,
    ): GeneratedEssReport {
        val prompts = request.prompts
        var notes = reports.map { PeriodNotes(it.date, it.date, prompts.dailyNotes(it)) }
        var notesAreSummaries = false
        var completedSteps = 0
        repeat(MAX_SUMMARY_ROUNDS) {
            val reportPrompt = prompts.report(request.student, request.startDate, request.endDate, notes.joinedText(), notesAreSummaries)
            if (reportPrompt.length <= MAX_PROMPT_LENGTH) {
                onProgress(EssReportProgress(step = completedSteps + 1, stepCount = completedSteps + 1))
                val report = generate(reportPrompt)
                return GeneratedEssReport(text = report.plainText(), isCutOff = report.isCutOff)
            }

            val periods = groupIntoPeriods(notes, periodSummaryNotesBudget(request))
            val stepCount = completedSteps + periods.size + 1
            notes =
                periods.map { period ->
                    onProgress(EssReportProgress(step = ++completedSteps, stepCount = stepCount))
                    val firstDate = period.first().firstDate
                    val lastDate = period.last().lastDate
                    val summary = generate(prompts.periodSummary(request.student, period.joinedText()))
                    PeriodNotes(firstDate, lastDate, prompts.periodSummaryNotes(firstDate, lastDate, summary.plainText()))
                }
            notesAreSummaries = true
        }
        // Only reachable if the model keeps answering with summaries as long as what they summarize.
        throw AiPromptTooLongException()
    }

    private suspend fun generate(prompt: String): GeneratedText {
        currentCoroutineContext().ensureActive()
        return aiTextGenerationRepository.generate(prompt, AiWordChoice.Varied).getOrThrow()
    }

    /**
     * The answer without Markdown and, when the model was cut off, without the sentence it stopped in the middle of:
     * a summary or report never ends on half a sentence (but may lack whatever the model would have written next).
     */
    private fun GeneratedText.plainText(): String = text.withoutMarkdown().let { if (isCutOff) it.withoutUnfinishedSentence() else it }

    /** How long the notes of one period summary prompt may be, so the whole prompt stays within [MAX_PROMPT_LENGTH]. */
    private fun periodSummaryNotesBudget(request: ReportRequest): Int {
        val template = request.prompts.periodSummary(request.student, notes = "")
        return (MAX_PROMPT_LENGTH - template.length).coerceAtLeast(MIN_NOTES_BUDGET)
    }

    /** Splits [notes] into consecutive periods whose joined text fits [budget], cutting any single overlong note. */
    private fun groupIntoPeriods(
        notes: List<PeriodNotes>,
        budget: Int,
    ): List<List<PeriodNotes>> {
        val periods = mutableListOf<MutableList<PeriodNotes>>()
        var currentLength = 0
        for (piece in notes.flatMap { it.splitToFit(budget) }) {
            val lengthWithPiece = currentLength + NOTES_SEPARATOR.length + piece.text.length
            if (periods.isEmpty() || lengthWithPiece > budget) {
                periods += mutableListOf(piece)
                currentLength = piece.text.length
            } else {
                periods.last() += piece
                currentLength = lengthWithPiece
            }
        }
        return periods
    }

    private fun PeriodNotes.splitToFit(maxLength: Int): List<PeriodNotes> {
        if (text.length <= maxLength) return listOf(this)
        val parts = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxLength) {
            val cut = remaining.lastIndexOf(' ', maxLength).takeIf { it > 0 } ?: maxLength
            parts += remaining.substring(0, cut).trim()
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotEmpty()) parts += remaining
        return parts.map { copy(text = it) }
    }

    private fun List<PeriodNotes>.joinedText(): String = joinToString(NOTES_SEPARATOR) { it.text }

    private data class ReportRequest(
        val student: Student,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val prompts: EssReportPrompts,
    )

    /** Notes (one day's, or a summary of several days') covering [firstDate]..[lastDate]. */
    private data class PeriodNotes(
        val firstDate: LocalDate,
        val lastDate: LocalDate,
        val text: String,
    )

    private companion object {
        /**
         * Longest prompt sent in one request, in characters. Gemini Nano is documented to take "under 4000 tokens";
         * French prose averages about 4 characters per token, but dates and levels are digit-heavy and closer to
         * 2.5, so 6000 characters stays well under the limit and leaves room for the answer.
         */
        const val MAX_PROMPT_LENGTH = 6_000

        /** Floor for a period's notes, in case a very long student name or PPS made the template itself huge. */
        const val MIN_NOTES_BUDGET = 1_000

        /** A round summarizes the previous round's notes; each one shrinks them several times over, so a few suffice. */
        const val MAX_SUMMARY_ROUNDS = 4

        const val NOTES_SEPARATOR = "\n\n"
    }
}

/**
 * An ESS report written by the on-device model, as plain text.
 *
 * @property isCutOff Whether the model reached the longest answer it may write before finishing the report: the
 * sentence it was writing was dropped, so [text] ends on a complete one, but whatever came next is missing.
 */
data class GeneratedEssReport(
    val text: String,
    val isCutOff: Boolean = false,
)

/**
 * How far an ESS report generation is: request [step] of [stepCount] to the
 * on-device model. [stepCount] can still grow while summarizing a long
 * period, and is 1 when the notes fit in a single request.
 */
data class EssReportProgress(
    val step: Int,
    val stepCount: Int,
)

/** Thrown by [GenerateEssReportUseCase] when the student has no daily reports in the selected date range. */
class NoReportsInRangeException(
    val studentFirstName: String,
) : Exception("No daily reports found for $studentFirstName in the selected period.")

private val MARKDOWN_HEADING_REGEX = Regex("""^\s{0,3}#{1,6}\s+""")
private val MARKDOWN_BULLET_REGEX = Regex("""^(\s*)[*+-]\s+""")
private val MARKDOWN_STRONG_REGEX = Regex("""(\*\*|__)(.+?)\1""")
private val MARKDOWN_EMPHASIS_REGEX = Regex("""\*(\S(?:[^*\n]*\S)?)\*""")

/**
 * The model is asked for plain text, but instruction-tuned models often answer in Markdown anyway, which would show
 * up as stray asterisks and hashes in the report and its PDF. Headings become plain lines, bullets become "•".
 */
internal fun String.withoutMarkdown(): String =
    lines()
        .joinToString("\n") { line ->
            line
                .replace(MARKDOWN_HEADING_REGEX, "")
                .replace(MARKDOWN_BULLET_REGEX, "$1• ")
                .replace(MARKDOWN_STRONG_REGEX, "$2")
                .replace(MARKDOWN_EMPHASIS_REGEX, "$1")
        }.trim()

/**
 * The end of a sentence: ".", "!", "?" or "…", possibly closed by quotes or parentheses (with French spacing: "! »"),
 * then a space or the end of the text. The period of an initial ("M. Durand") doesn't end one.
 */
private val SENTENCE_END_REGEX = Regex("""(?<!\b\p{Lu})[.!?…](?:\s?["'»”)\]])*(?=\s|$)""")

/**
 * What's left of a text that was cut off mid-sentence once that unfinished sentence is dropped: everything up to the
 * last complete sentence, or the whole text when it has none (dropping it all would leave nothing to work from).
 */
internal fun String.withoutUnfinishedSentence(): String {
    val text = trimEnd()
    val lastSentenceEnd = SENTENCE_END_REGEX.findAll(text).lastOrNull() ?: return text
    return text.substring(0, lastSentenceEnd.range.last + 1)
}
