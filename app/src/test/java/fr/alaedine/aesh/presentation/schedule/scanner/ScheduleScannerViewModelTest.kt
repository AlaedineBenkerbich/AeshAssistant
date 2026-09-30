package fr.alaedine.aesh.presentation.schedule.scanner

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
}
