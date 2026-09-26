package fr.alaedine.aesh.presentation.schedule.scanner

import fr.alaedine.aesh.domain.repository.ScheduleScannerRepository
import java.io.File

/**
 * In-memory [ScheduleScannerRepository] test double, avoiding the need for
 * a mocking library or real ML Kit/CameraX dependencies to unit test
 * [ScheduleScannerViewModel].
 */
class FakeScheduleScannerRepository(
    private val result: Result<String> = Result.success(""),
) : ScheduleScannerRepository {

    var lastRecognizedFile: File? = null
        private set

    override suspend fun recognizeText(imageFile: File): Result<String> {
        lastRecognizedFile = imageFile
        return result
    }
}
