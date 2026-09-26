package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.report.FakeAiTextGenerationRepository
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenerateEssReportUseCaseTest {

    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2", ppsGoals = "Improve handwriting")
    private val today = LocalDate.of(2026, 9, 27)

    private fun report(date: LocalDate, notes: String) = DailyReport(
        id = date.toEpochDay(),
        date = date,
        studentId = alice.id,
        moodLevel = 4,
        focusLevel = 3,
        socialInteractions = 5,
        freeNotes = notes,
    )

    @Test
    fun `should fail without calling the generator when there are no reports in range`() = runTest {
        // Given
        val generator = FakeAiTextGenerationRepository()
        val useCase = GenerateEssReportUseCase(FakeDailyReportRepository(), generator)

        // When
        val result = useCase(alice, today.minusDays(7), today)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoReportsInRangeException)
        assertEquals(
            "No daily reports found for Alice in the selected period.",
            result.exceptionOrNull()?.message,
        )
        assertNull(generator.lastPrompt)
    }

    @Test
    fun `should only include reports within the requested date range in the prompt`() = runTest {
        // Given
        val insideRange = report(today.minusDays(2), "Great focus today")
        val outsideRange = report(today.minusDays(30), "Should not appear")
        val dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(insideRange, outsideRange))
        val generator = FakeAiTextGenerationRepository()
        val useCase = GenerateEssReportUseCase(dailyReportRepository, generator)

        // When
        useCase(alice, today.minusDays(7), today)

        // Then
        val prompt = requireNotNull(generator.lastPrompt)
        assertContains(prompt, "Great focus today")
        assertFalse(prompt.contains("Should not appear"))
    }

    @Test
    fun `should include the student context in the prompt`() = runTest {
        // Given
        val generator = FakeAiTextGenerationRepository()
        val useCase = GenerateEssReportUseCase(
            FakeDailyReportRepository(initialReports = listOf(report(today, "Some notes"))),
            generator,
        )

        // When
        useCase(alice, today, today)

        // Then
        val prompt = requireNotNull(generator.lastPrompt)
        assertContains(prompt, "Alice")
        assertContains(prompt, "CE2")
        assertContains(prompt, "Improve handwriting")
    }

    @Test
    fun `should return the generated text on success`() = runTest {
        // Given
        val generator = FakeAiTextGenerationRepository(Result.success("A cohesive summary."))
        val useCase = GenerateEssReportUseCase(
            FakeDailyReportRepository(initialReports = listOf(report(today, "notes"))),
            generator,
        )

        // When
        val result = useCase(alice, today, today)

        // Then
        assertEquals("A cohesive summary.", result.getOrNull())
    }

    @Test
    fun `should propagate a generation failure`() = runTest {
        // Given
        val generator = FakeAiTextGenerationRepository(Result.failure(RuntimeException("model unavailable")))
        val useCase = GenerateEssReportUseCase(
            FakeDailyReportRepository(initialReports = listOf(report(today, "notes"))),
            generator,
        )

        // When
        val result = useCase(alice, today, today)

        // Then
        assertTrue(result.isFailure)
        assertEquals("model unavailable", result.exceptionOrNull()?.message)
    }
}
