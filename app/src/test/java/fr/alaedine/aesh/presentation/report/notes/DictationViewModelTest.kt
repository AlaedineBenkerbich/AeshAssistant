package fr.alaedine.aesh.presentation.report.notes

import fr.alaedine.aesh.domain.repository.SpeechRecognitionException
import fr.alaedine.aesh.domain.repository.SpeechTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DictationViewModel] listens through `viewModelScope`, which requires the
 * `Main` dispatcher to be available; [UnconfinedTestDispatcher] makes
 * coroutines launched on it run eagerly so state updates are visible
 * immediately, without needing manual virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DictationViewModelTest {
    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun partial(text: String) = SpeechTranscript(text = text, isFinal = false)

    private fun final(text: String) = SpeechTranscript(text = text, isFinal = true)

    @Test
    fun `should expose that dictation is available when the device has an on-device recognizer`() =
        runTest {
            // Given / When
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(isAvailableResult = true))

            // Then
            assertTrue(viewModel.uiState.value.isAvailable)
        }

    @Test
    fun `should expose that dictation is unavailable when the device has no on-device recognizer`() =
        runTest {
            // Given / When
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(isAvailableResult = false))

            // Then so the screen can skip asking for the microphone permission
            assertFalse(viewModel.uiState.value.isAvailable)
        }

    @Test
    fun `should expose the transcript when the recognizer delivers its final result`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(
                    FakeSpeechRecognitionRepository(transcripts = listOf(partial("il a"), final("il a eu du mal à lire"))),
                )

            // When
            viewModel.onStartClicked()

            // Then
            val state = viewModel.uiState.value
            assertEquals("il a eu du mal à lire", state.transcript)
            assertFalse(state.isListening)
            assertEquals("", state.partialTranscript)
            assertNull(state.error)
        }

    @Test
    fun `should trim the transcript when the recognizer pads it with whitespace`() =
        runTest {
            // Given
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(transcripts = listOf(final("  il a souri \n"))))

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals("il a souri", viewModel.uiState.value.transcript)
        }

    @Test
    fun `should show what has been recognized so far when the recognizer emits partial results`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(
                    FakeSpeechRecognitionRepository(
                        transcripts = listOf(partial("il a"), partial("il a eu du")),
                        finishesOnStop = emptyList(),
                    ),
                )

            // When
            viewModel.onStartClicked()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state.isListening)
            assertEquals("il a eu du", state.partialTranscript)
            assertNull(state.transcript)
        }

    @Test
    fun `should ask the recognizer to stop and use its final result when the user is done`() =
        runTest {
            // Given
            val repository =
                FakeSpeechRecognitionRepository(
                    transcripts = listOf(partial("il a eu")),
                    finishesOnStop = listOf(final("il a eu du mal")),
                )
            val viewModel = DictationViewModel(repository)
            viewModel.onStartClicked()

            // When
            viewModel.onStopClicked()

            // Then
            assertEquals(1, repository.stopListeningCallCount)
            assertEquals("il a eu du mal", viewModel.uiState.value.transcript)
            assertFalse(viewModel.uiState.value.isListening)
        }

    @Test
    fun `should report that dictation is unavailable when the device has no on-device recognizer`() =
        runTest {
            // Given
            val repository = FakeSpeechRecognitionRepository(isAvailableResult = false)
            val viewModel = DictationViewModel(repository)

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(DictationError.Unavailable, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isListening)
            assertEquals(0, repository.listenCallCount)
        }

    @Test
    fun `should report no speech when the recognizer hears nothing`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(FakeSpeechRecognitionRepository(failure = SpeechRecognitionException.NoSpeechDetected()))

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(DictationError.NoSpeechDetected, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.transcript)
            assertFalse(viewModel.uiState.value.isListening)
        }

    @Test
    fun `should report no speech when the final transcript is blank`() =
        runTest {
            // Given
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(transcripts = listOf(final("   "))))

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(DictationError.NoSpeechDetected, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.transcript)
        }

    @Test
    fun `should report that the language is not installed when the recognizer lacks its model`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(FakeSpeechRecognitionRepository(failure = SpeechRecognitionException.LanguageNotInstalled()))

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(DictationError.LanguageNotInstalled, viewModel.uiState.value.error)
        }

    @Test
    fun `should report that microphone access was denied when the recognizer says so`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(FakeSpeechRecognitionRepository(failure = SpeechRecognitionException.MicrophonePermissionDenied()))

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(DictationError.MicrophonePermissionDenied, viewModel.uiState.value.error)
        }

    @Test
    fun `should report a generic failure when the recognizer fails for any other reason`() =
        runTest {
            // Given
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(failure = SpeechRecognitionException.Failed(errorCode = 5)))

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(DictationError.Failed, viewModel.uiState.value.error)
        }

    @Test
    fun `should report a generic failure when the recognizer throws an unexpected exception`() =
        runTest {
            // Given
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(failure = IllegalStateException("boom")))

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(DictationError.Failed, viewModel.uiState.value.error)
        }

    @Test
    fun `should keep what was heard when the recognizer fails after partial results`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(
                    FakeSpeechRecognitionRepository(
                        transcripts = listOf(partial("il a eu du mal à lire")),
                        failure = SpeechRecognitionException.NoSpeechDetected(),
                    ),
                )

            // When
            viewModel.onStartClicked()

            // Then a failure never throws away what the user already said
            assertEquals("il a eu du mal à lire", viewModel.uiState.value.transcript)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `should discard everything when the dictation is cancelled`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(
                    FakeSpeechRecognitionRepository(
                        transcripts = listOf(partial("il a eu")),
                        finishesOnStop = listOf(final("il a eu du mal")),
                    ),
                )
            viewModel.onStartClicked()

            // When
            viewModel.onCancelled()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isListening)
            assertEquals("", state.partialTranscript)
            assertNull(state.transcript)
            assertNull(state.error)
        }

    @Test
    fun `should not start a second session when already listening`() =
        runTest {
            // Given
            val repository = FakeSpeechRecognitionRepository(finishesOnStop = emptyList())
            val viewModel = DictationViewModel(repository)
            viewModel.onStartClicked()

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals(1, repository.listenCallCount)
        }

    @Test
    fun `should report that microphone access is needed when the user refuses the permission`() =
        runTest {
            // Given
            val repository = FakeSpeechRecognitionRepository()
            val viewModel = DictationViewModel(repository)

            // When
            viewModel.onMicrophonePermissionDenied()

            // Then
            assertEquals(DictationError.MicrophonePermissionDenied, viewModel.uiState.value.error)
            assertEquals(0, repository.listenCallCount)
        }

    @Test
    fun `should clear the transcript when it has been consumed`() =
        runTest {
            // Given
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(transcripts = listOf(final("il a souri"))))
            viewModel.onStartClicked()

            // When
            viewModel.onTranscriptConsumed()

            // Then
            assertNull(viewModel.uiState.value.transcript)
        }

    @Test
    fun `should clear the error when it has been shown`() =
        runTest {
            // Given
            val viewModel = DictationViewModel(FakeSpeechRecognitionRepository(isAvailableResult = false))
            viewModel.onStartClicked()

            // When
            viewModel.onErrorShown()

            // Then
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `should be able to dictate again when the previous session failed`() =
        runTest {
            // Given
            val repository = FakeSpeechRecognitionRepository(failure = SpeechRecognitionException.NoSpeechDetected())
            val viewModel = DictationViewModel(repository)
            viewModel.onStartClicked()

            // When
            viewModel.onStartClicked()

            // Then a failed session doesn't leave the view model stuck listening
            assertFalse(viewModel.uiState.value.isListening)
            assertTrue(repository.listenCallCount > 1)
        }

    @Test
    fun `should keep listening and keep the text when the user pauses to think`() =
        runTest {
            // Given the recognizer ends its first session at the pause, then the user carries on
            val repository =
                FakeSpeechRecognitionRepository(
                    transcripts = listOf(partial("il a eu"), final("il a eu du mal"), final("")),
                    laterSessions = listOf(listOf(partial("à lire"))),
                )
            val viewModel = DictationViewModel(repository)

            // When
            viewModel.onStartClicked()

            // Then the first part is kept, followed by the new words
            assertEquals("il a eu du mal à lire", viewModel.uiState.value.transcript)
        }

    @Test
    fun `should not erase what was heard when the recognizer emits a blank result`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(
                    FakeSpeechRecognitionRepository(transcripts = listOf(partial("il a souri"), partial(""), final(""))),
                )

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals("il a souri", viewModel.uiState.value.transcript)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `should join what was said before and after a pause into one transcript`() =
        runTest {
            // Given
            val viewModel =
                DictationViewModel(
                    FakeSpeechRecognitionRepository(
                        transcripts = listOf(final("il a souri")),
                        laterSessions = listOf(listOf(final("puis il a lu"))),
                    ),
                )

            // When
            viewModel.onStartClicked()

            // Then
            assertEquals("il a souri puis il a lu", viewModel.uiState.value.transcript)
        }
}
