package fr.alaedine.aesh.domain.repository

import java.io.File

/**
 * Framework-agnostic contract for recognizing the text in a photo (a
 * physical schedule, handwritten observation notes...), implemented by
 * [fr.alaedine.aesh.data.scanner.MlKitTextRecognitionRepository] on top of
 * ML Kit's on-device Text Recognition. Kept separate from
 * [ScheduleSlotRepository] since it never touches persistence, only OCR;
 * what the recognized text *means* is left to its callers (see
 * [fr.alaedine.aesh.domain.scanner.ScheduleTextParser] and
 * [fr.alaedine.aesh.domain.usecase.SortObservationNotesUseCase]).
 */
interface TextRecognitionRepository {
    /**
     * Runs on-device text recognition on the photo at [imageFile].
     *
     * @return the recognized text (roughly one line per line of the photo,
     * empty if none was found), or a failed [Result] if recognition itself
     * failed (e.g. a corrupt image or an unreadable file).
     */
    suspend fun recognizeText(imageFile: File): Result<String>
}
