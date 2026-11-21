package fr.alaedine.aesh.presentation.schedule.scanner

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fr.alaedine.aesh.domain.repository.TextRecognitionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ScheduleScannerViewModel] runs OCR + parsing through `viewModelScope`,
 * which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual
 * virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleScannerViewModelTest {
    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose the parsed fields when recognition finds usable text`() =
        runTest {
            // Given
            val repository = FakeTextRecognitionRepository(Result.success("Lundi 08:00 - 09:00 Mathématiques"))
            val viewModel = ScheduleScannerViewModel(repository)

            // When
            viewModel.onPhotoCaptured(File.createTempFile("schedule_scan_test", ".jpg"))

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isProcessing)
            assertTrue(state.isReviewing)
            assertEquals("Mathématiques", state.parsedScheduleSlot?.subject)
            assertNull(state.error)
        }

    @Test
    fun `should surface an error when no text is recognized`() =
        runTest {
            // Given
            val repository = FakeTextRecognitionRepository(Result.success("   "))
            val viewModel = ScheduleScannerViewModel(repository)

            // When
            viewModel.onPhotoCaptured(File.createTempFile("schedule_scan_test", ".jpg"))

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isProcessing)
            assertFalse(state.isReviewing)
            assertEquals(ScheduleScannerError.NoTextRecognized, state.error)
        }

    @Test
    fun `should surface an error when recognition fails`() =
        runTest {
            // Given
            val repository = FakeTextRecognitionRepository(Result.failure(RuntimeException("boom")))
            val viewModel = ScheduleScannerViewModel(repository)

            // When
            viewModel.onPhotoCaptured(File.createTempFile("schedule_scan_test", ".jpg"))

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isProcessing)
            assertFalse(state.isReviewing)
            assertEquals(ScheduleScannerError.RecognitionFailed, state.error)
        }

    @Test
    fun `should delete the temporary photo file once recognition completes`() =
        runTest {
            // Given
            val repository = FakeTextRecognitionRepository(Result.success("Mathématiques"))
            val viewModel = ScheduleScannerViewModel(repository)
            val photoFile = File.createTempFile("schedule_scan_test", ".jpg")
            assertTrue(photoFile.exists())

            // When
            viewModel.onPhotoCaptured(photoFile)

            // Then
            assertFalse(photoFile.exists())
        }

    @Test
    fun `should discard the result and clear errors when retaking`() =
        runTest {
            // Given
            val repository = FakeTextRecognitionRepository(Result.success("Mathématiques"))
            val viewModel = ScheduleScannerViewModel(repository)
            viewModel.onPhotoCaptured(File.createTempFile("schedule_scan_test", ".jpg"))
            assertTrue(viewModel.uiState.value.isReviewing)

            // When
            viewModel.onRetake()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isReviewing)
            assertNull(state.error)
        }

    @Test
    fun `should dismiss the error message`() =
        runTest {
            // Given
            val repository = FakeTextRecognitionRepository(Result.failure(RuntimeException("boom")))
            val viewModel = ScheduleScannerViewModel(repository)
            viewModel.onPhotoCaptured(File.createTempFile("schedule_scan_test", ".jpg"))
            assertNotNull(viewModel.uiState.value.error)

            // When
            viewModel.onErrorDismissed()

            // Then
            assertNull(viewModel.uiState.value.error)
        }

    /** Answers each photo only once the test completes its deferred answer, to act while recognition runs. */
    private class GatedTextRecognitionRepository(
        private val answers: Map<String, CompletableDeferred<Result<String>>>,
    ) : TextRecognitionRepository {
        override suspend fun recognizeText(imageFile: File): Result<String> = answers.getValue(imageFile.name).await()
    }

    @Test
    fun `should delete the photo when the scanner is left while it is being read`() =
        runTest {
            // Given
            val photo = File.createTempFile("schedule_scan", ".jpg")
            val store = ViewModelStore()
            val factory =
                viewModelFactory {
                    initializer { ScheduleScannerViewModel(GatedTextRecognitionRepository(mapOf(photo.name to CompletableDeferred()))) }
                }
            val viewModel = ViewModelProvider.create(store, factory)[ScheduleScannerViewModel::class]
            viewModel.onPhotoCaptured(photo)

            // When
            store.clear()

            // Then
            assertFalse(photo.exists())
        }

    @Test
    fun `should only show the result of the latest photo when the shutter was tapped twice`() =
        runTest {
            // Given
            val first = File.createTempFile("schedule_scan_first", ".jpg")
            val second = File.createTempFile("schedule_scan_second", ".jpg")
            val firstAnswer = CompletableDeferred<Result<String>>()
            val secondAnswer = CompletableDeferred<Result<String>>()
            val viewModel =
                ScheduleScannerViewModel(GatedTextRecognitionRepository(mapOf(first.name to firstAnswer, second.name to secondAnswer)))
            viewModel.onPhotoCaptured(first)
            viewModel.onPhotoCaptured(second)

            // When
            secondAnswer.complete(Result.success("Mardi 10h-11h Anglais"))
            firstAnswer.complete(Result.success("Lundi 8h-9h Français"))

            // Then
            assertEquals(
                "Anglais",
                viewModel.uiState.value.parsedScheduleSlot
                    ?.subject,
            )
            assertFalse(first.exists())
            assertFalse(second.exists())
        }

    @Test
    fun `should not bring a result back after the user chose to retake the photo`() =
        runTest {
            // Given
            val photo = File.createTempFile("schedule_scan", ".jpg")
            val answer = CompletableDeferred<Result<String>>()
            val viewModel = ScheduleScannerViewModel(GatedTextRecognitionRepository(mapOf(photo.name to answer)))
            viewModel.onPhotoCaptured(photo)

            // When
            viewModel.onRetake()
            answer.complete(Result.success("Lundi 8h-9h Français"))

            // Then
            assertNull(viewModel.uiState.value.parsedScheduleSlot)
            assertFalse(viewModel.uiState.value.isProcessing)
            assertFalse(photo.exists())
        }
}
