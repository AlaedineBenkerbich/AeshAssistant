package fr.alaedine.aesh.domain.scanner

import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTextParserTest {

    @Test
    fun `should recognize every field from a multi-line French schedule`() {
        // Given
        val recognizedText = "Lundi\n08:00 - 09:00\nMathématiques\nSalle B12"

        // When
        val parsed = ScheduleTextParser.parse(recognizedText)

        // Then
        assertEquals(DayOfWeek.MONDAY, parsed.dayOfWeek)
        assertEquals(LocalTime.of(8, 0), parsed.startTime)
        assertEquals(LocalTime.of(9, 0), parsed.endTime)
        assertEquals("Mathématiques", parsed.subject)
        assertEquals("B12", parsed.room)
    }

    @Test
    fun `should recognize every field from a single-line English schedule`() {
        // Given
        val recognizedText = "Monday 8:00-9:00 Physics Room 203"

        // When
        val parsed = ScheduleTextParser.parse(recognizedText)

        // Then
        assertEquals(DayOfWeek.MONDAY, parsed.dayOfWeek)
        assertEquals(LocalTime.of(8, 0), parsed.startTime)
        assertEquals(LocalTime.of(9, 0), parsed.endTime)
        assertEquals("Physics", parsed.subject)
        assertEquals("203", parsed.room)
    }

    @Test
    fun `should recognize an hour-only French time range separated by 'à' with no room`() {
        // Given
        val recognizedText = "Mardi 14h à 16h Français"

        // When
        val parsed = ScheduleTextParser.parse(recognizedText)

        // Then
        assertEquals(DayOfWeek.TUESDAY, parsed.dayOfWeek)
        assertEquals(LocalTime.of(14, 0), parsed.startTime)
        assertEquals(LocalTime.of(16, 0), parsed.endTime)
        assertEquals("Français", parsed.subject)
        assertNull(parsed.room)
    }

    @Test
    fun `should recognize only the subject when nothing else is present`() {
        // Given / When
        val parsed = ScheduleTextParser.parse("Récréation")

        // Then
        assertEquals("Récréation", parsed.subject)
        assertNull(parsed.dayOfWeek)
        assertNull(parsed.startTime)
        assertNull(parsed.endTime)
        assertNull(parsed.room)
    }

    @Test
    fun `should return an empty result for blank input`() {
        // Given / When
        val parsed = ScheduleTextParser.parse("   \n   \n")

        // Then
        assertTrue(parsed.isEmpty)
    }

    @Test
    fun `should recognize a single time as the start time only`() {
        // Given / When
        val parsed = ScheduleTextParser.parse("9h Mathématiques")

        // Then
        assertEquals(LocalTime.of(9, 0), parsed.startTime)
        assertNull(parsed.endTime)
        assertEquals("Mathématiques", parsed.subject)
    }

    @Test
    fun `should swap a reversed time range so start is always before end`() {
        // Given / When
        val parsed = ScheduleTextParser.parse("Lundi 17:00-16:00 Histoire")

        // Then
        assertEquals(LocalTime.of(16, 0), parsed.startTime)
        assertEquals(LocalTime.of(17, 0), parsed.endTime)
    }

    @Test
    fun `should recognize a room-only line without contaminating other fields`() {
        // Given / When
        val parsed = ScheduleTextParser.parse("Salle B12")

        // Then
        assertEquals("B12", parsed.room)
        assertNull(parsed.dayOfWeek)
        assertNull(parsed.startTime)
        assertNull(parsed.subject)
    }

    @Test
    fun `should not let a room label on one line swallow the subject on the next line`() {
        // Given / When
        val parsed = ScheduleTextParser.parse("Salle B12\nMathématiques")

        // Then
        assertEquals("B12", parsed.room)
        assertEquals("Mathématiques", parsed.subject)
    }

    @Test
    fun `should recognize the English day name 'Tuesday' rather than a shorter accidental match`() {
        // Given / When
        val parsed = ScheduleTextParser.parse("Tuesday 10:00-11:00 Chemistry")

        // Then
        assertEquals(DayOfWeek.TUESDAY, parsed.dayOfWeek)
    }
}
