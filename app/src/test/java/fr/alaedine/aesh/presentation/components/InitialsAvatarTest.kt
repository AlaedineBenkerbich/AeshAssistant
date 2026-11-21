package fr.alaedine.aesh.presentation.components

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class InitialsAvatarTest {
    @Test
    fun `should upper-case the first letter when the name starts with an accented lowercase letter`() {
        // Given
        val name = "  élodie"

        // When
        val initial = initialOf(name, Locale.FRENCH)

        // Then
        assertEquals("É", initial)
    }

    @Test
    fun `should keep the whole character when the name starts with an emoji`() {
        // Given
        val name = "😀 Sam"

        // When
        val initial = initialOf(name, Locale.FRENCH)

        // Then
        assertEquals("😀", initial)
    }

    @Test
    fun `should return no initial when the name is blank`() {
        // Given
        val name = "   "

        // When
        val initial = initialOf(name, Locale.FRENCH)

        // Then
        assertEquals("", initial)
    }

    @Test
    fun `should give consecutive keys consecutive colors when picking from the palette`() {
        // Given
        val keys = listOf(1L, 2L, 3L, 4L)

        // When
        val indexes = keys.map { avatarPaletteIndex(it, paletteSize = 6) }

        // Then
        assertEquals(listOf(1, 2, 3, 4), indexes)
    }

    @Test
    fun `should wrap around the palette when the key is larger than the palette`() {
        // Given
        val key = 13L

        // When
        val index = avatarPaletteIndex(key, paletteSize = 6)

        // Then
        assertEquals(1, index)
    }
}
