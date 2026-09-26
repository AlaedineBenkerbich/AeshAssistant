package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import fr.alaedine.aesh.presentation.student.FakeStudentRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HasIncompleteDailyReportsUseCaseTest {

    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val amir = Student(id = 2L, firstName = "Amir", className = "CM2")
    private val today = LocalDate.now()

    @Test
    fun `should return false when there are no students`() = runTest {
        // Given
        val useCase = HasIncompleteDailyReportsUseCase(FakeStudentRepository(), FakeDailyReportRepository())

        // When
        val result = useCase(today)

        // Then
        assertFalse(result)
    }

    @Test
    fun `should return true when a student is missing todays report`() = runTest {
        // Given
        val useCase = HasIncompleteDailyReportsUseCase(
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
            dailyReportRepository = FakeDailyReportRepository(),
        )

        // When
        val result = useCase(today)

        // Then
        assertTrue(result)
    }

    @Test
    fun `should return false when every student already has todays report`() = runTest {
        // Given
        val todaysReports = listOf(
            DailyReport(id = 1L, date = today, studentId = alice.id, moodLevel = 4, focusLevel = 4, socialInteractions = 4),
            DailyReport(id = 2L, date = today, studentId = amir.id, moodLevel = 3, focusLevel = 3, socialInteractions = 3),
        )
        val useCase = HasIncompleteDailyReportsUseCase(
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice, amir)),
            dailyReportRepository = FakeDailyReportRepository(initialReports = todaysReports),
        )

        // When
        val result = useCase(today)

        // Then
        assertFalse(result)
    }

    @Test
    fun `should ignore reports from other days when checking completeness`() = runTest {
        // Given
        val yesterdaysReport = DailyReport(
            id = 1L,
            date = today.minusDays(1),
            studentId = alice.id,
            moodLevel = 4,
            focusLevel = 4,
            socialInteractions = 4,
        )
        val useCase = HasIncompleteDailyReportsUseCase(
            studentRepository = FakeStudentRepository(initialStudents = listOf(alice)),
            dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(yesterdaysReport)),
        )

        // When
        val result = useCase(today)

        // Then
        assertTrue(result)
    }
}
