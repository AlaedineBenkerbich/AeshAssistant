package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.presentation.schedule.scanner.FakeTextRecognitionRepository
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecognizeNotesFromPhotosUseCaseTest {
    private val firstPage = File("page-1.jpg")
    private val secondPage = File("page-2.jpg")
    private val thirdPage = File("page-3.jpg")

    @Test
    fun `should join the text of every photo in the order they were taken when there are several`() =
        runTest {
            // Given
            val recognizer =
                FakeTextRecognitionRepository(
                    resultsByFileName =
                        mapOf(
                            "page-1.jpg" to Result.success("Crise à la récré\nCasque: aide"),
                            "page-2.jpg" to Result.success("Bonne lecture"),
                        ),
                )
            val useCase = RecognizeNotesFromPhotosUseCase(recognizer)

            // When
            val result = useCase(listOf(firstPage, secondPage))

            // Then
            assertEquals("Crise à la récré\nCasque: aide\nBonne lecture", result.getOrNull())
            assertEquals(listOf(firstPage, secondPage), recognizer.recognizedFiles)
        }

    @Test
    fun `should skip photos without any text when others have some`() =
        runTest {
            // Given
            val recognizer =
                FakeTextRecognitionRepository(
                    resultsByFileName =
                        mapOf(
                            "page-1.jpg" to Result.success("  \n "),
                            "page-2.jpg" to Result.success("  Bonne lecture  "),
                            "page-3.jpg" to Result.success(""),
                        ),
                )
            val useCase = RecognizeNotesFromPhotosUseCase(recognizer)

            // When
            val result = useCase(listOf(firstPage, secondPage, thirdPage))

            // Then
            assertEquals("Bonne lecture", result.getOrNull())
        }

    @Test
    fun `should fail with no text recognized when none of the photos has any text`() =
        runTest {
            // Given
            val useCase = RecognizeNotesFromPhotosUseCase(FakeTextRecognitionRepository(Result.success("   ")))

            // When
            val result = useCase(listOf(firstPage, secondPage))

            // Then
            assertTrue(result.isFailure)
            assertIs<NoTextRecognizedException>(result.exceptionOrNull())
        }

    @Test
    fun `should fail with no text recognized when there are no photos`() =
        runTest {
            // Given
            val useCase = RecognizeNotesFromPhotosUseCase(FakeTextRecognitionRepository(Result.success("Some text")))

            // When
            val result = useCase(emptyList())

            // Then
            assertIs<NoTextRecognizedException>(result.exceptionOrNull())
        }

    @Test
    fun `should fail the whole batch when one photo can not be read`() =
        runTest {
            // Given
            val unreadable = RuntimeException("corrupt image")
            val recognizer =
                FakeTextRecognitionRepository(
                    resultsByFileName =
                        mapOf(
                            "page-1.jpg" to Result.success("Bonne lecture"),
                            "page-2.jpg" to Result.failure(unreadable),
                            "page-3.jpg" to Result.success("Fin de journée"),
                        ),
                )
            val useCase = RecognizeNotesFromPhotosUseCase(recognizer)

            // When
            val result = useCase(listOf(firstPage, secondPage, thirdPage))

            // Then
            assertSame(unreadable, result.exceptionOrNull())
            assertEquals(listOf(firstPage, secondPage), recognizer.recognizedFiles)
        }
}
