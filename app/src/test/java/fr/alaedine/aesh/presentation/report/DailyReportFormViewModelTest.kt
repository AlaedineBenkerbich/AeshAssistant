package fr.alaedine.aesh.presentation.report

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `should expose the repository students when the view model is initialized`() = runTest {
        // Given / When
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = FakeDailyReportRepository(),
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
        )

        // Then
        assertEquals(listOf(alice), viewModel.uiState.value.students)
        assertNull(viewModel.uiState.value.selectedStudent)
    }

    @Test
    fun `should not allow saving when no student is selected`() = runTest {
        // Given / When
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = FakeDailyReportRepository(),
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
        )

        // Then
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `should allow saving once a student is selected`() = runTest {
        // Given
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = FakeDailyReportRepository(),
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
        )

        // When
        viewModel.onStudentSelected(alice)

        // Then
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `should reset fields to neutral defaults when selecting a student without an existing report`() = runTest {
        // Given
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = FakeDailyReportRepository(),
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
        )

        // When
        viewModel.onStudentSelected(alice)

        // Then
        val state = viewModel.uiState.value
        assertEquals(NEUTRAL_LEVEL, state.moodLevel)
        assertEquals(NEUTRAL_LEVEL, state.focusLevel)
        assertEquals(NEUTRAL_LEVEL, state.socialInteractions)
        assertEquals("", state.freeNotes)
        assertFalse(state.isEditing)
    }

    @Test
    fun `should load todays existing report when selecting a student who already has one`() = runTest {
        // Given
        val existingReport = DailyReport(
            id = 5L,
            date = today,
            studentId = alice.id,
            moodLevel = 5,
            focusLevel = 1,
            socialInteractions = 4,
            freeNotes = "Rough morning",
        )
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(existingReport)),
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
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
        assertEquals(existingReport.freeNotes, state.freeNotes)
    }

    @Test
    fun `should add a new report when saving without an existing report for the selected student`() = runTest {
        // Given
        val dailyReportRepository = FakeDailyReportRepository()
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = dailyReportRepository,
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
        )
        viewModel.onStudentSelected(alice)
        viewModel.onMoodLevelChanged(5)
        viewModel.onFocusLevelChanged(1)
        viewModel.onSocialInteractionsChanged(4)
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
        assertEquals("Great day overall", saved.freeNotes)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `should update todays existing report instead of duplicating it when saving again`() = runTest {
        // Given
        val existingReport = DailyReport(
            id = 5L,
            date = today,
            studentId = alice.id,
            moodLevel = 3,
            focusLevel = 3,
            socialInteractions = 3,
        )
        val dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(existingReport))
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = dailyReportRepository,
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
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
    fun `should not persist anything when saving without a selected student`() = runTest {
        // Given
        val dailyReportRepository = FakeDailyReportRepository()
        val viewModel = DailyReportFormViewModel(
            dailyReportRepository = dailyReportRepository,
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
        )

        // When
        viewModel.onSaveClicked()

        // Then
        assertEquals(emptyList(), dailyReportRepository.observeReports().first())
        assertFalse(viewModel.uiState.value.isSaved)
    }
}
