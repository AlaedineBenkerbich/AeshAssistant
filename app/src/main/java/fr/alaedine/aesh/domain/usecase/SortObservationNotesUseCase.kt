package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.ObservationNotes
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Sorts notes that were dictated or photographed (rather than typed field
 * by field) into the daily observation form's three free-text fields —
 * obstacles, what helped, and other notes — using the on-device
 * [aiTextGenerationRepository] (Gemini Nano), entirely offline.
 *
 * The model is only asked to *classify* the notes' numbered lines, never to
 * rewrite them: each field is then assembled from the user's own lines. A
 * small on-device model can't be trusted to paraphrase a child's record
 * faithfully, and this way no word can be dropped, altered or invented. It
 * also keeps the model's answer to a few tokens, which keeps the wait short.
 *
 * Sorting is a convenience on top of capturing the notes, so it never gets
 * in the way: whenever it isn't possible (no on-device model, too slow,
 * unusable answer), the notes are returned unsorted instead, see
 * [NotesSortingResult.Unsorted].
 */
class SortObservationNotesUseCase(
    private val aiTextGenerationRepository: AiTextGenerationRepository,
) {
    /**
     * @param rawNotes The notes as dictated or recognized from photos, one
     * thought per line or sentence.
     */
    suspend operator fun invoke(rawNotes: String): NotesSortingResult {
        val lines = splitIntoLines(rawNotes)
        if (lines.isEmpty()) return NotesSortingResult.Sorted(ObservationNotes())

        val unsorted = ObservationNotes.unsorted(rawNotes)
        val generation =
            withTimeoutOrNull(SORTING_TIMEOUT) { aiTextGenerationRepository.generate(buildPrompt(lines)) }
                ?: return NotesSortingResult.Unsorted(unsorted, AiGenerationTimeoutException())
        val response =
            generation.getOrElse { error ->
                // The repository wraps everything in a Result, including the cancellation of this very coroutine.
                if (error is CancellationException) throw error
                return NotesSortingResult.Unsorted(unsorted, error)
            }

        val fieldByLine =
            parseFieldByLine(response, lines.size)
                ?: return NotesSortingResult.Unsorted(unsorted, UnrecognizedSortingResponseException())
        return NotesSortingResult.Sorted(assemble(lines, fieldByLine))
    }

    /** One line per sentence, so each thought can be filed on its own. */
    private fun splitIntoLines(rawNotes: String): List<String> =
        rawNotes
            .lines()
            .flatMap { SENTENCE_BOUNDARY_REGEX.split(it.trim()) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun buildPrompt(lines: List<String>): String {
        val numberedLines = lines.mapIndexed { index, line -> "[${index + 1}] $line" }.joinToString("\n")
        return "$PROMPT_INSTRUCTIONS\n$numberedLines\nAnswer:"
    }

    /**
     * @return for each line, the field the model filed it under ([NotesField.Notes] when it left a line
     * out, so nothing is lost), or `null` when the answer doesn't mention any field at all.
     */
    private fun parseFieldByLine(
        response: String,
        lineCount: Int,
    ): List<NotesField>? {
        val labels = FIELD_LABEL_REGEX.findAll(response).toList()
        if (labels.isEmpty()) return null

        val lineNumbersByField = NotesField.entries.associateWith { mutableSetOf<Int>() }
        labels.forEachIndexed { index, label ->
            val answerEnd = labels.getOrNull(index + 1)?.range?.first ?: response.length
            val answer = response.substring(label.range.last + 1, answerEnd)
            lineNumbersByField.getValue(NotesField.fromLabel(label.groupValues[1])) += parseLineNumbers(answer, lineCount)
        }
        // A line the model filed twice goes to the first field in declaration order, whatever the answer's order.
        return List(lineCount) { index ->
            NotesField.entries.firstOrNull { (index + 1) in lineNumbersByField.getValue(it) } ?: NotesField.Notes
        }
    }

    /** Reads "1, 4", "2 and 5" or "3-5" style answers, ignoring numbers that aren't a line of the notes. */
    private fun parseLineNumbers(
        answer: String,
        lineCount: Int,
    ): Set<Int> {
        val lineNumbers = mutableSetOf<Int>()
        val withoutRanges =
            LINE_RANGE_REGEX.replace(answer) { range ->
                val first = range.groupValues[1].toIntOrNull()
                val last = range.groupValues[2].toIntOrNull()
                if (first != null && last != null) lineNumbers += first.coerceAtLeast(1)..last.coerceAtMost(lineCount)
                " "
            }
        NUMBER_REGEX.findAll(withoutRanges).mapNotNullTo(lineNumbers) { it.value.toIntOrNull() }
        return lineNumbers.filterTo(mutableSetOf()) { it in 1..lineCount }
    }

    private fun assemble(
        lines: List<String>,
        fieldByLine: List<NotesField>,
    ): ObservationNotes {
        fun textOf(field: NotesField) = lines.filterIndexed { index, _ -> fieldByLine[index] == field }.joinToString("\n")
        return ObservationNotes(
            obstacles = textOf(NotesField.Obstacles),
            supportStrategies = textOf(NotesField.Helped),
            freeNotes = textOf(NotesField.Notes),
        )
    }

    /** Declaration order is the priority order when the model files one line under several fields. */
    private enum class NotesField(
        val label: String,
    ) {
        Obstacles("OBSTACLES"),
        Helped("HELPED"),
        Notes("NOTES"),
        ;

        companion object {
            fun fromLabel(label: String): NotesField = entries.first { it.label.equals(label, ignoreCase = true) }
        }
    }

    private companion object {
        val SORTING_TIMEOUT = 60.seconds

        // A sentence ends at ".", "!", "?" or "…" followed by a capitalized word or a number; requiring two
        // letters before the punctuation keeps abbreviations such as "M. Durand" in one piece.
        val SENTENCE_BOUNDARY_REGEX = Regex("""(?<=[\p{L}\p{N}]{2}[.!?…])\s+(?=[\p{Lu}\p{N}])""")

        // "OBSTACLES:", also when the model decorates it ("**Obstacles:**", "- OBSTACLES =").
        val FIELD_LABEL_REGEX = Regex("""\b(OBSTACLES|HELPED|NOTES)\b\W{0,4}?[:=]""", RegexOption.IGNORE_CASE)
        val LINE_RANGE_REGEX = Regex("""(\d+)\s*(?:-|–|—|to)\s*(\d+)""", RegexOption.IGNORE_CASE)
        val NUMBER_REGEX = Regex("""\d+""")

        val PROMPT_INSTRUCTIONS =
            """
            You are helping a special needs teaching assistant (AESH) sort the notes they took about a student's
            day into the three free-text fields of a daily observation form.

            The notes are split into numbered lines. Decide which field each line belongs to:
            OBSTACLES: difficulties the student ran into, or behaviours that got in the way.
            HELPED: support, strategies, tools or adjustments that helped the student.
            NOTES: anything else worth remembering (general remarks, progress, events, context).

            Rules:
            - Every line number goes in exactly one field. Do not rewrite or repeat the lines.
            - Answer with these three lines and nothing else, writing "none" for a field that gets no lines:
            OBSTACLES: <line numbers separated by commas>
            HELPED: <line numbers separated by commas>
            NOTES: <line numbers separated by commas>

            Example (only to show the format, it has nothing to do with the notes to sort)
            [1] Lina had trouble staying seated during the reading exercise.
            [2] The visual timer helped her refocus after each break.
            [3] Great mood all morning.
            [4] Needed the instructions read aloud twice.
            Answer:
            OBSTACLES: 1, 4
            HELPED: 2
            NOTES: 3

            Notes to sort
            """.trimIndent()
    }
}

/** What [SortObservationNotesUseCase] made of a set of notes. */
sealed interface NotesSortingResult {
    /** The notes as they should be added to the form's fields. */
    val notes: ObservationNotes

    /** The on-device model sorted the notes into the form's fields. */
    data class Sorted(
        override val notes: ObservationNotes,
    ) : NotesSortingResult

    /**
     * The notes couldn't be sorted, so [notes] holds them as they are, all in
     * the free notes field.
     *
     * @property cause Why: an [fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException],
     * an [AiGenerationTimeoutException], an [UnrecognizedSortingResponseException], or whatever else the
     * model failed with.
     */
    data class Unsorted(
        override val notes: ObservationNotes,
        val cause: Throwable,
    ) : NotesSortingResult
}

/** Thrown when the on-device model's answer doesn't say which field any line belongs to. */
class UnrecognizedSortingResponseException : Exception("The on-device AI model's answer couldn't be understood.")
