package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.repository.NoSpeechDetectedException
import fr.alaedine.aesh.domain.usecase.ExtractObservationNotesUseCase
import fr.alaedine.aesh.presentation.schedule.scanner.FakeScheduleScannerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotesImportViewModelTest {
    private val json = """{"obstacles":"Agitated","supportStrategies":"","freeNotes":"Good day"}"""

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        speech: FakeSpeechToTextRepository = FakeSpeechToTextRepository(),
        ai: Result<String> = Result.success(json),
        ocr: Result<String> = Result.success("scanned"),
    ) = NotesImportViewModel(
        speech,
        ExtractObservationNotesUseCase(FakeScheduleScannerRepository(ocr), FakeAiTextGenerationRepository(ai)),
    )

    @Test
    fun `should expose whether dictation is available`() {
        assertFalse(viewModel(speech = FakeSpeechToTextRepository(isAvailable = false)).uiState.value.isDictationAvailable)
        assertTrue(viewModel().uiState.value.isDictationAvailable)
    }

    @Test
    fun `should emit the extracted notes after a dictation`() =
        runTest {
            // Given
            val viewModel = viewModel()

            // When
            viewModel.onDictateClicked()

            // Then
            val notes = viewModel.extractedNotes.first()
            assertEquals("Agitated", notes.obstacles)
            assertEquals("Good day", notes.freeNotes)
            assertEquals(NotesImportMessage.Filled, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isBusy)
        }

    @Test
    fun `should report that nothing was heard when no speech is detected`() {
        // Given
        val viewModel = viewModel(speech = FakeSpeechToTextRepository(result = Result.failure(NoSpeechDetectedException())))

        // When
        viewModel.onDictateClicked()

        // Then
        assertEquals(NotesImportMessage.NoSpeechDetected, viewModel.uiState.value.message)
    }

    @Test
    fun `should tell the user the text was not sorted when the AI is unavailable`() {
        // Given
        val viewModel = viewModel(ai = Result.failure(IllegalStateException()))

        // When
        viewModel.onDictateClicked()

        // Then
        assertEquals(NotesImportMessage.FilledWithoutSorting, viewModel.uiState.value.message)
    }

    @Test
    fun `should import several photos and delete the temporary files`() =
        runTest {
            // Given
            val viewModel = viewModel()
            val photos = List(2) { File.createTempFile("note", ".jpg") }

            // When
            viewModel.onPhotosSelected(photos)

            // Then
            assertEquals("Good day", viewModel.extractedNotes.first().freeNotes)
            assertTrue(photos.none { it.exists() })
        }

    @Test
    fun `should report that no text was found when photos are unreadable`() {
        // Given
        val viewModel = viewModel(ocr = Result.success(""))

        // When
        viewModel.onPhotosSelected(listOf(File.createTempFile("note", ".jpg")))

        // Then
        assertEquals(NotesImportMessage.NoTextFound, viewModel.uiState.value.message)
    }

    @Test
    fun `should forward stop requests to the speech repository`() {
        // Given
        val speech = FakeSpeechToTextRepository()

        // When
        viewModel(speech = speech).onStopDictationClicked()

        // Then
        assertEquals(1, speech.stopCount)
    }
}
