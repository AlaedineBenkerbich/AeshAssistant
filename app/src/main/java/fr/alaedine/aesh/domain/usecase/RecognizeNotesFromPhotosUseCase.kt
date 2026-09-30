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
     * @return the text of all [photos] joined together, one line of the
     * photos per line, or a failed [Result] with [NoTextRecognizedException]
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
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(separator = "\n")
        return if (notes.isEmpty()) Result.failure(NoTextRecognizedException()) else Result.success(notes)
    }
}

/** Thrown by [RecognizeNotesFromPhotosUseCase] when OCR ran but found no text in any of the photos. */
class NoTextRecognizedException : Exception("No text was recognized in the photos.")
