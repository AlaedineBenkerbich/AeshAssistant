package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.ObservationNotes
import fr.alaedine.aesh.domain.repository.AiFeatureUnavailableException
import fr.alaedine.aesh.domain.repository.AiGenerationTimeoutException
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.presentation.report.FakeAiTextGenerationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class SortObservationNotesUseCaseTest {
    private val threeLineNotes =
        """
        Lina had trouble staying seated.
        The visual timer helped her.
        Great mood all morning.
        """.trimIndent()

    private fun useCaseAnswering(answer: String): SortObservationNotesUseCase =
        SortObservationNotesUseCase(FakeAiTextGenerationRepository(Result.success(answer)))

    @Test
    fun `should number every sentence of the notes in the prompt when sorting`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success("NOTES: 1, 2, 3"))
            val useCase = SortObservationNotesUseCase(generator)

            // When
            useCase("Il a eu du mal à lire. Le minuteur a aidé.\nBonne humeur")

            // Then
            val prompt = requireNotNull(generator.lastPrompt)
            assertContains(prompt, "[1] Il a eu du mal à lire.")
            assertContains(prompt, "[2] Le minuteur a aidé.")
            assertContains(prompt, "[3] Bonne humeur")
        }

    @Test
    fun `should keep abbreviations in one piece when splitting the notes into lines`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository(Result.success("NOTES: 1"))
            val useCase = SortObservationNotesUseCase(generator)

            // When
            useCase("M. Durand a aidé Alice à 10h30. Elle a souri.")

            // Then exactly two lines: the abbreviation's period and the time's period don't split anything else
            val notesToSort = requireNotNull(generator.lastPrompt).substringAfter("Notes to sort\n")
            assertEquals("[1] M. Durand a aidé Alice à 10h30.\n[2] Elle a souri.\nAnswer:", notesToSort)
        }

    @Test
    fun `should file each line under the field the model chose when the answer is well formed`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("OBSTACLES: 1\nHELPED: 2\nNOTES: 3")

            // When
            val result = useCase(threeLineNotes)

            // Then
            val sorted = assertIs<NotesSortingResult.Sorted>(result)
            assertEquals(
                ObservationNotes(
                    obstacles = "Lina had trouble staying seated.",
                    supportStrategies = "The visual timer helped her.",
                    freeNotes = "Great mood all morning.",
                ),
                sorted.notes,
            )
        }

    @Test
    fun `should keep the original order of the lines within a field when several lines share it`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("OBSTACLES: 3, 1\nHELPED: none\nNOTES: 2")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertEquals(
                "Lina had trouble staying seated.\nGreat mood all morning.",
                result.notes.obstacles,
            )
        }

    @Test
    fun `should file lines the model left out under notes when the answer skips them`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("OBSTACLES: 1\nHELPED: none\nNOTES: none")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertEquals("Lina had trouble staying seated.", result.notes.obstacles)
            assertEquals("", result.notes.supportStrategies)
            assertEquals("The visual timer helped her.\nGreat mood all morning.", result.notes.freeNotes)
        }

    @Test
    fun `should ignore line numbers that are not in the notes when the model answers with them`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("OBSTACLES: 0, 1, 7, 99999999999\nHELPED: 2\nNOTES: 3")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertEquals(
                ObservationNotes(
                    obstacles = "Lina had trouble staying seated.",
                    supportStrategies = "The visual timer helped her.",
                    freeNotes = "Great mood all morning.",
                ),
                result.notes,
            )
        }

    @Test
    fun `should file a line under the first field in priority order when the model lists it under several`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("NOTES: 1, 2\nHELPED: 1, 2, 3\nOBSTACLES: 1")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertEquals("Lina had trouble staying seated.", result.notes.obstacles)
            assertEquals("The visual timer helped her.\nGreat mood all morning.", result.notes.supportStrategies)
            assertEquals("", result.notes.freeNotes)
        }

    @Test
    fun `should understand a range of lines when the model answers with a dash`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("OBSTACLES: 1-2\nHELPED: none\nNOTES: 3")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertEquals("Lina had trouble staying seated.\nThe visual timer helped her.", result.notes.obstacles)
            assertEquals("Great mood all morning.", result.notes.freeNotes)
        }

    @Test
    fun `should not hang on an absurdly large range when the model answers with one`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("OBSTACLES: 1-2000000000\nHELPED: none\nNOTES: none")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertEquals(threeLineNotes, result.notes.obstacles)
        }

    @Test
    fun `should tolerate decorated labels when the model answers with markdown`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("Here you go:\n**Obstacles:** 1\n- helped = 2\n* NOTES : 3")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertIs<NotesSortingResult.Sorted>(result)
            assertEquals("Lina had trouble staying seated.", result.notes.obstacles)
            assertEquals("The visual timer helped her.", result.notes.supportStrategies)
            assertEquals("Great mood all morning.", result.notes.freeNotes)
        }

    @Test
    fun `should read every field when the model answers on a single line`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("OBSTACLES: 1 HELPED: 2 NOTES: 3")

            // When
            val result = useCase(threeLineNotes)

            // Then
            assertEquals("Lina had trouble staying seated.", result.notes.obstacles)
            assertEquals("The visual timer helped her.", result.notes.supportStrategies)
            assertEquals("Great mood all morning.", result.notes.freeNotes)
        }

    @Test
    fun `should keep the notes unsorted when the answer names no field`() =
        runTest {
            // Given
            val useCase = useCaseAnswering("I'm sorry, I can't help with that.")

            // When
            val result = useCase(threeLineNotes)

            // Then
            val unsorted = assertIs<NotesSortingResult.Unsorted>(result)
            assertIs<UnrecognizedSortingResponseException>(unsorted.cause)
            assertEquals(ObservationNotes(freeNotes = threeLineNotes), unsorted.notes)
        }

    @Test
    fun `should keep the notes unsorted with the failure as cause when generation fails`() =
        runTest {
            // Given
            val failure = AiFeatureUnavailableException()
            val useCase = SortObservationNotesUseCase(FakeAiTextGenerationRepository(Result.failure(failure)))

            // When
            val result = useCase(threeLineNotes)

            // Then
            val unsorted = assertIs<NotesSortingResult.Unsorted>(result)
            assertSame(failure, unsorted.cause)
            assertEquals(ObservationNotes(freeNotes = threeLineNotes), unsorted.notes)
        }

    @Test
    fun `should keep the notes unsorted with a timeout cause when the model takes too long`() =
        runTest {
            // Given
            val neverAnswering =
                object : AiTextGenerationRepository {
                    override suspend fun generate(prompt: String): Result<String> = awaitCancellation()
                }
            val useCase = SortObservationNotesUseCase(neverAnswering)

            // When
            val result = useCase(threeLineNotes)

            // Then
            val unsorted = assertIs<NotesSortingResult.Unsorted>(result)
            assertIs<AiGenerationTimeoutException>(unsorted.cause)
            assertEquals(ObservationNotes(freeNotes = threeLineNotes), unsorted.notes)
        }

    @Test
    fun `should propagate cancellation instead of returning unsorted notes when generation was cancelled`() =
        runTest {
            // Given
            val useCase = SortObservationNotesUseCase(FakeAiTextGenerationRepository(Result.failure(CancellationException("cancelled"))))

            // When / Then
            assertFailsWith<CancellationException> { useCase(threeLineNotes) }
        }

    @Test
    fun `should not call the model when the notes are blank`() =
        runTest {
            // Given
            val generator = FakeAiTextGenerationRepository()
            val useCase = SortObservationNotesUseCase(generator)

            // When
            val result = useCase("  \n  ")

            // Then
            assertIs<NotesSortingResult.Sorted>(result)
            assertEquals(ObservationNotes(), result.notes)
            assertNull(generator.lastPrompt)
        }

    @Test
    fun `should keep the raw notes untouched when they are returned unsorted`() =
        runTest {
            // Given
            val raw = "  alice s'est calmée  avec le casque\n\nbonne fin de journée  "
            val useCase = SortObservationNotesUseCase(FakeAiTextGenerationRepository(Result.failure(RuntimeException("boom"))))

            // When
            val result = useCase(raw)

            // Then
            assertEquals("alice s'est calmée  avec le casque\n\nbonne fin de journée", result.notes.freeNotes)
        }
}
