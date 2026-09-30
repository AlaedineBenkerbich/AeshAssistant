package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.presentation.report.FakeAiTextGenerationRepository
import fr.alaedine.aesh.presentation.schedule.scanner.FakeScheduleScannerRepository
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExtractObservationNotesUseCaseTest {
    private val photo = File("photo.jpg")

    private fun useCase(
        ocr: Result<String> = Result.success("scanned text"),
        ai: Result<String>,
        aiRepository: FakeAiTextGenerationRepository = FakeAiTextGenerationRepository(ai),
    ) = ExtractObservationNotesUseCase(FakeScheduleScannerRepository(ocr), aiRepository)

    @Test
    fun `should split the text across the fields when the AI answers with JSON`() =
        runTest {
            // Given
            val json = """Sure! {"obstacles":"Agitated","supportStrategies":"Timer","freeNotes":"Good day"}"""

            // When
            val notes = useCase(ai = Result.success(json))("Spoken", listOf(photo)).getOrThrow()

            // Then
            assertEquals("Agitated", notes.obstacles)
            assertEquals("Timer", notes.supportStrategies)
            assertEquals("Good day", notes.freeNotes)
            assertTrue(notes.sortedByAi)
        }

    @Test
    fun `should send both the transcript and the recognized photo text to the AI`() =
        runTest {
            // Given
            val ai = FakeAiTextGenerationRepository(Result.success("""{"freeNotes":"x"}"""))

            // When
            useCase(ai = Result.success(""), aiRepository = ai)("Spoken", listOf(photo))

            // Then
            val prompt = ai.lastPrompt.orEmpty()
            assertTrue("Spoken" in prompt)
            assertTrue("scanned text" in prompt)
        }

    @Test
    fun `should fall back to free notes when the AI fails`() =
        runTest {
            // When
            val notes = useCase(ai = Result.failure(IllegalStateException()))("Spoken", emptyList()).getOrThrow()

            // Then
            assertEquals("Spoken", notes.freeNotes)
            assertEquals("", notes.obstacles)
            assertFalse(notes.sortedByAi)
        }

    @Test
    fun `should fall back to free notes when the AI answer is not valid JSON`() =
        runTest {
            // When
            val notes = useCase(ai = Result.success("I cannot do that"))("Spoken", emptyList()).getOrThrow()

            // Then
            assertEquals("Spoken", notes.freeNotes)
            assertFalse(notes.sortedByAi)
        }

    @Test
    fun `should skip the AI and keep the full text when it is too long`() =
        runTest {
            // Given
            val ai = FakeAiTextGenerationRepository(Result.success("""{"freeNotes":"x"}"""))
            val longText = "a".repeat(5000)

            // When
            val notes = useCase(ai = Result.success(""), aiRepository = ai)(longText, emptyList()).getOrThrow()

            // Then
            assertEquals(longText, notes.freeNotes)
            assertFalse(notes.sortedByAi)
            assertEquals(null, ai.lastPrompt)
        }

    @Test
    fun `should still succeed when one photo cannot be read but another source has text`() =
        runTest {
            // When
            val notes =
                useCase(
                    ocr = Result.failure(IllegalStateException()),
                    ai = Result.failure(IllegalStateException()),
                )("Spoken", listOf(photo)).getOrThrow()

            // Then
            assertEquals("Spoken", notes.freeNotes)
        }

    @Test
    fun `should fail when neither the dictation nor the photos contain text`() =
        runTest {
            // When
            val result = useCase(ocr = Result.success("  "), ai = Result.success(""))(null, listOf(photo))

            // Then
            assertIs<NoNotesTextFoundException>(result.exceptionOrNull())
        }
}
