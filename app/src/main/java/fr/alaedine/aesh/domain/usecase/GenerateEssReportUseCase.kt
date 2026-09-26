package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

/**
 * Turns a student's daily observation notes over a date range into a
 * professional ESS (*Équipe de Suivi de Scolarisation*, the French school
 * follow-up meeting for students with disabilities) report: concatenates
 * the matching [DailyReport]s into a specialized prompt, then runs it
 * through the on-device [aiTextGenerationRepository] (Gemini Nano) —
 * entirely offline, so notes never leave the device.
 */
class GenerateEssReportUseCase(
    private val dailyReportRepository: DailyReportRepository,
    private val aiTextGenerationRepository: AiTextGenerationRepository,
) {

    /**
     * @return the generated report text, or a failed [Result] with
     * [NoReportsInRangeException] when [student] has no daily reports
     * between [startDate] and [endDate] (inclusive), or whatever
     * [AiTextGenerationRepository.generate] failed with otherwise.
     */
    suspend operator fun invoke(student: Student, startDate: LocalDate, endDate: LocalDate): Result<String> {
        val reports = dailyReportRepository.observeReportsForStudent(student.id).first()
            .filter { it.date in startDate..endDate }
            .sortedBy { it.date }

        if (reports.isEmpty()) {
            return Result.failure(NoReportsInRangeException(student.firstName))
        }

        return aiTextGenerationRepository.generate(buildPrompt(student, reports))
    }

    /**
     * Every field of every report in range is included, not just
     * [DailyReport.freeNotes]: the mood/focus/social interaction levels give
     * the model useful signal (e.g. a trend) even on days where free-text
     * notes are sparse or empty.
     */
    private fun buildPrompt(student: Student, reports: List<DailyReport>): String {
        val notes = reports.joinToString(separator = "\n\n") { it.toPromptSection() }
        return """
            You are helping a special needs teaching assistant (AESH) write a professional,
            cohesive summary of a student's recent progress for an ESS (Équipe de Suivi de
            Scolarisation) school follow-up meeting.

            Student: ${student.firstName}, class ${student.className}.
            PPS goals: ${student.ppsGoals.ifBlank { "(none provided)" }}

            Using only the daily observation notes below, write a short, professional, neutral
            summary. Group similar observations together, highlight notable progress or
            recurring difficulties, and do not invent information that isn't in the notes. Write
            the summary in the same language as the notes below.

            Daily observation notes (chronological):
            $notes
        """.trimIndent()
    }

    private fun DailyReport.toPromptSection(): String =
        "Date: ${date.format(DATE_FORMATTER)}\n" +
            "Mood: $moodLevel/5 · Focus: $focusLevel/5 · Social interactions: $socialInteractions/5\n" +
            "Notes: ${freeNotes.ifBlank { "(none)" }}"

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}

/** Thrown by [GenerateEssReportUseCase] when the student has no daily reports in the selected date range. */
class NoReportsInRangeException(studentFirstName: String) :
    Exception("No daily reports found for $studentFirstName in the selected period.")
