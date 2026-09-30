package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.repository.SpeechToTextRepository

/** In-memory [SpeechToTextRepository] test double returning a canned transcript. */
class FakeSpeechToTextRepository(
    override val isAvailable: Boolean = true,
    private val result: Result<String> = Result.success("Spoken text."),
) : SpeechToTextRepository {
    var stopCount = 0
        private set

    override suspend fun listen(): Result<String> = result

    override fun stopListening() {
        stopCount++
    }
}
