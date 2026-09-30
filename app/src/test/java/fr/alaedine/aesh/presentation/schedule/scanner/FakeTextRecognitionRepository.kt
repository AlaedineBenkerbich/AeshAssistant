package fr.alaedine.aesh.presentation.schedule.scanner

import fr.alaedine.aesh.domain.repository.TextRecognitionRepository
import java.io.File

/**
 * In-memory [TextRecognitionRepository] test double, avoiding the need for
 * a mocking library or real ML Kit/CameraX dependencies to unit test
 * [ScheduleScannerViewModel] and the observation notes scanner.
 *
 * Returns [result] for every file, unless [resultsByFileName] holds an
 * entry for that file's name (to give each photo of a multi-photo scan its
 * own text).
 */
class FakeTextRecognitionRepository(
    private val result: Result<String> = Result.success(""),
    private val resultsByFileName: Map<String, Result<String>> = emptyMap(),
) : TextRecognitionRepository {
    var lastRecognizedFile: File? = null
        private set

    val recognizedFiles = mutableListOf<File>()

    override suspend fun recognizeText(imageFile: File): Result<String> {
        lastRecognizedFile = imageFile
        recognizedFiles += imageFile
        return resultsByFileName[imageFile.name] ?: result
    }
}
