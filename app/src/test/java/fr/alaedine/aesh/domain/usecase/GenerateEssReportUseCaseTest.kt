package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.presentation.report.FakeAiTextGenerationRepository
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GenerateEssReportUseCaseTest {
    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2", ppsGoals = "Improve handwriting")
    private val today = LocalDate.of(2026, 9, 27)

    private fun report(
        date: LocalDate,
        notes: String,
        autonomyLevel: Int = 3,
        obstacles: String = "",
        supportStrategies: String = "",
    ) = DailyReport(
        id = date.toEpochDay(),
        date = date,
        studentId = alice.id,
        moodLevel = 4,
        focusLevel = 3,
        socialInteractions = 5,
        autonomyLevel = autonomyLevel,
        obstacles = obstacles,
        supportStrategies = supportStrategies,
        freeNotes = notes,
    )

    /** A school year of detailed notes: far more than the on-device model takes in one request. */
    private fun schoolYearOfReports(): List<DailyReport> =
        (0L until 140L).map { day ->
            report(
                date = LocalDate.of(2025, 9, 1).plusDays(day),
                notes =
                    "Alice a participé à l'atelier de lecture et a demandé de l'aide pour la consigne numéro $day. " +
                        "Elle est restée concentrée une bonne partie de la matinée, puis s'est agitée après la récréation.",
                obstacles = "Difficulté à démarrer seule l'exercice de mathématiques, besoin d'une reformulation.",
                supportStrategies = "Le minuteur visuel et le plan de travail découpé en étapes l'ont aidée.",
            )
        }

    private fun useCase(
        reports: List<DailyReport>,
        generator: FakeAiTextGenerationRepository,
    ) = GenerateEssReportUseCase(FakeDailyReportRepository(initialReports = reports), generator)

    @Test
    fun `should fail without calling the generator when there are no reports in range`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val useCase = useCase(emptyList(), generator)

            // When
            val result = useCase(alice, today.minusDays(7), today, ReportLanguage.English)

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
    fun `should only include reports within the requested date range in the prompt`() =
        runTest {
            // Given
            val insideRange = report(today.minusDays(2), "Great focus today")
            val outsideRange = report(today.minusDays(30), "Should not appear")
            val generator = FakeAiTextGenerationRepository()
            val useCase = useCase(listOf(insideRange, outsideRange), generator)

            // When
            useCase(alice, today.minusDays(7), today, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "Great focus today")
            assertFalse(prompt.contains("Should not appear"))
        }

    @Test
    fun `should include autonomy level, obstacles and support strategies in the prompt`() =
        runTest {
            // Given
            val reportWithContext =
                report(
                    date = today,
                    notes = "Some notes",
                    autonomyLevel = 2,
                    obstacles = "Needed constant redirection during the math exercise",
                    supportStrategies = "A visual checklist helped her stay on task",
                )
            val generator = FakeAiTextGenerationRepository()
            val useCase = useCase(listOf(reportWithContext), generator)

            // When
            useCase(alice, today, today, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "autonomy 2/5")
            assertContains(prompt, "Obstacles: Needed constant redirection during the math exercise")
            assertContains(prompt, "What helped: A visual checklist helped her stay on task")
        }

    @Test
    fun `should leave empty fields out of the prompt when a report only has levels`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val useCase = useCase(listOf(report(today, notes = "")), generator)

            // When
            useCase(alice, today, today, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "2026-09-27: mood 4/5, focus 3/5, social interactions 5/5, autonomy 3/5")
            assertFalse(prompt.contains("(none)"))
            assertFalse(prompt.contains("Obstacles:"))
        }

    @Test
    fun `should include the student context in the prompt`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val useCase = useCase(listOf(report(today, "Some notes")), generator)

            // When
            useCase(alice, today, today, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "Alice")
            assertContains(prompt, "CE2")
            assertContains(prompt, "Improve handwriting")
        }

    @Test
    fun `should write the prompt in French and ask for a French report when the language is French`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val useCase = useCase(listOf(report(today, "Bonne matinée", obstacles = "Fatigue en fin de journée")), generator)

            // When
            useCase(alice, today, today, ReportLanguage.French)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "rédige en français")
            assertContains(prompt, "2026-09-27 : humeur 4/5, concentration 3/5, interactions sociales 5/5, autonomie 3/5")
            assertContains(prompt, "Obstacles : Fatigue en fin de journée")
            assertContains(prompt, "Notes : Bonne matinée")
            assertFalse(prompt.contains("Mood"))
        }

    @Test
    fun `should not indent the prompt`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val useCase = useCase(listOf(report(today, "notes")), generator)

            // When
            useCase(alice, today, today, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertTrue(prompt.lines().none { it.startsWith(" ") }, prompt)
        }

    @Test
    fun `should send a single request when the notes fit in one prompt`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success("A cohesive summary."))
            val progress = mutableListOf<EssReportProgress>()
            val useCase = useCase(listOf(report(today, "notes")), generator)

            // When
            val result = useCase(alice, today, today, ReportLanguage.English) { progress += it }

            // Then
            assertEquals("A cohesive summary.", result.getOrNull())
            assertEquals(1, generator.prompts.size)
            assertEquals(listOf(EssReportProgress(step = 1, stepCount = 1)), progress)
        }

    @Test
    fun `should summarize a long period piece by piece within the model's input limit`() =
        runTest {
            // Given
            val reports = schoolYearOfReports()
            val generator =
                FakeAiTextGenerationRepository(
                    respond = { prompt ->
                        if (prompt.startsWith("Tu aides un ou une AESH à préparer")) {
                            Result.success("Résumé de la période : progrès en lecture, difficultés en mathématiques.")
                        } else {
                            Result.success("Rapport ESS final.")
                        }
                    },
                )
            val progress = mutableListOf<EssReportProgress>()
            val useCase = useCase(reports, generator)

            // When
            val result = useCase(alice, reports.first().date, reports.last().date, ReportLanguage.French) { progress += it }

            // Then
            assertEquals("Rapport ESS final.", result.getOrNull())
            assertTrue(generator.prompts.size > 2, "expected several requests, got ${generator.prompts.size}")
            assertTrue(generator.prompts.all { it.length <= 6_000 }, "a prompt exceeds the input limit")
            val finalPrompt = generator.prompts.last()
            assertContains(finalPrompt, "Résumés par période")
            assertContains(finalPrompt, "Du 2025-09-01 au ")
            // Every day's notes reached the model, in one of the period summary requests.
            reports.forEach { report ->
                assertTrue(
                    generator.prompts.any {
                        it.contains(
                            "consigne numéro ${report.date.toEpochDay() - reports.first().date.toEpochDay()}.",
                        )
                    },
                )
            }
            assertEquals(generator.prompts.size, progress.size)
            assertEquals(progress.last().stepCount, progress.last().step)
        }

    @Test
    fun `should split a single day's notes that are longer than a whole prompt`() =
        runTest {
            // Given
            val hugeNote = "Observation détaillée de la séance de lecture. ".repeat(300)
            val generator =
                FakeAiTextGenerationRepository(
                    respond = { prompt ->
                        Result.success(if (prompt.startsWith("Tu aides un ou une AESH à préparer")) "Résumé." else "Rapport.")
                    },
                )
            val useCase = useCase(listOf(report(today, hugeNote)), generator)

            // When
            val result = useCase(alice, today, today, ReportLanguage.French)

            // Then
            assertEquals("Rapport.", result.getOrNull())
            assertTrue(generator.prompts.all { it.length <= 6_000 })
        }

    @Test
    fun `should return the report without markdown formatting`() =
        runTest {
            // Given
            val markdownReport = "## Synthèse\n**Points d'appui :** le minuteur visuel.\n* Lecture : progrès\n- Calcul : *à renforcer*"
            val generator = FakeAiTextGenerationRepository(Result.success(markdownReport))
            val useCase = useCase(listOf(report(today, "notes")), generator)

            // When
            val result = useCase(alice, today, today, ReportLanguage.French)

            // Then
            assertEquals("Synthèse\nPoints d'appui : le minuteur visuel.\n• Lecture : progrès\n• Calcul : à renforcer", result.getOrNull())
        }

    @Test
    fun `should propagate a generation failure`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.failure(RuntimeException("model unavailable")))
            val useCase = useCase(listOf(report(today, "notes")), generator)

            // When
            val result = useCase(alice, today, today, ReportLanguage.English)

            // Then
            assertTrue(result.isFailure)
            assertEquals("model unavailable", result.exceptionOrNull()?.message)
        }

    @Test
    fun `should stop at the first failing request of a long period`() =
        runTest {
            // Given
            val reports = schoolYearOfReports()
            val generator = FakeAiTextGenerationRepository(Result.failure(RuntimeException("busy")))
            val useCase = useCase(reports, generator)

            // When
            val result = useCase(alice, reports.first().date, reports.last().date, ReportLanguage.French)

            // Then
            assertEquals("busy", result.exceptionOrNull()?.message)
            assertEquals(1, generator.prompts.size)
        }

    @Test
    fun `should rethrow the cancellation of the generation instead of returning it as a failure`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.failure(CancellationException("left the screen")))
            val useCase = useCase(listOf(report(today, "notes")), generator)

            // When / Then
            assertFailsWith<CancellationException> { useCase(alice, today, today, ReportLanguage.English) }
        }
}
