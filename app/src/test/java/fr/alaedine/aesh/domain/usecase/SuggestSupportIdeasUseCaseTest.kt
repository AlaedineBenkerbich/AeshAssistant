package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ReportLanguage
import fr.alaedine.aesh.domain.model.Student
import fr.alaedine.aesh.domain.model.SupportIdea
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiPromptTooLongException
import fr.alaedine.aesh.domain.repository.AiWordChoice
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SuggestSupportIdeasUseCaseTest {
    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2", ppsGoals = "Gain autonomy in written work")
    private val bob = Student(id = 2L, firstName = "Bob", className = "CM1")
    private val today = LocalDate.of(2026, 11, 20)
    private val ideasAnswer = "- Keep the visual timer. Why: it helped her refocus.\n- Read the instructions aloud. Why: she asked for it."

    private fun report(
        date: LocalDate,
        studentId: Long = alice.id,
        moodLevel: Int = 4,
        focusLevel: Int = 2,
        autonomyLevel: Int = 2,
        obstacles: String = "",
        supportStrategies: String = "",
        notes: String = "",
    ) = DailyReport(
        id = date.toEpochDay() * 10 + studentId,
        date = date,
        studentId = studentId,
        moodLevel = moodLevel,
        focusLevel = focusLevel,
        socialInteractions = 3,
        autonomyLevel = autonomyLevel,
        obstacles = obstacles,
        supportStrategies = supportStrategies,
        freeNotes = notes,
    )

    /** A school year of detailed observations: far more than the on-device model takes in one request. */
    private fun schoolYearOfReports(): List<DailyReport> =
        (0L until 140L).map { day ->
            report(
                date = LocalDate.of(2025, 9, 1).plusDays(day),
                obstacles = "Difficulté à démarrer seule l'exercice de mathématiques numéro $day, besoin d'une reformulation.",
                supportStrategies = "Le minuteur visuel et le plan de travail découpé en étapes l'ont aidée.",
                notes = "Alice a participé à l'atelier de lecture puis s'est agitée après la récréation.",
            )
        }

    private fun useCase(
        reports: List<DailyReport>,
        generator: FakeAiTextGenerationRepository = FakeAiTextGenerationRepository(Result.success(ideasAnswer)),
    ) = SuggestSupportIdeasUseCase(FakeDailyReportRepository(initialReports = reports), generator)

    @Test
    fun `should fail without calling the generator when the student has no observations`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(listOf(report(today, studentId = bob.id)), generator)

            // When
            val result = useCase(alice, ReportLanguage.English)

            // Then
            assertIs<NoObservationsException>(result.exceptionOrNull())
            assertEquals("No daily observations found for Alice yet.", result.exceptionOrNull()?.message)
            assertNull(generator.lastPrompt)
        }

    @Test
    fun `should return the ideas read from the model's answer`() =
        runTest {
            // Given
            val useCase = useCase(listOf(report(today)))

            // When
            val result = useCase(alice, ReportLanguage.English)

            // Then
            assertEquals(
                listOf(
                    SupportIdea("Keep the visual timer.", "it helped her refocus."),
                    SupportIdea("Read the instructions aloud.", "she asked for it."),
                ),
                result.getOrThrow().ideas,
            )
        }

    @Test
    fun `should only draw ideas from the student's own observations`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val reports = listOf(report(today, notes = "Alice read aloud"), report(today, studentId = bob.id, notes = "Bob's note"))
            val useCase = useCase(reports, generator)

            // When
            useCase(alice, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "Alice read aloud")
            assertFalse(prompt.contains("Bob's note"))
        }

    @Test
    fun `should give the model the student, their observations and their average levels`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val reports =
                listOf(
                    report(today.minusDays(1), moodLevel = 4, focusLevel = 2, autonomyLevel = 1),
                    report(
                        date = today,
                        moodLevel = 5,
                        focusLevel = 3,
                        autonomyLevel = 2,
                        obstacles = "Struggled to start the maths exercise alone",
                        supportStrategies = "The visual timer",
                    ),
                )
            val useCase = useCase(reports, generator)

            // When
            useCase(alice, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "plan how to support Alice (class CE2)")
            assertContains(prompt, "PPS goals: Gain autonomy in written work\n")
            assertContains(prompt, "mood 4.5/5, focus 2.5/5, social interactions 3.0/5, autonomy 1.5/5")
            assertContains(prompt, "November 20, 2026: mood 5/5, focus 3/5, social interactions 3/5, autonomy 2/5")
            assertContains(prompt, "Obstacles: Struggled to start the maths exercise alone")
            assertContains(prompt, "What helped: The visual timer")
        }

    @Test
    fun `should give the model the PPS goals as they were typed`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val student = alice.copy(ppsGoals = "  Lire seule une consigne courte.\nMieux vivre les transitions.  ")
            val useCase = useCase(listOf(report(today)), generator)

            // When
            useCase(student, ReportLanguage.French)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "Objectifs du PPS : Lire seule une consigne courte.\nMieux vivre les transitions.\nNiveaux moyens")
        }

    @Test
    fun `should tell the model when the PPS goals weren't filled in`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(listOf(report(today)), generator)

            // When
            useCase(alice.copy(ppsGoals = " "), ReportLanguage.English)

            // Then
            assertContains(requireNotNull(generator.lastPrompt), "PPS goals: not provided\n")
        }

    @Test
    fun `should give the model the observations in chronological order`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(listOf(report(today, notes = "Latest"), report(today.minusDays(7), notes = "Earliest")), generator)

            // When
            useCase(alice, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertTrue(prompt.indexOf("Earliest") < prompt.indexOf("Latest"))
        }

    @Test
    fun `should say which observations the ideas are drawn from`() =
        runTest {
            // Given
            val reports = listOf(report(today.minusDays(14)), report(today), report(today.minusDays(3)))
            val useCase = useCase(reports)

            // When
            val ideas = useCase(alice, ReportLanguage.English).getOrThrow()

            // Then
            assertEquals(3, ideas.observationCount)
            assertEquals(today.minusDays(14), ideas.firstObservationDate)
            assertEquals(today, ideas.lastObservationDate)
        }

    @Test
    fun `should only send the most recent observations when they don't all fit in one request`() =
        runTest {
            // Given
            val reports = schoolYearOfReports()
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(reports, generator)

            // When
            val ideas = useCase(alice, ReportLanguage.French).getOrThrow()

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertEquals(1, generator.prompts.size)
            assertTrue(prompt.length <= 6_000, "Prompt of ${prompt.length} characters")
            assertContains(prompt, "18 janvier 2026 : humeur")
            assertFalse(prompt.contains("1er septembre 2025"))
            assertTrue(ideas.observationCount in 2 until reports.size)
            assertEquals(reports.last().date, ideas.lastObservationDate)
            assertEquals(reports[reports.size - ideas.observationCount].date, ideas.firstObservationDate)
        }

    @Test
    fun `should cut an observation too long for a request on its own instead of failing`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(listOf(report(today, notes = "Elle a beaucoup progressé en lecture. ".repeat(300))), generator)

            // When
            val result = useCase(alice, ReportLanguage.French)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertTrue(prompt.length <= 6_000, "Prompt of ${prompt.length} characters")
            assertContains(prompt, "Réponds uniquement par la liste des pistes")
            assertEquals(1, result.getOrThrow().observationCount)
        }

    @Test
    fun `should try again with fewer observations when the request is too long for the model`() =
        runTest {
            // Given
            val reports = schoolYearOfReports()
            val generator =
                FakeAiTextGenerationRepository(
                    respond = { prompt ->
                        if (prompt.length > 3_000) Result.failure(AiPromptTooLongException()) else Result.success(ideasAnswer)
                    },
                )
            val useCase = useCase(reports, generator)

            // When
            val result = useCase(alice, ReportLanguage.French)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(2, generator.prompts.size)
            assertTrue(generator.prompts.last().length <= 3_000)
            assertEquals(reports.last().date, result.getOrThrow().lastObservationDate)
        }

    @Test
    fun `should write the prompt in French and ask for French ideas when the language is French`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(listOf(report(today, obstacles = "Fatigue en fin de journée")), generator)

            // When
            useCase(alice, ReportLanguage.French)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "Écris en français")
            assertContains(prompt, "Objectifs du PPS : Gain autonomy in written work\n")
            assertContains(prompt, "humeur 4.0/5, concentration 2.0/5, interactions sociales 3.0/5, autonomie 2.0/5")
            assertContains(prompt, "20 novembre 2026 : humeur 4/5")
            assertContains(prompt, "Obstacles : Fatigue en fin de journée")
            assertFalse(prompt.contains("Why:"))
        }

    @Test
    fun `should not indent the prompt`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(listOf(report(today, notes = "notes")), generator)

            // When
            useCase(alice, ReportLanguage.English)

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertTrue(prompt.lines().none { it.startsWith(" ") }, prompt)
        }

    @Test
    fun `should ask for varied wording so that asking again gets other ideas`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success(ideasAnswer))
            val useCase = useCase(listOf(report(today)), generator)

            // When
            useCase(alice, ReportLanguage.English)

            // Then
            assertEquals(listOf(AiWordChoice.Varied), generator.wordChoices)
        }

    @Test
    fun `should leave out the idea the model was writing when it was cut off`() =
        runTest {
            // Given
            val cutOffAnswer = "- Keep the visual timer. Why: it helped her refocus.\n- Read the instructions aloud. Why: she ask"
            val generator = FakeAiTextGenerationRepository(Result.success(cutOffAnswer), isCutOff = { true })
            val useCase = useCase(listOf(report(today)), generator)

            // When
            val ideas = useCase(alice, ReportLanguage.English).getOrThrow().ideas

            // Then
            assertEquals(listOf(SupportIdea("Keep the visual timer.", "it helped her refocus.")), ideas)
        }

    @Test
    fun `should keep the only idea of an answer that was cut off`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success("- Keep the visual timer for long"), isCutOff = { true })
            val useCase = useCase(listOf(report(today)), generator)

            // When
            val ideas = useCase(alice, ReportLanguage.English).getOrThrow().ideas

            // Then
            assertEquals(listOf(SupportIdea("Keep the visual timer for long")), ideas)
        }

    @Test
    fun `should fail when the model's answer holds no idea`() =
        runTest {
            // Given
            val useCase = useCase(listOf(report(today)), FakeAiTextGenerationRepository(Result.success("Here are some ideas:")))

            // When
            val result = useCase(alice, ReportLanguage.English)

            // Then
            assertIs<UnrecognizedSupportIdeasResponseException>(result.exceptionOrNull())
        }

    @Test
    fun `should propagate a generation failure`() =
        runTest {
            // Given
            val useCase = useCase(listOf(report(today)), FakeAiTextGenerationRepository(Result.failure(AiFeatureUnavailableException())))

            // When
            val result = useCase(alice, ReportLanguage.English)

            // Then
            assertIs<AiFeatureUnavailableException>(result.exceptionOrNull())
        }

    @Test
    fun `should rethrow the cancellation of the request instead of returning it as a failure`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.failure(CancellationException("left the screen")))
            val useCase = useCase(listOf(report(today)), generator)

            // When / Then
            assertFailsWith<CancellationException> { useCase(alice, ReportLanguage.English) }
        }
}
