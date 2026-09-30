package fr.alaedine.aesh.presentation.report.notes

import fr.alaedine.aesh.domain.repository.SpeechRecognitionRepository
import fr.alaedine.aesh.domain.repository.SpeechTranscript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scriptable [SpeechRecognitionRepository] test double, avoiding the need
 * for a real microphone or an on-device recognizer to unit test
 * [DictationViewModel].
 *
 * The first [listen] session emits [transcripts], then, if [finishesOnStop] is set,
 * keeps listening until [stopListening] is called and emits those
 * transcripts (mirroring a real recognizer delivering its final result when
 * asked to stop), then fails with [failure] if one is set.
 * Later sessions (the view model restarts listening after a pause) emit the
 * matching entry of [laterSessions], or nothing.
 */
class FakeSpeechRecognitionRepository(
    var isAvailableResult: Boolean = true,
    private val transcripts: List<SpeechTranscript> = emptyList(),
    private val finishesOnStop: List<SpeechTranscript>? = null,
    private val failure: Throwable? = null,
    private val laterSessions: List<List<SpeechTranscript>> = emptyList(),
) : SpeechRecognitionRepository {
    var listenCallCount = 0
        private set

    var stopListeningCallCount = 0
        private set

    private val stopRequested = CompletableDeferred<Unit>()

    override fun isAvailable(): Boolean = isAvailableResult

    override fun listen(): Flow<SpeechTranscript> =
        flow {
            val session = listenCallCount++
            (if (session == 0) transcripts else laterSessions.getOrElse(session - 1) { emptyList() }).forEach { emit(it) }
            if (finishesOnStop != null) {
                stopRequested.await()
                finishesOnStop.forEach { emit(it) }
            }
            failure?.let { throw it }
        }

    override fun stopListening() {
        stopListeningCallCount++
        stopRequested.complete(Unit)
    }
}
