package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.repository.TextRecognitionRepository
import java.io.File

/**
 * Reads the text of one or more photos of handwritten observation notes
 * (e.g. the pages of a notebook) with on-device OCR, in the order the
 * photos were taken, so they can be sorted into the observation form's
 * fields by [SortObservationNotesUseCase].
 */
class RecognizeNotesFromPhotosUseCase(
    private val textRecognitionRepository: TextRecognitionRepository,
) {
    /**
     * @return the text of all [photos] joined together, one line or
     * sentence per line, or a failed [Result] with [NoTextRecognizedException]
     * when none of them contains any text. A photo that can't be read at
     * all fails the whole batch rather than being skipped, so the user
     * never silently loses a page of their notes.
     */
    suspend operator fun invoke(photos: List<File>): Result<String> {
        val recognizedTexts =
            photos.map { photo ->
                textRecognitionRepository.recognizeText(photo).getOrElse { return Result.failure(it) }
            }

        val notes =
            recognizedTexts
                .map { it.withWrappedLinesJoined().trim() }
                .filter { it.isNotEmpty() }
                .joinToString(separator = "\n")
        return if (notes.isEmpty()) Result.failure(NoTextRecognizedException()) else Result.success(notes)
    }

    /**
     * OCR keeps the page's line breaks, so a sentence written over two lines
     * comes back cut in two, and each half would then be sorted on its own
     * (possibly into different fields). On a page written in punctuated
     * sentences, a line is taken as the continuation of the previous one
     * when that one stops short of any punctuation while running close to
     * the full width of the page, and the next one starts in lowercase.
     * Short lines, and pages of brief unpunctuated notes, stay apart.
     */
    private fun String.withWrappedLinesJoined(): String {
        val lines = lines().map { it.trim() }
        if (lines.none { it.isNotEmpty() && it.last() in SENTENCE_END_CHARACTERS }) return lines.joinToString("\n")
        val longestLineLength = lines.maxOf { it.length }
        return buildString {
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    val previousLine = lines[index - 1]
                    val continuesPreviousLine =
                        previousLine.isNotEmpty() &&
                            line.firstOrNull()?.isLowerCase() == true &&
                            previousLine.last() !in SENTENCE_END_CHARACTERS &&
                            previousLine.length >= longestLineLength * WRAPPED_LINE_MIN_WIDTH_RATIO
                    append(if (continuesPreviousLine) ' ' else '\n')
                }
                append(line)
            }
        }
    }

    private companion object {
        const val SENTENCE_END_CHARACTERS = ".!?…:;"

        /** How close to the longest line of the page a line must run to have been cut by the edge of the page. */
        const val WRAPPED_LINE_MIN_WIDTH_RATIO = 0.6
    }
}

/** Thrown by [RecognizeNotesFromPhotosUseCase] when OCR ran but found no text in any of the photos. */
class NoTextRecognizedException : Exception("No text was recognized in the photos.")
