package fr.alaedine.aesh.domain.repository

import java.io.File

/**
 * Framework-agnostic contract for recognizing text from a photo of a
 * physical schedule, implemented by
 * [fr.alaedine.aesh.data.scanner.MlKitScheduleScannerRepository] on top of
 * ML Kit's on-device Text Recognition. Kept separate from
 * [ScheduleSlotRepository] since it never touches persistence, only OCR.
 */
interface ScheduleScannerRepository {

    /**
     * Runs on-device text recognition on the photo at [imageFile].
     *
     * @return the recognized text (roughly one line per line of the photo,
     * empty if none was found), or a failed [Result] if recognition itself
     * failed (e.g. a corrupt image or an unreadable file).
     */
    suspend fun recognizeText(imageFile: File): Result<String>
}
