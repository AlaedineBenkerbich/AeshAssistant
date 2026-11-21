package fr.alaedine.aesh.presentation.report.notes

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.alaedine.aesh.domain.usecase.RecognizeNotesFromPhotosUseCase
import fr.alaedine.aesh.presentation.schedule.scanner.FakeTextRecognitionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [NotesScannerViewModel] reads photos through `viewModelScope`, which
 * requires the `Main` dispatcher to be available; [UnconfinedTestDispatcher]
 * makes coroutines launched on it run eagerly so state updates are visible
 * immediately, without needing manual virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesScannerViewModelTest {
    private val createdPhotos = mutableListOf<File>()
    private val photoDirectory: File = Files.createTempDirectory("notes-scans").toFile()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
        createdPhotos.forEach { it.delete() }
        photoDirectory.deleteRecursively()
    }

    /** A real (empty) file, since the view model is responsible for deleting the photos it is given. */
    private fun photo(): File = File.createTempFile("notes-scan", ".jpg").also { createdPhotos += it }

    private fun viewModelReading(result: Result<String>) =
        NotesScannerViewModel(RecognizeNotesFromPhotosUseCase(FakeTextRecognitionRepository(result)), photoDirectory)

    @Test
    fun `should keep the photos in the order they were captured when several are taken`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("Bonne lecture"))
            val first = photo()
            val second = photo()

            // When
            viewModel.onPhotoCaptured(first)
            viewModel.onPhotoCaptured(second)

            // Then
            assertEquals(listOf(first, second), viewModel.uiState.value.photos)
            assertTrue(viewModel.uiState.value.canFinish)
        }

    @Test
    fun `should not allow finishing when no photo was taken`() =
        runTest {
            // Given / When
            val viewModel = viewModelReading(Result.success("Bonne lecture"))

            // Then
            assertFalse(viewModel.uiState.value.canFinish)
        }

    @Test
    fun `should delete and forget the last photo when it is removed`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("Bonne lecture"))
            val first = photo()
            val second = photo()
            viewModel.onPhotoCaptured(first)
            viewModel.onPhotoCaptured(second)

            // When
            viewModel.onLastPhotoRemoved()

            // Then
            assertEquals(listOf(first), viewModel.uiState.value.photos)
            assertFalse(second.exists())
            assertTrue(first.exists())
        }

    @Test
    fun `should ignore removing a photo when none was taken`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("Bonne lecture"))

            // When
            viewModel.onLastPhotoRemoved()

            // Then
            assertEquals(emptyList(), viewModel.uiState.value.photos)
        }

    @Test
    fun `should read every photo and expose the text when done`() =
        runTest {
            // Given
            val recognizer = FakeTextRecognitionRepository(Result.success("Bonne lecture"))
            val viewModel = NotesScannerViewModel(RecognizeNotesFromPhotosUseCase(recognizer), photoDirectory)
            val first = photo()
            val second = photo()
            viewModel.onPhotoCaptured(first)
            viewModel.onPhotoCaptured(second)

            // When
            viewModel.onDoneClicked()

            // Then
            val state = viewModel.uiState.value
            assertEquals("Bonne lecture\nBonne lecture", state.recognizedNotes)
            assertEquals(listOf(first, second), recognizer.recognizedFiles)
            assertFalse(state.isProcessing)
            assertNull(state.error)
        }

    @Test
    fun `should delete every photo when they have been read`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("Bonne lecture"))
            val first = photo()
            val second = photo()
            viewModel.onPhotoCaptured(first)
            viewModel.onPhotoCaptured(second)

            // When
            viewModel.onDoneClicked()

            // Then photos of a child's notes don't outlive their use
            assertFalse(first.exists())
            assertFalse(second.exists())
            assertEquals(emptyList(), viewModel.uiState.value.photos)
        }

    @Test
    fun `should keep the photos and report no text recognized when none of them has any text`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("   "))
            val only = photo()
            viewModel.onPhotoCaptured(only)

            // When
            viewModel.onDoneClicked()

            // Then so the user can retake or add photos and try again
            val state = viewModel.uiState.value
            assertEquals(NotesScannerError.NoTextRecognized, state.error)
            assertNull(state.recognizedNotes)
            assertEquals(listOf(only), state.photos)
            assertTrue(only.exists())
            assertFalse(state.isProcessing)
        }

    @Test
    fun `should keep the photos and report a recognition failure when a photo can not be read`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.failure(RuntimeException("corrupt image")))
            val only = photo()
            viewModel.onPhotoCaptured(only)

            // When
            viewModel.onDoneClicked()

            // Then
            val state = viewModel.uiState.value
            assertEquals(NotesScannerError.RecognitionFailed, state.error)
            assertEquals(listOf(only), state.photos)
            assertFalse(state.isProcessing)
        }

    @Test
    fun `should do nothing when done is clicked without any photo`() =
        runTest {
            // Given
            val recognizer = FakeTextRecognitionRepository(Result.success("Bonne lecture"))
            val viewModel = NotesScannerViewModel(RecognizeNotesFromPhotosUseCase(recognizer), photoDirectory)

            // When
            viewModel.onDoneClicked()

            // Then
            assertEquals(emptyList(), recognizer.recognizedFiles)
            assertNull(viewModel.uiState.value.recognizedNotes)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `should clear a previous error when another photo is taken`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success(""))
            viewModel.onPhotoCaptured(photo())
            viewModel.onDoneClicked()

            // When
            viewModel.onPhotoCaptured(photo())

            // Then
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `should clear the error when it is dismissed`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success(""))
            viewModel.onPhotoCaptured(photo())
            viewModel.onDoneClicked()

            // When
            viewModel.onErrorDismissed()

            // Then
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `should clear the recognized notes when they have been consumed`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("Bonne lecture"))
            viewModel.onPhotoCaptured(photo())
            viewModel.onDoneClicked()

            // When
            viewModel.onRecognizedNotesConsumed()

            // Then
            assertNull(viewModel.uiState.value.recognizedNotes)
        }

    @Test
    fun `should delete the remaining photos when the scanner is left`() =
        runTest {
            // Given
            val store = ViewModelStore()
            val factory =
                viewModelFactory {
                    initializer { NotesScannerViewModel(RecognizeNotesFromPhotosUseCase(FakeTextRecognitionRepository()), photoDirectory) }
                }
            val viewModel = ViewModelProvider.create(store, factory)[NotesScannerViewModel::class]
            val first = photo()
            val second = photo()
            viewModel.onPhotoCaptured(first)
            viewModel.onPhotoCaptured(second)

            // When the user backs out of the scanner, which clears its view model
            store.clear()

            // Then
            assertFalse(first.exists())
            assertFalse(second.exists())
        }

    @Test
    fun `should not let the scan be finished while a photo is still being saved`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("Page 1"))
            viewModel.onPhotoCaptured(photo())

            // When
            viewModel.onCaptureStarted()

            // Then
            assertFalse(viewModel.uiState.value.canFinish)
        }

    @Test
    fun `should read the late photo too once it is saved`() =
        runTest {
            // Given
            val recognizer = FakeTextRecognitionRepository(Result.success("Page"))
            val viewModel = NotesScannerViewModel(RecognizeNotesFromPhotosUseCase(recognizer), photoDirectory)
            viewModel.onPhotoCaptured(photo())
            viewModel.onCaptureStarted()

            // When
            viewModel.onPhotoCaptured(photo())
            viewModel.onDoneClicked()

            // Then
            assertEquals("Page\nPage", viewModel.uiState.value.recognizedNotes)
        }

    @Test
    fun `should report a photo that could not be taken`() =
        runTest {
            // Given
            val viewModel = viewModelReading(Result.success("Page 1"))
            viewModel.onPhotoCaptured(photo())
            viewModel.onCaptureStarted()

            // When
            viewModel.onCaptureFailed()

            // Then
            assertEquals(NotesScannerError.CaptureFailed, viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.canFinish)
        }

    @Test
    fun `should delete the photos left behind by an unfinished scan when the scanner opens`() =
        runTest {
            // Given a photo of a scan the app was killed in the middle of
            val leftover = File(photoDirectory, "observation_notes_1.jpg").apply { writeText("jpeg") }

            // When
            viewModelReading(Result.success("Page 1"))

            // Then
            assertFalse(leftover.exists())
        }
}
