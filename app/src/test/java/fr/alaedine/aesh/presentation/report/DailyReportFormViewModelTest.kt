package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.usecase.SortObservationNotesUseCase
import fr.alaedine.aesh.presentation.report.notes.DictationError
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DailyReportFormViewModel] reads/writes [fr.alaedine.aesh.domain.repository.DailyReportRepository]
 * and [fr.alaedine.aesh.domain.repository.StudentRepository] through
 * `viewModelScope`, which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual
 * virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyReportFormViewModelTest {
    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val today = LocalDate.now()
    private val bob = Student(id = 2L, firstName = "Bob", className = "CM2")
    private val defaultSortObservationNotes = SortObservationNotesUseCase(FakeAiTextGenerationRepository())
    private val threeLineNotes =
        """
        Lina had trouble staying seated.
        The visual timer helped her.
        Great mood all morning.
        """.trimIndent()
    private val threeLineAnswer = "OBSTACLES: 1\nHELPED: 2\nNOTES: 3"

    private fun viewModelSortingWith(
        aiTextGenerationRepository: AiTextGenerationRepository,
        students: List<Student> = listOf(alice),
    ) = DailyReportFormViewModel(
        dailyReportRepository = FakeDailyReportRepository(),
        studentRepository = FakeStudentRepository(initialStudents = students),
        sortObservationNotes = SortObservationNotesUseCase(aiTextGenerationRepository),
    )

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose the repository students when the view model is initialized`() =
        runTest {
            // Given / When
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // Then
            assertEquals(listOf(alice), viewModel.uiState.value.students)
            assertNull(viewModel.uiState.value.selectedStudent)
        }

    @Test
    fun `should not allow saving when no student is selected`() =
        runTest {
            // Given / When
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // Then
            assertFalse(viewModel.uiState.value.canSave)
        }

    @Test
    fun `should allow saving once a student is selected`() =
        runTest {
            // Given
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // When
            viewModel.onStudentSelected(alice)

            // Then
            assertTrue(viewModel.uiState.value.canSave)
        }

    @Test
    fun `should reset fields to neutral defaults when selecting a student without an existing report`() =
        runTest {
            // Given
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // When
            viewModel.onStudentSelected(alice)

            // Then
            val state = viewModel.uiState.value
            assertEquals(NEUTRAL_LEVEL, state.moodLevel)
            assertEquals(NEUTRAL_LEVEL, state.focusLevel)
            assertEquals(NEUTRAL_LEVEL, state.socialInteractions)
            assertEquals(NEUTRAL_LEVEL, state.autonomyLevel)
            assertEquals("", state.obstacles)
            assertEquals("", state.supportStrategies)
            assertEquals("", state.freeNotes)
            assertFalse(state.isEditing)
        }

    @Test
    fun `should update autonomy level, obstacles and support strategies when changed`() =
        runTest {
            // Given
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onAutonomyLevelChanged(5)
            viewModel.onObstaclesChanged("Got distracted during group work")
            viewModel.onSupportStrategiesChanged("Seating her near the front helped")

            // Then
            val state = viewModel.uiState.value
            assertEquals(5, state.autonomyLevel)
            assertEquals("Got distracted during group work", state.obstacles)
            assertEquals("Seating her near the front helped", state.supportStrategies)
        }

    @Test
    fun `should load todays existing report when selecting a student who already has one`() =
        runTest {
            // Given
            val existingReport =
                DailyReport(
                    id = 5L,
                    date = today,
                    studentId = alice.id,
                    moodLevel = 5,
                    focusLevel = 1,
                    socialInteractions = 4,
                    autonomyLevel = 2,
                    obstacles = "Needed help reading the instructions",
                    supportStrategies = "Reading the instructions aloud helped",
                    freeNotes = "Rough morning",
                )
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(existingReport)),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // When
            viewModel.onStudentSelected(alice)

            // Then
            val state = viewModel.uiState.value
            assertTrue(state.isEditing)
            assertEquals(existingReport.id, state.reportId)
            assertEquals(existingReport.moodLevel, state.moodLevel)
            assertEquals(existingReport.focusLevel, state.focusLevel)
            assertEquals(existingReport.socialInteractions, state.socialInteractions)
            assertEquals(existingReport.autonomyLevel, state.autonomyLevel)
            assertEquals(existingReport.obstacles, state.obstacles)
            assertEquals(existingReport.supportStrategies, state.supportStrategies)
            assertEquals(existingReport.freeNotes, state.freeNotes)
        }

    @Test
    fun `should add a new report when saving without an existing report for the selected student`() =
        runTest {
            // Given
            val dailyReportRepository = FakeDailyReportRepository()
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = dailyReportRepository,
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )
            viewModel.onStudentSelected(alice)
            viewModel.onMoodLevelChanged(5)
            viewModel.onFocusLevelChanged(1)
            viewModel.onSocialInteractionsChanged(4)
            viewModel.onAutonomyLevelChanged(2)
            viewModel.onObstaclesChanged("  Struggled with transitions  ")
            viewModel.onSupportStrategiesChanged("  A visual schedule helped  ")
            viewModel.onFreeNotesChanged("  Great day overall  ")

            // When
            viewModel.onSaveClicked()

            // Then
            val savedReports = dailyReportRepository.observeReports().first()
            assertEquals(1, savedReports.size)
            val saved = savedReports.first()
            assertEquals(alice.id, saved.studentId)
            assertEquals(today, saved.date)
            assertEquals(5, saved.moodLevel)
            assertEquals(1, saved.focusLevel)
            assertEquals(4, saved.socialInteractions)
            assertEquals(2, saved.autonomyLevel)
            assertEquals("Struggled with transitions", saved.obstacles)
            assertEquals("A visual schedule helped", saved.supportStrategies)
            assertEquals("Great day overall", saved.freeNotes)
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `should update todays existing report instead of duplicating it when saving again`() =
        runTest {
            // Given
            val existingReport =
                DailyReport(
                    id = 5L,
                    date = today,
                    studentId = alice.id,
                    moodLevel = 3,
                    focusLevel = 3,
                    socialInteractions = 3,
                )
            val dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(existingReport))
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = dailyReportRepository,
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onMoodLevelChanged(5)
            viewModel.onSaveClicked()

            // Then
            val savedReports = dailyReportRepository.observeReports().first()
            assertEquals(1, savedReports.size)
            assertEquals(existingReport.id, savedReports.first().id)
            assertEquals(5, savedReports.first().moodLevel)
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `should not persist anything when saving without a selected student`() =
        runTest {
            // Given
            val dailyReportRepository = FakeDailyReportRepository()
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = dailyReportRepository,
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // When
            viewModel.onSaveClicked()

            // Then
            assertEquals(emptyList(), dailyReportRepository.observeReports().first())
            assertFalse(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `should default the report date to today`() =
        runTest {
            // Given / When
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // Then
            assertEquals(today, viewModel.uiState.value.date)
        }

    @Test
    fun `should update the date when a previous day is selected`() =
        runTest {
            // Given
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )
            val previousDay = today.minusDays(1)

            // When
            viewModel.onDateSelected(previousDay)

            // Then
            assertEquals(previousDay, viewModel.uiState.value.date)
        }

    @Test
    fun `should ignore selecting a date after today`() =
        runTest {
            // Given
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )

            // When
            viewModel.onDateSelected(today.plusDays(1))

            // Then
            assertEquals(today, viewModel.uiState.value.date)
        }

    @Test
    fun `should load a previous days existing report when backfilling for the selected student`() =
        runTest {
            // Given
            val previousDay = today.minusDays(3)
            val existingReport =
                DailyReport(
                    id = 7L,
                    date = previousDay,
                    studentId = alice.id,
                    moodLevel = 2,
                    focusLevel = 4,
                    socialInteractions = 1,
                    autonomyLevel = 3,
                    obstacles = "Needed one-on-one support for the whole activity",
                    supportStrategies = "Breaking the task into smaller steps helped",
                    freeNotes = "Backfilled after forgetting to log it on the day",
                )
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(existingReport)),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onDateSelected(previousDay)

            // Then
            val state = viewModel.uiState.value
            assertTrue(state.isEditing)
            assertEquals(existingReport.id, state.reportId)
            assertEquals(existingReport.moodLevel, state.moodLevel)
            assertEquals(existingReport.focusLevel, state.focusLevel)
            assertEquals(existingReport.socialInteractions, state.socialInteractions)
            assertEquals(existingReport.autonomyLevel, state.autonomyLevel)
            assertEquals(existingReport.obstacles, state.obstacles)
            assertEquals(existingReport.supportStrategies, state.supportStrategies)
            assertEquals(existingReport.freeNotes, state.freeNotes)
        }

    @Test
    fun `should reset fields to neutral defaults when switching to a date without an existing report`() =
        runTest {
            // Given
            val existingReport =
                DailyReport(
                    id = 5L,
                    date = today,
                    studentId = alice.id,
                    moodLevel = 5,
                    focusLevel = 1,
                    socialInteractions = 4,
                    autonomyLevel = 2,
                    obstacles = "Needed help reading the instructions",
                    supportStrategies = "Reading the instructions aloud helped",
                    freeNotes = "Rough morning",
                )
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(existingReport)),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onDateSelected(today.minusDays(1))

            // Then
            val state = viewModel.uiState.value
            assertFalse(state.isEditing)
            assertNull(state.reportId)
            assertEquals(NEUTRAL_LEVEL, state.moodLevel)
            assertEquals(NEUTRAL_LEVEL, state.focusLevel)
            assertEquals(NEUTRAL_LEVEL, state.socialInteractions)
            assertEquals(NEUTRAL_LEVEL, state.autonomyLevel)
            assertEquals("", state.obstacles)
            assertEquals("", state.supportStrategies)
            assertEquals("", state.freeNotes)
        }

    @Test
    fun `should save a new report under the selected previous date when backfilling`() =
        runTest {
            // Given
            val dailyReportRepository = FakeDailyReportRepository()
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = dailyReportRepository,
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                )
            val previousDay = today.minusDays(2)
            viewModel.onStudentSelected(alice)
            viewModel.onDateSelected(previousDay)
            viewModel.onMoodLevelChanged(4)

            // When
            viewModel.onSaveClicked()

            // Then
            val savedReports = dailyReportRepository.observeReports().first()
            assertEquals(1, savedReports.size)
            assertEquals(previousDay, savedReports.first().date)
            assertEquals(alice.id, savedReports.first().studentId)
            assertEquals(4, savedReports.first().moodLevel)
            assertTrue(viewModel.uiState.value.isSaved)
        }

    @Test
    fun `should preselect the student matching the prefilled student id once students load`() =
        runTest {
            // Given / When: mirrors tapping a student row on the dashboard, which supplies both a student id and a date.
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                    preselectedStudentId = alice.id,
                    prefilledDate = today,
                )

            // Then
            assertEquals(alice, viewModel.uiState.value.selectedStudent)
        }

    @Test
    fun `should default the report date to the prefilled date when provided`() =
        runTest {
            // Given
            val previousDay = today.minusDays(1)

            // When
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                    prefilledDate = previousDay,
                )

            // Then
            assertEquals(previousDay, viewModel.uiState.value.date)
        }

    @Test
    fun `should clamp a future prefilled date to today`() =
        runTest {
            // Given: the dashboard allows browsing forward in time, unlike this form.
            val nextWeek = today.plusWeeks(1)

            // When
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                    prefilledDate = nextWeek,
                )

            // Then
            assertEquals(today, viewModel.uiState.value.date)
        }

    @Test
    fun `should load the prefilled dates existing report once the preselected student loads`() =
        runTest {
            // Given: reopening the form for a student/day already reported, e.g. re-tapping a completed observation on the dashboard.
            val previousDay = today.minusDays(3)
            val existingReport =
                DailyReport(
                    id = 9L,
                    date = previousDay,
                    studentId = alice.id,
                    moodLevel = 2,
                    focusLevel = 5,
                    socialInteractions = 3,
                    freeNotes = "Logged from the dashboard",
                )

            // When
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(existingReport)),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                    preselectedStudentId = alice.id,
                    prefilledDate = previousDay,
                )

            // Then
            val state = viewModel.uiState.value
            assertEquals(previousDay, state.date)
            assertEquals(alice, state.selectedStudent)
            assertTrue(state.isEditing)
            assertEquals(existingReport.id, state.reportId)
            assertEquals(existingReport.moodLevel, state.moodLevel)
            assertEquals(existingReport.freeNotes, state.freeNotes)
        }

    @Test
    fun `should default the report date to today when no date is prefilled`() =
        runTest {
            // Given / When: mirrors the dashboard's "new report" FAB, which doesn't supply a date.
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = FakeDailyReportRepository(),
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = defaultSortObservationNotes,
                    prefilledDate = null,
                )

            // Then
            assertEquals(today, viewModel.uiState.value.date)
        }

    @Test
    fun `should add the sorted notes to their fields when raw notes are received`() =
        runTest {
            // Given
            val viewModel = viewModelSortingWith(FakeAiTextGenerationRepository(Result.success(threeLineAnswer)))
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onRawNotesReceived(threeLineNotes)

            // Then
            val state = viewModel.uiState.value
            assertEquals("Lina had trouble staying seated.", state.obstacles)
            assertEquals("The visual timer helped her.", state.supportStrategies)
            assertEquals("Great mood all morning.", state.freeNotes)
            assertFalse(state.isSortingNotes)
            assertEquals(DailyReportStatusMessage.NotesFilledIn, state.statusMessage)
        }

    @Test
    fun `should add the sorted notes after the existing content when the fields are not empty`() =
        runTest {
            // Given
            val viewModel = viewModelSortingWith(FakeAiTextGenerationRepository(Result.success("OBSTACLES: 1\nHELPED: none\nNOTES: 2, 3")))
            viewModel.onStudentSelected(alice)
            viewModel.onObstaclesChanged("Typed earlier")
            viewModel.onSupportStrategiesChanged("Also typed earlier")
            viewModel.onFreeNotesChanged("Trailing newline kept out\n")

            // When
            viewModel.onRawNotesReceived(threeLineNotes)

            // Then
            val state = viewModel.uiState.value
            assertEquals("Typed earlier\nLina had trouble staying seated.", state.obstacles)
            assertEquals("Also typed earlier", state.supportStrategies)
            assertEquals("Trailing newline kept out\nThe visual timer helped her.\nGreat mood all morning.", state.freeNotes)
        }

    @Test
    fun `should add the raw notes to the free notes when the on-device AI is unavailable`() =
        runTest {
            // Given
            val viewModel = viewModelSortingWith(FakeAiTextGenerationRepository(Result.failure(AiFeatureUnavailableException())))
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onRawNotesReceived(threeLineNotes)

            // Then
            val state = viewModel.uiState.value
            assertEquals("", state.obstacles)
            assertEquals("", state.supportStrategies)
            assertEquals(threeLineNotes, state.freeNotes)
            assertEquals(DailyReportStatusMessage.NotesAddedUnsorted(UnsortedNotesReason.AiUnavailable), state.statusMessage)
        }

    @Test
    fun `should add the raw notes to the free notes and report a timeout when sorting takes too long`() =
        runTest {
            // Given
            val neverAnswering =
                object : AiTextGenerationRepository {
                    override suspend fun generate(prompt: String): Result<String> = awaitCancellation()
                }
            val viewModel = viewModelSortingWith(neverAnswering)
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onRawNotesReceived(threeLineNotes)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertEquals(threeLineNotes, state.freeNotes)
            assertFalse(state.isSortingNotes)
            assertEquals(DailyReportStatusMessage.NotesAddedUnsorted(UnsortedNotesReason.Timeout), state.statusMessage)
        }

    @Test
    fun `should add the raw notes to the free notes and report a failure when sorting fails unexpectedly`() =
        runTest {
            // Given
            val viewModel = viewModelSortingWith(FakeAiTextGenerationRepository(Result.failure(RuntimeException("boom"))))
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onRawNotesReceived(threeLineNotes)

            // Then
            val state = viewModel.uiState.value
            assertEquals(threeLineNotes, state.freeNotes)
            assertEquals(DailyReportStatusMessage.NotesAddedUnsorted(UnsortedNotesReason.Failed), state.statusMessage)
        }

    @Test
    fun `should hold the form back when notes are being sorted`() =
        runTest {
            // Given
            val suspendedAi = SuspendedAiTextGenerationRepository()
            val dailyReportRepository = FakeDailyReportRepository()
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = dailyReportRepository,
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = SortObservationNotesUseCase(suspendedAi),
                )
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onRawNotesReceived(threeLineNotes)
            viewModel.onSaveClicked()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state.isSortingNotes)
            assertFalse(state.canSave)
            assertFalse(state.isSaved)
            assertEquals(emptyList(), dailyReportRepository.observeReports().first())

            // When the model finally answers
            suspendedAi.answerWith(Result.success(threeLineAnswer))

            // Then
            assertFalse(viewModel.uiState.value.isSortingNotes)
            assertTrue(viewModel.uiState.value.canSave)
        }

    @Test
    fun `should add the notes being sorted to the free notes as they are when sorting is skipped`() =
        runTest {
            // Given
            val suspendedAi = SuspendedAiTextGenerationRepository()
            val viewModel = viewModelSortingWith(suspendedAi)
            viewModel.onStudentSelected(alice)
            viewModel.onRawNotesReceived(threeLineNotes)

            // When
            viewModel.onSkipSortingClicked()
            suspendedAi.answerWith(Result.success(threeLineAnswer))

            // Then the late answer doesn't sort the notes a second time
            val state = viewModel.uiState.value
            assertEquals("", state.obstacles)
            assertEquals("", state.supportStrategies)
            assertEquals(threeLineNotes, state.freeNotes)
            assertFalse(state.isSortingNotes)
            assertEquals(DailyReportStatusMessage.NotesAddedUnsorted(UnsortedNotesReason.Skipped), state.statusMessage)
        }

    @Test
    fun `should ignore skipping when no notes are being sorted`() =
        runTest {
            // Given
            val viewModel = viewModelSortingWith(FakeAiTextGenerationRepository())
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onSkipSortingClicked()

            // Then
            assertEquals("", viewModel.uiState.value.freeNotes)
            assertNull(viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `should ignore raw notes when no student is selected`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val viewModel = viewModelSortingWith(generator)

            // When
            viewModel.onRawNotesReceived(threeLineNotes)

            // Then
            assertFalse(viewModel.uiState.value.isSortingNotes)
            assertNull(viewModel.uiState.value.statusMessage)
            assertNull(generator.lastPrompt)
        }

    @Test
    fun `should ignore raw notes when they are blank`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val viewModel = viewModelSortingWith(generator)
            viewModel.onStudentSelected(alice)

            // When
            viewModel.onRawNotesReceived("  \n ")

            // Then
            assertNull(viewModel.uiState.value.statusMessage)
            assertNull(generator.lastPrompt)
        }

    @Test
    fun `should ignore a second batch of raw notes when one is being sorted`() =
        runTest {
            // Given
            val suspendedAi = SuspendedAiTextGenerationRepository()
            val viewModel = viewModelSortingWith(suspendedAi)
            viewModel.onStudentSelected(alice)
            viewModel.onRawNotesReceived("First batch.")

            // When
            viewModel.onRawNotesReceived("Second batch.")
            suspendedAi.answerWith(Result.success("OBSTACLES: none\nHELPED: none\nNOTES: 1"))

            // Then
            assertEquals("First batch.", viewModel.uiState.value.freeNotes)
        }

    @Test
    fun `should drop the notes being sorted when another student is selected meanwhile`() =
        runTest {
            // Given
            val suspendedAi = SuspendedAiTextGenerationRepository()
            val viewModel = viewModelSortingWith(suspendedAi, students = listOf(alice, bob))
            viewModel.onStudentSelected(alice)
            viewModel.onRawNotesReceived(threeLineNotes)

            // When
            viewModel.onStudentSelected(bob)
            suspendedAi.answerWith(Result.success(threeLineAnswer))

            // Then a child's notes never end up in another child's report
            val state = viewModel.uiState.value
            assertEquals(bob, state.selectedStudent)
            assertEquals("", state.obstacles)
            assertEquals("", state.supportStrategies)
            assertEquals("", state.freeNotes)
            assertFalse(state.isSortingNotes)
            assertNull(state.statusMessage)
        }

    @Test
    fun `should drop the notes being sorted when another date is selected meanwhile`() =
        runTest {
            // Given
            val suspendedAi = SuspendedAiTextGenerationRepository()
            val viewModel = viewModelSortingWith(suspendedAi)
            viewModel.onStudentSelected(alice)
            viewModel.onRawNotesReceived(threeLineNotes)

            // When
            viewModel.onDateSelected(today.minusDays(1))
            suspendedAi.answerWith(Result.success(threeLineAnswer))

            // Then
            val state = viewModel.uiState.value
            assertEquals("", state.freeNotes)
            assertFalse(state.isSortingNotes)
        }

    @Test
    fun `should save the sorted notes when saving after raw notes were received`() =
        runTest {
            // Given
            val dailyReportRepository = FakeDailyReportRepository()
            val viewModel =
                DailyReportFormViewModel(
                    dailyReportRepository = dailyReportRepository,
                    studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
                    sortObservationNotes = SortObservationNotesUseCase(FakeAiTextGenerationRepository(Result.success(threeLineAnswer))),
                )
            viewModel.onStudentSelected(alice)
            viewModel.onRawNotesReceived(threeLineNotes)

            // When
            viewModel.onSaveClicked()

            // Then
            val saved = dailyReportRepository.observeReports().first().single()
            assertEquals("Lina had trouble staying seated.", saved.obstacles)
            assertEquals("The visual timer helped her.", saved.supportStrategies)
            assertEquals("Great mood all morning.", saved.freeNotes)
        }

    @Test
    fun `should report why dictation failed when it does`() =
        runTest {
            // Given
            val viewModel = viewModelSortingWith(FakeAiTextGenerationRepository())

            // When
            viewModel.onDictationFailed(DictationError.NoSpeechDetected)

            // Then
            val message = assertIs<DailyReportStatusMessage.DictationFailed>(viewModel.uiState.value.statusMessage)
            assertEquals(DictationError.NoSpeechDetected, message.error)
        }

    @Test
    fun `should clear the status message when it has been shown`() =
        runTest {
            // Given
            val viewModel = viewModelSortingWith(FakeAiTextGenerationRepository())
            viewModel.onDictationFailed(DictationError.Failed)

            // When
            viewModel.onStatusMessageShown()

            // Then
            assertNull(viewModel.uiState.value.statusMessage)
        }
}

/**
 * An [AiTextGenerationRepository] that holds its answer back until the test
 * hands it over with [answerWith], to observe the form while notes are
 * still being sorted.
 */
private class SuspendedAiTextGenerationRepository : AiTextGenerationRepository {
    private val answer = CompletableDeferred<Result<String>>()

    override suspend fun generate(prompt: String): Result<String> = answer.await()

    fun answerWith(result: Result<String>) {
        answer.complete(result)
    }
}
