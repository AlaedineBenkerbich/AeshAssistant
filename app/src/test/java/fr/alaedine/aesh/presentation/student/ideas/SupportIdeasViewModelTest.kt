package fr.alaedine.aesh.presentation.student.ideas

import androidx.lifecycle.SavedStateHandle
import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.model.SupportIdea
import fr.alaedine.aesh.domain.model.SupportIdeas
import fr.alaedine.aesh.domain.repository.AiBackgroundUseBlockedException
import fr.alaedine.aesh.domain.repository.AiBusyException
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiNotEnoughStorageException
import fr.alaedine.aesh.domain.usecase.SuggestSupportIdeasUseCase
import fr.alaedine.aesh.presentation.report.FakeAiTextGenerationRepository
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SupportIdeasViewModel] observes its repositories and runs the use case in
 * `viewModelScope`, which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual virtual-time
 * advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupportIdeasViewModelTest {
    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val today = LocalDate.of(2026, 11, 20)
    private val ideasAnswer = "- Keep the visual timer. Why: it helped her refocus."
    private val otherIdeasAnswer = "- Read the instructions aloud. Why: she asked for it."
    private val keepTheTimer = SupportIdea("Keep the visual timer.", "it helped her refocus.")
    private val readAloud = SupportIdea("Read the instructions aloud.", "she asked for it.")

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun report(date: LocalDate = today) =
        DailyReport(
            id = date.toEpochDay(),
            date = date,
            studentId = alice.id,
            moodLevel = 3,
            focusLevel = 3,
            socialInteractions = 3,
        )

    private fun viewModel(
        students: List<Student> = listOf(alice),
        dailyReportRepository: FakeDailyReportRepository = FakeDailyReportRepository(initialReports = listOf(report())),
        aiTextGenerationRepository: FakeAiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success(ideasAnswer)),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = SupportIdeasViewModel(
        studentId = alice.id,
        studentRepository = FakeStudentRepository(initialStudents = students),
        dailyReportRepository = dailyReportRepository,
        suggestSupportIdeasUseCase = SuggestSupportIdeasUseCase(dailyReportRepository, aiTextGenerationRepository),
        savedStateHandle = savedStateHandle,
        ideasLanguage = { ReportLanguage.English },
    )

    /** A generator whose answers the test releases one at a time, to observe what happens while it runs. */
    private fun gatedGenerator(vararg answers: CompletableDeferred<Result<String>>): FakeAiTextGenerationRepository {
        val pending = ArrayDeque(answers.toList())
        return FakeAiTextGenerationRepository(respond = { pending.removeFirst().await() })
    }

    @Test
    fun `should expose the student and how many observations they have once loaded`() =
        runTest {
            // Given
            val reports = FakeDailyReportRepository(initialReports = listOf(report(), report(today.minusDays(1))))

            // When
            val viewModel = viewModel(dailyReportRepository = reports)

            // Then
            val state = viewModel.uiState.value
            assertEquals(alice, state.student)
            assertEquals(2, state.observationCount)
            assertFalse(state.isLoading)
            assertTrue(state.canSuggest)
        }

    @Test
    fun `should flag the student as missing when they no longer exist`() =
        runTest {
            // Given / When
            val viewModel = viewModel(students = emptyList())

            // Then
            assertTrue(viewModel.uiState.value.isStudentMissing)
            assertFalse(viewModel.uiState.value.canSuggest)
        }

    @Test
    fun `should not offer ideas when the student has no observations`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val viewModel = viewModel(dailyReportRepository = FakeDailyReportRepository(), aiTextGenerationRepository = generator)

            // When
            viewModel.onSuggestClicked()

            // Then
            assertTrue(viewModel.uiState.value.hasNoObservations)
            assertFalse(viewModel.uiState.value.canSuggest)
            assertNull(generator.lastPrompt)
        }

    @Test
    fun `should offer ideas as soon as a first observation is filled in`() =
        runTest {
            // Given
            val reports = FakeDailyReportRepository()
            val viewModel = viewModel(dailyReportRepository = reports)

            // When
            reports.addReport(report())

            // Then
            assertFalse(viewModel.uiState.value.hasNoObservations)
            assertTrue(viewModel.uiState.value.canSuggest)
        }

    @Test
    fun `should show the ideas the model suggests`() =
        runTest {
            // Given
            val viewModel = viewModel()

            // When
            viewModel.onSuggestClicked()

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isGenerating)
            assertNull(state.failure)
            assertEquals(
                SupportIdeas(listOf(keepTheTimer), observationCount = 1, firstObservationDate = today, lastObservationDate = today),
                state.ideas,
            )
        }

    @Test
    fun `should keep the previous ideas on screen while new ones are being suggested`() =
        runTest {
            // Given
            val firstAnswer = CompletableDeferred<Result<String>>()
            val secondAnswer = CompletableDeferred<Result<String>>()
            val viewModel = viewModel(aiTextGenerationRepository = gatedGenerator(firstAnswer, secondAnswer))
            viewModel.onSuggestClicked()
            firstAnswer.complete(Result.success(ideasAnswer))

            // When
            viewModel.onSuggestClicked()

            // Then
            assertTrue(viewModel.uiState.value.isGenerating)
            assertFalse(viewModel.uiState.value.canSuggest)
            assertEquals(
                listOf(keepTheTimer),
                viewModel.uiState.value.ideas
                    ?.ideas,
            )

            // When
            secondAnswer.complete(Result.success(otherIdeasAnswer))

            // Then
            assertFalse(viewModel.uiState.value.isGenerating)
            assertEquals(
                listOf(readAloud),
                viewModel.uiState.value.ideas
                    ?.ideas,
            )
        }

    @Test
    fun `should explain why the ideas couldn't be suggested`() =
        runTest {
            // Given
            val expectedFailures =
                mapOf(
                    AiFeatureUnavailableException() to SupportIdeasFailure.AiFeatureUnavailable,
                    AiGenerationTimeoutException() to SupportIdeasFailure.GenerationTimeout,
                    AiBusyException() to SupportIdeasFailure.AiBusy,
                    AiBackgroundUseBlockedException() to SupportIdeasFailure.BackgroundUseBlocked,
                    AiNotEnoughStorageException() to SupportIdeasFailure.NotEnoughStorage,
                    IllegalStateException("unexpected") to SupportIdeasFailure.GenerationFailed,
                )

            expectedFailures.forEach { (error, expectedFailure) ->
                val viewModel = viewModel(aiTextGenerationRepository = FakeAiTextGenerationRepository(Result.failure(error)))

                // When
                viewModel.onSuggestClicked()

                // Then
                assertEquals(expectedFailure, viewModel.uiState.value.failure, "For ${error::class.simpleName}")
                assertFalse(viewModel.uiState.value.isGenerating)
            }
        }

    @Test
    fun `should keep the previous ideas when suggesting new ones fails`() =
        runTest {
            // Given
            val firstAnswer = CompletableDeferred<Result<String>>()
            val secondAnswer = CompletableDeferred<Result<String>>()
            val viewModel = viewModel(aiTextGenerationRepository = gatedGenerator(firstAnswer, secondAnswer))
            viewModel.onSuggestClicked()
            firstAnswer.complete(Result.success(ideasAnswer))

            // When
            viewModel.onSuggestClicked()
            secondAnswer.complete(Result.failure(AiBusyException()))

            // Then
            assertEquals(SupportIdeasFailure.AiBusy, viewModel.uiState.value.failure)
            assertEquals(
                listOf(keepTheTimer),
                viewModel.uiState.value.ideas
                    ?.ideas,
            )
        }

    @Test
    fun `should clear the previous failure when trying again`() =
        runTest {
            // Given
            val firstAnswer = CompletableDeferred<Result<String>>()
            val secondAnswer = CompletableDeferred<Result<String>>()
            val viewModel = viewModel(aiTextGenerationRepository = gatedGenerator(firstAnswer, secondAnswer))
            viewModel.onSuggestClicked()
            firstAnswer.complete(Result.failure(AiBusyException()))

            // When
            viewModel.onSuggestClicked()

            // Then
            assertNull(viewModel.uiState.value.failure)
            assertTrue(viewModel.uiState.value.isGenerating)
        }

    @Test
    fun `should stop and keep the previous ideas when the request is cancelled`() =
        runTest {
            // Given
            val firstAnswer = CompletableDeferred<Result<String>>()
            val secondAnswer = CompletableDeferred<Result<String>>()
            val viewModel = viewModel(aiTextGenerationRepository = gatedGenerator(firstAnswer, secondAnswer))
            viewModel.onSuggestClicked()
            firstAnswer.complete(Result.success(ideasAnswer))
            viewModel.onSuggestClicked()

            // When
            viewModel.onCancelClicked()
            secondAnswer.complete(Result.success(otherIdeasAnswer))

            // Then
            assertFalse(viewModel.uiState.value.isGenerating)
            assertNull(viewModel.uiState.value.failure)
            assertEquals(
                listOf(keepTheTimer),
                viewModel.uiState.value.ideas
                    ?.ideas,
            )
        }

    @Test
    fun `should restore the ideas after the process was recreated`() =
        runTest {
            // Given
            val savedStateHandle = SavedStateHandle()
            viewModel(savedStateHandle = savedStateHandle).onSuggestClicked()

            // When
            val restoredViewModel = viewModel(savedStateHandle = savedStateHandle)

            // Then
            assertEquals(
                SupportIdeas(listOf(keepTheTimer), observationCount = 1, firstObservationDate = today, lastObservationDate = today),
                restoredViewModel.uiState.value.ideas,
            )
        }
}
