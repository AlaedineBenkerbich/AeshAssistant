package fr.alaedine.aesh.presentation.home

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import java.time.LocalDate
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
import kotlin.test.assertTrue

/**
 * [HomeViewModel] reads [fr.alaedine.aesh.domain.repository.StudentRepository]
 * and [fr.alaedine.aesh.domain.repository.DailyReportRepository] through
 * `viewModelScope`, which requires the `Main` dispatcher to be available;
 * [UnconfinedTestDispatcher] makes coroutines launched on it run eagerly so
 * state updates are visible immediately, without needing manual virtual-time
 * advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val amir = Student(id = 2L, firstName = "Amir", className = "CM2")
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
    fun `should expose todays date when the view model is initialized`() = runTest {
        // Given / When
        val viewModel = HomeViewModel(FakeStudentRepository(), FakeDailyReportRepository())

        // Then
        assertEquals(today, viewModel.uiState.value.date)
    }

    @Test
    fun `should mark every student as missing todays report when none exist`() = runTest {
        // Given / When
        val viewModel = HomeViewModel(
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
            dailyReportRepository = FakeDailyReportRepository(),
        )

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.studentStatuses.all { !it.hasReportToday })
        assertTrue(state.hasMissingReports)
        assertEquals(2, state.missingReportCount)
    }

    @Test
    fun `should mark a student as reported when they already have todays report`() = runTest {
        // Given
        val todaysReport = DailyReport(
            id = 1L,
            date = today,
            studentId = alice.id,
            moodLevel = 4,
            focusLevel = 4,
            socialInteractions = 4,
        )

        // When
        val viewModel = HomeViewModel(
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
            dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(todaysReport)),
        )

        // Then
        val state = viewModel.uiState.value
        assertTrue(state.studentStatuses.first { it.student.id == alice.id }.hasReportToday)
        assertFalse(state.studentStatuses.first { it.student.id == amir.id }.hasReportToday)
        assertEquals(1, state.missingReportCount)
    }

    @Test
    fun `should ignore reports from other days when computing todays status`() = runTest {
        // Given
        val yesterdaysReport = DailyReport(
            id = 1L,
            date = today.minusDays(1),
            studentId = alice.id,
            moodLevel = 4,
            focusLevel = 4,
            socialInteractions = 4,
        )

        // When
        val viewModel = HomeViewModel(
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
            dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(yesterdaysReport)),
        )

        // Then
        assertFalse(viewModel.uiState.value.studentStatuses.first().hasReportToday)
    }

    @Test
    fun `should not warn about missing reports when every student already has one today`() = runTest {
        // Given
        val todaysReport = DailyReport(
            id = 1L,
            date = today,
            studentId = alice.id,
            moodLevel = 4,
            focusLevel = 4,
            socialInteractions = 4,
        )

        // When
        val viewModel = HomeViewModel(
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
            dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(todaysReport)),
        )

        // Then
        assertFalse(viewModel.uiState.value.hasMissingReports)
    }

    @Test
    fun `should not warn about missing reports when there are no students`() = runTest {
        // Given / When
        val viewModel = HomeViewModel(FakeStudentRepository(), FakeDailyReportRepository())

        // Then
        assertFalse(viewModel.uiState.value.hasMissingReports)
    }
}
