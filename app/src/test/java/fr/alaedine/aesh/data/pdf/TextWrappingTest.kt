package fr.alaedine.aesh.data.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextWrappingTest {
    /** One unit of width per character keeps expectations readable. */
    private val measureWidth: (String) -> Float = { it.length.toFloat() }

    @Test
    fun `should wrap words greedily when a paragraph is wider than a line`() {
        // Given
        val paragraph = "Alice a bien progressé en lecture"

        // When
        val lines = wrapParagraph(paragraph, maxWidth = 12f, measureWidth = measureWidth)

        // Then
        assertEquals(listOf("Alice a bien", "progressé en", "lecture"), lines)
    }

    @Test
    fun `should break a word wider than a whole line instead of letting it overflow`() {
        // Given
        val paragraph = "Ressources : https://eduscol.education.gouv.fr/ecole-inclusive"

        // When
        val lines = wrapParagraph(paragraph, maxWidth = 20f, measureWidth = measureWidth)

        // Then
        assertTrue(lines.all { it.length <= 20 }, lines.toString())
        assertEquals(paragraph.replace(" ", ""), lines.joinToString("").replace(" ", ""))
    }

    @Test
    fun `should split on tabs and repeated spaces as well as single spaces`() {
        // Given
        val paragraph = "Lecture\tCalcul    Écriture"

        // When
        val lines = wrapParagraph(paragraph, maxWidth = 40f, measureWidth = measureWidth)

        // Then
        assertEquals(listOf("Lecture Calcul Écriture"), lines)
    }

    @Test
    fun `should keep an empty line for a blank paragraph`() {
        // When
        val lines = wrapParagraph("   ", maxWidth = 40f, measureWidth = measureWidth)

        // Then
        assertEquals(listOf(""), lines)
    }
}
