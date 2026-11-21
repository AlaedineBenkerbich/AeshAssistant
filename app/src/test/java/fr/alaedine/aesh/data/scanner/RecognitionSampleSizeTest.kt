package fr.alaedine.aesh.data.scanner

import kotlin.test.Test
import kotlin.test.assertEquals

class RecognitionSampleSizeTest {
    @Test
    fun `should halve a 12 megapixel photo so it stays readable`() {
        // When
        val sampleSize = recognitionSampleSize(width = 4032, height = 3024)

        // Then 2016 x 1512
        assertEquals(2, sampleSize)
    }

    @Test
    fun `should scale a 50 megapixel photo down further`() {
        // When
        val sampleSize = recognitionSampleSize(width = 8160, height = 6120)

        // Then 2040 x 1530
        assertEquals(4, sampleSize)
    }

    @Test
    fun `should keep a small photo at full resolution`() {
        // When
        val sampleSize = recognitionSampleSize(width = 1920, height = 1080)

        // Then
        assertEquals(1, sampleSize)
    }
}
