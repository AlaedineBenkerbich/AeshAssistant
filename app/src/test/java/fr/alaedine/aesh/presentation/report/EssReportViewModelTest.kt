package fr.alaedine.aesh.presentation.report

import androidx.lifecycle.SavedStateHandle
import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiBackgroundUseBlockedException
import fr.alaedine.aesh.domain.repository.AiBusyException
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiNotEnoughStorageException
import fr.alaedine.aesh.domain.repository.AiPromptTooLongException
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.usecase.GenerateEssReportUseCase
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.io.ByteArrayOutputStream
import java.io.OutputStream
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
    private val bob = Student(id = 2L, firstName = "Bob", className = "CM1")
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
        studentRepository: FakeStudentRepository = FakeStudentRepository(initialStudents = listOf(alice, bob)),
        dailyReportRepository: DailyReportRepository = FakeDailyReportRepository(),
        aiTextGenerationRepository: FakeAiTextGenerationRepository = FakeAiTextGenerationRepository(),
        pdfExportRepository: FakePdfExportRepository = FakePdfExportRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        reportLanguage: ReportLanguage = ReportLanguage.English,
    ) = EssReportViewModel(
        studentRepository = studentRepository,
        generateEssReportUseCase = GenerateEssReportUseCase(dailyReportRepository, aiTextGenerationRepository),
        pdfExportRepository = pdfExportRepository,
        savedStateHandle = savedStateHandle,
        reportLanguage = { reportLanguage },
    )

    /** A generator whose answers the test releases one at a time, to observe what happens while it runs. */
    private fun gatedGenerator(vararg answers: CompletableDeferred<Result<String>>): FakeAiTextGenerationRepository {
        val pending = ArrayDeque(answers.toList())
        return FakeAiTextGenerationRepository(respond = { pending.removeFirst().await() })
    }

    /** Records whether it was closed, unlike [ByteArrayOutputStream] whose close() does nothing observable. */
    private class TrackingOutputStream : OutputStream() {
        var isClosed = false
            private set

        override fun write(b: Int) = Unit

        override fun close() {
            isClosed = true
        }
    }

    private fun report(
        notes: String = "",
        studentId: Long = alice.id,
    ) = DailyReport(
        id = studentId,
        date = today,
        studentId = studentId,
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
            assertEquals(listOf(alice, bob), viewModel.uiState.value.students)
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
                            Result.failure(RuntimeException("[ErrorCode 15]Couldn't generate a response.")),
                        ),
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onGenerateClicked()

            // Then: a localized message, never the library's English developer message.
            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertNull(state.generatedText)
            assertEquals(EssReportStatusMessage.GenerationFailed, state.statusMessage)
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
    fun `should show a timeout message when generation doesn't complete in time`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.failure(AiGenerationTimeoutException())),
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onGenerateClicked()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertNull(state.generatedText)
            assertEquals(EssReportStatusMessage.GenerationTimeout, state.statusMessage)
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
            viewModel.onStudentSelected(bob)

            // Then
            assertNull(viewModel.uiState.value.generatedText)
        }

    @Test
    fun `should keep the draft when the same student or dates are picked again`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Original text.")),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            viewModel.onReportTextChanged("Edited text.")
            val state = viewModel.uiState.value

            // When
            viewModel.onStudentSelected(alice)
            viewModel.onStartDateSelected(state.startDate)
            viewModel.onEndDateSelected(state.endDate)

            // Then
            assertEquals("Edited text.", viewModel.uiState.value.generatedText)
        }

    @Test
    fun `should not show a report generated for another student when the selection changed meanwhile`() =
        runTest {
            // Given
            val aliceAnswer = CompletableDeferred<Result<String>>()
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report(), report(studentId = bob.id))),
                    aiTextGenerationRepository = gatedGenerator(aliceAnswer),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            assertTrue(viewModel.uiState.value.isGenerating)

            // When
            viewModel.onStudentSelected(bob)
            aliceAnswer.complete(Result.success("Alice's report."))

            // Then
            val state = viewModel.uiState.value
            assertEquals(bob, state.selectedStudent)
            assertFalse(state.isGenerating)
            assertNull(state.draft)
            assertNull(state.statusMessage)
        }

    @Test
    fun `should not show a report generated for another period when the dates changed meanwhile`() =
        runTest {
            // Given
            val answer = CompletableDeferred<Result<String>>()
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = gatedGenerator(answer),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()

            // When
            viewModel.onStartDateSelected(today.minusDays(3))
            answer.complete(Result.success("Report about the previous period."))

            // Then
            assertFalse(viewModel.uiState.value.isGenerating)
            assertNull(viewModel.uiState.value.draft)
        }

    @Test
    fun `should keep the previous draft when the user cancels a regeneration`() =
        runTest {
            // Given
            val secondAnswer = CompletableDeferred<Result<String>>()
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = gatedGenerator(CompletableDeferred(Result.success("First draft.")), secondAnswer),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            viewModel.onGenerateClicked()
            assertTrue(viewModel.uiState.value.isGenerating)

            // When
            viewModel.onCancelGenerationClicked()
            secondAnswer.complete(Result.success("Second draft."))

            // Then
            assertFalse(viewModel.uiState.value.isGenerating)
            assertEquals("First draft.", viewModel.uiState.value.generatedText)
        }

    @Test
    fun `should keep the previous draft when a regeneration fails`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository =
                        gatedGenerator(
                            CompletableDeferred(Result.success("First draft.")),
                            CompletableDeferred(Result.failure(AiBusyException())),
                        ),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()

            // When
            viewModel.onGenerateClicked()

            // Then
            assertEquals("First draft.", viewModel.uiState.value.generatedText)
            assertEquals(EssReportStatusMessage.AiBusy, viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `should ask for confirmation before replacing a draft the user edited`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success("Generated draft."))
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = generator,
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            viewModel.onReportTextChanged("Carefully edited draft.")

            // When
            viewModel.onGenerateClicked()

            // Then
            assertTrue(viewModel.uiState.value.isRegenerateConfirmationVisible)
            assertEquals("Carefully edited draft.", viewModel.uiState.value.generatedText)
            assertEquals(1, generator.prompts.size)
        }

    @Test
    fun `should keep the edited draft when the user declines to regenerate`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Generated draft.")),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            viewModel.onReportTextChanged("Carefully edited draft.")
            viewModel.onGenerateClicked()

            // When
            viewModel.onRegenerateDismissed()

            // Then
            assertFalse(viewModel.uiState.value.isRegenerateConfirmationVisible)
            assertEquals("Carefully edited draft.", viewModel.uiState.value.generatedText)
        }

    @Test
    fun `should replace the edited draft once the user confirms regenerating it`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Generated draft.")),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onGenerateClicked()
            viewModel.onReportTextChanged("Carefully edited draft.")
            viewModel.onGenerateClicked()

            // When
            viewModel.onRegenerateConfirmed()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isRegenerateConfirmationVisible)
            assertEquals("Generated draft.", state.generatedText)
            assertEquals(false, state.draft?.isEdited)
        }

    @Test
    fun `should restore the selection and draft after the process was recreated`() =
        runTest {
            // Given
            val savedStateHandle = SavedStateHandle()
            val firstViewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Generated draft.")),
                    savedStateHandle = savedStateHandle,
                )
            firstViewModel.onStudentSelected(alice)
            firstViewModel.onStartDateSelected(today.minusDays(10))
            firstViewModel.onGenerateClicked()
            firstViewModel.onReportTextChanged("Edited draft.")

            // When
            val restoredViewModel =
                viewModel(savedStateHandle = SavedStateHandle(savedStateHandle.keys().associateWith { savedStateHandle.get<Any>(it) }))

            // Then
            val state = restoredViewModel.uiState.value
            assertEquals(alice, state.selectedStudent)
            assertEquals(today.minusDays(10), state.startDate)
            assertEquals(EssReportDraft("Edited draft.", "Alice", today.minusDays(10), today, isEdited = true), state.draft)
        }

    @Test
    fun `should generate from the student's latest details when they were edited after being selected`() =
        runTest {
            // Given
            val studentRepository = FakeStudentRepository(initialStudents = listOf(alice))
            val generator = FakeAiTextGenerationRepository()
            val viewModel =
                viewModel(
                    studentRepository = studentRepository,
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = generator,
                )
            viewModel.onStudentSelected(alice)
            studentRepository.updateStudent(alice.copy(className = "CM1", ppsGoals = "Read aloud with confidence"))

            // When
            viewModel.onGenerateClicked()

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertTrue(prompt.contains("class CM1"))
            assertTrue(prompt.contains("Read aloud with confidence"))
        }

    @Test
    fun `should write the report in the app's language`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = generator,
                    reportLanguage = ReportLanguage.French,
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onGenerateClicked()

            // Then
            assertTrue(requireNotNull(generator.lastPrompt).contains("rédige en français"))
        }

    @Test
    fun `should show a dedicated message for each on-device AI failure the user can act on`() =
        runTest {
            val expectedMessages =
                mapOf(
                    AiPromptTooLongException() to EssReportStatusMessage.PromptTooLong,
                    AiBusyException() to EssReportStatusMessage.AiBusy,
                    AiBackgroundUseBlockedException() to EssReportStatusMessage.BackgroundUseBlocked,
                    AiNotEnoughStorageException() to EssReportStatusMessage.NotEnoughStorage,
                )
            expectedMessages.forEach { (failure, expectedMessage) ->
                // Given
                val viewModel =
                    viewModel(
                        dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                        aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.failure(failure)),
                    )
                viewModel.onStudentSelected(alice)

                // When
                viewModel.onGenerateClicked()

                // Then
                assertEquals(expectedMessage, viewModel.uiState.value.statusMessage)
            }
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
    fun `should close the file and report a failure instead of exporting when there is no report`() =
        runTest {
            // Given
            val pdfExportRepository = FakePdfExportRepository()
            val viewModel = viewModel(pdfExportRepository = pdfExportRepository)
            viewModel.onStudentSelected(alice)
            val destination = TrackingOutputStream()

            // When
            viewModel.onExportRequested(destination, "ESS report — Alice")

            // Then
            assertNull(pdfExportRepository.exportedTo)
            assertTrue(destination.isClosed)
            assertEquals(EssReportStatusMessage.ExportFailed, viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `should name the pdf after the student and period the report was generated for`() =
        runTest {
            // Given
            val viewModel =
                viewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
                    aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success("Report body.")),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onStartDateSelected(today.minusDays(7))

            // When
            viewModel.onGenerateClicked()

            // Then
            assertEquals("ess-report-alice-${today.minusDays(7)}-to-$today.pdf", viewModel.uiState.value.suggestedPdfFileName)
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
            assertEquals(EssReportStatusMessage.ExportFailed, viewModel.uiState.value.statusMessage)
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
