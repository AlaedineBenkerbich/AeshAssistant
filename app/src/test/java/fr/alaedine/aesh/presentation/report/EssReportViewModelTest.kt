package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [EssReportViewModel] reads/writes its repositories through
 * `viewModelScope`, which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual virtual-time
 * advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EssReportViewModelTest {
    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val today = LocalDate.now()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        studentRepository: FakeStudentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
        dailyReportRepository: DailyReportRepository = FakeDailyReportRepository(),
        aiTextGenerationRepository: FakeAiTextGenerationRepository = FakeAiTextGenerationRepository(),
        pdfExportRepository: FakePdfExportRepository = FakePdfExportRepository(),
    ) = EssReportViewModel(
        studentRepository = studentRepository,
        generateEssReportUseCase = GenerateEssReportUseCase(dailyReportRepository, aiTextGenerationRepository),
        pdfExportRepository = pdfExportRepository,
    )

    private fun report(notes: String = "") =
        DailyReport(
            id = 1L,
            date = today,
            studentId = alice.id,
            moodLevel = 3,
            focusLevel = 3,
            socialInteractions = 3,
            freeNotes = notes,
        )

    @Test
    fun `should expose the repository students when the view model is initialized`() =
        runTest {
            // Given / When
            val viewModel = viewModel()

            // Then
            assertEquals(listOf(alice), viewModel.uiState.value.students)
            assertFalse(viewModel.uiState.value.isLoadingStudents)
        }

    @Test
    fun `should not allow generating when no student is selected`() =
        runTest {
            // Given / When
            val viewModel = viewModel()

            // Then
            assertFalse(viewModel.uiState.value.canGenerate)
        }

    @Test
    fun `should allow generating once a student is selected with a valid range`() =
        runTest {
            // Given
            val viewModel = viewModel()

            // When
            viewModel.onStudentSelected(alice)

            // Then
            assertTrue(viewModel.uiState.value.canGenerate)
        }

    @Test
    fun `should flag an invalid range when the start date is after the end date`() =
        runTest {
            // Given
            val viewModel = viewModel()
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onStartDateSelected(today)
            viewModel.onEndDateSelected(today.minusDays(1))

            // Then
            assertTrue(viewModel.uiState.value.isDateRangeInvalid)
            assertFalse(viewModel.uiState.value.canGenerate)
        }

    @Test
    fun `should expose the generated report text on success`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report("Good day"))),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("A cohesive summary.")),
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onGenerateClicked()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertEquals("A cohesive summary.", state.generatedText)
            assertNull(state.statusMessage)
        }

    @Test
    fun `should show a failure message when generation fails`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository =
                        FakeAiTextGenerationRepository(
                            Result.failure(RuntimeException("On-device AI isn't available on this device.")),
                        ),
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onGenerateClicked()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertNull(state.generatedText)
            assertEquals(
                EssReportStatusMessage.GenerationFailed("On-device AI isn't available on this device."),
                state.statusMessage,
            )
        }

    @Test
    fun `should show a dedicated message when the on-device AI feature is unavailable`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.failure(AiFeatureUnavailableException())),
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onGenerateClicked()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertNull(state.generatedText)
            assertEquals(EssReportStatusMessage.AiFeatureUnavailable, state.statusMessage)
        }

    @Test
    fun `should show a message when there are no reports in the selected range`() =
        runTest {
            // Given
            val viewModel = viewModel(dailyReportRepository = FakeDailyReportRepository())
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onGenerateClicked()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertNull(state.generatedText)
            assertEquals(EssReportStatusMessage.NoReportsInRange("Alice"), state.statusMessage)
        }

    @Test
    fun `should apply manual edits to the generated report text`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Original text.")),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()

            // When
            viewModel.onReportTextChanged("Edited text.")

            // Then
            assertEquals("Edited text.", viewModel.uiState.value.generatedText)
        }

    @Test
    fun `should discard the previous draft when the student selection changes`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Original text.")),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            assertEquals("Original text.", viewModel.uiState.value.generatedText)

            // When
            viewModel.onStudentSelected(alice)

            // Then
            assertNull(viewModel.uiState.value.generatedText)
        }

    @Test
    fun `should export the current report text as a pdf`() =
        runTest {
            // Given
            val pdfExportRepository = FakePdfExportRepository()
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Report body.")),
                    pdfExportRepository = pdfExportRepository,
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            val destination = ByteArrayOutputStream()

            // When
            viewModel.onExportRequested(destination, "ESS report — Alice")

            // Then
            assertEquals("ESS report — Alice", pdfExportRepository.exportedTitle)
            assertEquals("Report body.", pdfExportRepository.exportedBody)
            assertEquals(destination, pdfExportRepository.exportedTo)
            assertEquals(EssReportStatusMessage.ExportSuccess, viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `should not export when there is no generated report yet`() =
        runTest {
            // Given
            val pdfExportRepository = FakePdfExportRepository()
            val viewModel = viewModel(pdfExportRepository = pdfExportRepository)
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onExportRequested(ByteArrayOutputStream(), "ESS report — Alice")

            // Then
            assertNull(pdfExportRepository.exportedTo)
        }

    @Test
    fun `should show a failure message when pdf export fails`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Report body.")),
                    pdfExportRepository = FakePdfExportRepository(exportError = RuntimeException("disk full")),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()

            // When
            viewModel.onExportRequested(ByteArrayOutputStream(), "ESS report — Alice")

            // Then
            assertEquals(EssReportStatusMessage.ExportFailed("disk full"), viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `should show a failure message when the export destination file could not be opened`() =
        runTest {
            // Given
            val viewModel = viewModel()

            // When
            viewModel.onExportFailedToOpenFile()

            // Then
            assertEquals(EssReportStatusMessage.ExportFileOpenFailed, viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `should clear the status message once it has been shown`() =
        runTest {
            // Given
            val viewModel = viewModel()
            viewModel.onExportFailedToOpenFile()

            // When
            viewModel.onStatusMessageShown()

            // Then
            assertNull(viewModel.uiState.value.statusMessage)
        }
}
