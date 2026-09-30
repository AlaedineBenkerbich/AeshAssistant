package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.ExtractedObservationNotes
import fr.alaedine.aesh.domain.repository.AiTextGenerationRepository
import fr.alaedine.aesh.domain.repository.ScheduleScannerRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Turns dictated speech and/or photos of handwritten notes into the daily
 * observation form's free-text fields. Photos go through on-device OCR
 * ([scannerRepository]); the combined text is then split across "obstacles",
 * "what helped" and "notes" by the on-device [aiTextGenerationRepository]
 * (Gemini Nano). Everything stays offline.
 *
 * The AI step is best-effort: if it is unavailable, fails, answers with
 * something unparseable, or the text is too long for the model, the raw
 * text is returned in [ExtractedObservationNotes.freeNotes] (with
 * [ExtractedObservationNotes.sortedByAi] `false`) instead of failing.
 */
class ExtractObservationNotesUseCase(
    private val scannerRepository: ScheduleScannerRepository,
    private val aiTextGenerationRepository: AiTextGenerationRepository,
) {
    /**
     * @param spokenText the transcript of a dictation, if any.
     * @param photos photos of handwritten notes, in reading order.
     * @return the extracted fields, or a failed [Result] with
     * [NoNotesTextFoundException] when neither source yielded any text.
     */
    suspend operator fun invoke(
        spokenText: String?,
        photos: List<File>,
    ): Result<ExtractedObservationNotes> {
        val recognizedTexts =
            photos.mapNotNull {
                scannerRepository
                    .recognizeText(it)
                    .getOrNull()
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            }
        val sourceText = (listOfNotNull(spokenText?.trim()?.takeIf(String::isNotEmpty)) + recognizedTexts).joinToString("\n\n")
        if (sourceText.isEmpty()) return Result.failure(NoNotesTextFoundException())

        val unsorted = ExtractedObservationNotes(freeNotes = sourceText, sortedByAi = false)
        if (sourceText.length > MAX_AI_INPUT_CHARS) return Result.success(unsorted)

        val sorted =
            aiTextGenerationRepository
                .generate(buildPrompt(sourceText))
                .getOrNull()
                ?.let(::parseResponse)
        return Result.success(sorted ?: unsorted)
    }

    private fun buildPrompt(sourceText: String): String =
        """
        You are helping a special needs teaching assistant (AESH) fill out a daily observation
        form about a student. Below is the raw text of their notes (dictated or OCR-scanned from
        handwriting, so it may contain recognition errors).

        Split it into three fields:
        - "obstacles": difficulties or obstacles the student encountered.
        - "supportStrategies": support or strategies that helped the student.
        - "freeNotes": everything else worth keeping.

        Rules: use only information present in the text, never invent anything, do not
        translate (keep the original language), lightly fix obvious recognition errors, and use
        an empty string for a field with nothing relevant. Answer with a single JSON object with
        exactly these three string keys and nothing else.

        Notes:
        $sourceText
        """.trimIndent()

    /** Returns `null` when [response] holds no JSON object with at least one non-blank field. */
    private fun parseResponse(response: String): ExtractedObservationNotes? {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val parsed =
            runCatching { JSON.decodeFromString<AiResponse>(response.substring(start, end + 1)) }.getOrNull() ?: return null
        val notes =
            ExtractedObservationNotes(
                obstacles = parsed.obstacles.trim(),
                supportStrategies = parsed.supportStrategies.trim(),
                freeNotes = parsed.freeNotes.trim(),
            )
        return notes.takeIf { it.obstacles.isNotEmpty() || it.supportStrategies.isNotEmpty() || it.freeNotes.isNotEmpty() }
    }

    @Serializable
    private data class AiResponse(
        val obstacles: String = "",
        val supportStrategies: String = "",
        val freeNotes: String = "",
    )

    private companion object {
        /** Keeps the prompt comfortably inside Gemini Nano's limited context window. */
        const val MAX_AI_INPUT_CHARS = 4000
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

/** Thrown by [ExtractObservationNotesUseCase] when neither the dictation nor any photo yielded text. */
class NoNotesTextFoundException : Exception("No text could be extracted from the dictation or photos.")
