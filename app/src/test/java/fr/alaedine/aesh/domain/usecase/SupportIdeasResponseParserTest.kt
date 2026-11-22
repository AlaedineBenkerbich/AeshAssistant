package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.SupportIdea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportIdeasResponseParserTest {
    @Test
    fun `should read each dashed line as an idea with its reason when the answer follows the requested format`() {
        // Given
        val response =
            """
            - Keep the visual timer for long exercises. Why: it helped her refocus after breaks.
            - Split instructions into short steps. Why: starting alone keeps coming up as a difficulty.
            """.trimIndent()

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(
            listOf(
                SupportIdea("Keep the visual timer for long exercises.", "it helped her refocus after breaks."),
                SupportIdea("Split instructions into short steps.", "starting alone keeps coming up as a difficulty."),
            ),
            ideas,
        )
    }

    @Test
    fun `should read French reasons when the answer is in French`() {
        // Given
        val response =
            """
            - Garder le minuteur visuel. Pourquoi : il l'aide à se recentrer.
            - Proposer une pause après la récréation. Pourquoi: elle s'agite à son retour.
            """.trimIndent()

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(
            listOf(
                SupportIdea("Garder le minuteur visuel.", "il l'aide à se recentrer."),
                SupportIdea("Proposer une pause après la récréation.", "elle s'agite à son retour."),
            ),
            ideas,
        )
    }

    @Test
    fun `should read numbered ideas when the model numbers them`() {
        // Given
        val response = "1. Keep the timer. Why: it helps.\n2) Read the instructions aloud. Why: she asks for it."

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(
            listOf(SupportIdea("Keep the timer.", "it helps."), SupportIdea("Read the instructions aloud.", "she asks for it.")),
            ideas,
        )
    }

    @Test
    fun `should leave out the sentences introducing and concluding the list`() {
        // Given
        val response =
            """
            Here are a few ideas:

            - Keep the timer. Why: it helps.
            - Read the instructions aloud. Why: she asks for it.

            Talk them over with the teacher before trying them.
            """.trimIndent()

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(listOf("Keep the timer.", "Read the instructions aloud."), ideas.map { it.text })
    }

    @Test
    fun `should remove the markdown and the labels the model adds around an idea`() {
        // Given
        val response =
            """
            **Ideas:**
            * **Idea 1:** Keep the *visual* timer. **Why:** it helps.
            """.trimIndent()

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(listOf(SupportIdea("Keep the visual timer.", "it helps.")), ideas)
    }

    @Test
    fun `should attach a reason written on a line of its own to the idea above it`() {
        // Given
        val response =
            """
            - Keep the timer.
              Why: it helps.
            - Read the instructions aloud.
              - Pourquoi : elle le demande.
            """.trimIndent()

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(
            listOf(SupportIdea("Keep the timer.", "it helps."), SupportIdea("Read the instructions aloud.", "elle le demande.")),
            ideas,
        )
    }

    @Test
    fun `should read a reason given in brackets without its closing bracket`() {
        // Given
        val response = "- Garder le minuteur (Pourquoi : il l'aide à se recentrer (souvent))."

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(listOf(SupportIdea("Garder le minuteur", "il l'aide à se recentrer (souvent).")), ideas)
    }

    @Test
    fun `should keep an idea without a reason when the model gives none`() {
        // Given
        val response = "- Keep the timer.\n- Read the instructions aloud."

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(listOf(SupportIdea("Keep the timer."), SupportIdea("Read the instructions aloud.")), ideas)
    }

    @Test
    fun `should not cut an idea at the word why when it isn't a reason label`() {
        // Given
        val response = "- Explain to her why the timer matters before each exercise."

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(listOf(SupportIdea("Explain to her why the timer matters before each exercise.")), ideas)
    }

    @Test
    fun `should read each line as an idea when the answer isn't a list`() {
        // Given
        val response =
            """
            Voici quelques pistes :
            Garder le minuteur visuel. Pourquoi : il l'aide.
            Lire les consignes à voix haute.
            """.trimIndent()

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(
            listOf(SupportIdea("Garder le minuteur visuel.", "il l'aide."), SupportIdea("Lire les consignes à voix haute.")),
            ideas,
        )
    }

    @Test
    fun `should not take a number starting a line for the marker of a list`() {
        // Given
        val response = "-10 minutes on the timer worked well.\n1.5 hours of writing in a row is too long."

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(
            listOf(SupportIdea("-10 minutes on the timer worked well."), SupportIdea("1.5 hours of writing in a row is too long.")),
            ideas,
        )
    }

    @Test
    fun `should drop repeated ideas and keep at most six of them`() {
        // Given
        val response = (listOf("- Idea one.", "- idea ONE.") + (2..9).map { "- Idea $it." }).joinToString("\n")

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertEquals(listOf("Idea one.", "Idea 2.", "Idea 3.", "Idea 4.", "Idea 5.", "Idea 6."), ideas.map { it.text })
    }

    @Test
    fun `should find no idea when the answer holds no text`() {
        // Given
        val response = "  \n \n"

        // When
        val ideas = SupportIdeasResponseParser.parse(response)

        // Then
        assertTrue(ideas.isEmpty())
    }
}
