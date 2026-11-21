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

    @Test
    fun `should recognize times written with spaces around the h`() {
        // When
        val parsed = ScheduleTextParser.parse("Mardi : 8 h 30 - 9 h 25\nFrançais")

        // Then
        assertEquals(DayOfWeek.TUESDAY, parsed.dayOfWeek)
        assertEquals(LocalTime.of(8, 30), parsed.startTime)
        assertEquals(LocalTime.of(9, 25), parsed.endTime)
        assertEquals("Français", parsed.subject)
    }

    @Test
    fun `should not mistake a phone number, a duration or a dotted room number for a time`() {
        // When
        val parsed = ScheduleTextParser.parse("Collège Jean Moulin - Tél. 01.23.45.67.89\nEPS (2h)\nSalle 1.12")

        // Then
        assertNull(parsed.startTime)
        assertNull(parsed.endTime)
        assertEquals("1.12", parsed.room)
        assertEquals("EPS (2h)", parsed.subject)
    }

    @Test
    fun `should convert English 12-hour times`() {
        // When
        val parsed = ScheduleTextParser.parse("Friday 1:30 PM - 2:30 PM Art")

        // Then
        assertEquals(LocalTime.of(13, 30), parsed.startTime)
        assertEquals(LocalTime.of(14, 30), parsed.endTime)
        assertEquals("Art", parsed.subject)
    }

    @Test
    fun `should take the start and end from times stacked around the subject`() {
        // When
        val parsed = ScheduleTextParser.parse("13h30\nSciences\n14h30")

        // Then
        assertEquals(LocalTime.of(13, 30), parsed.startTime)
        assertEquals(LocalTime.of(14, 30), parsed.endTime)
    }

    @Test
    fun `should not combine a lone time with the range of another class`() {
        // When
        val parsed = ScheduleTextParser.parse("14h00 Histoire\n8h30 - 9h25 Mathématiques")

        // Then
        assertEquals(LocalTime.of(8, 30), parsed.startTime)
        assertEquals(LocalTime.of(9, 25), parsed.endTime)
    }

    @Test
    fun `should recognize multi-part and numbered room names, and rooms written on their own`() {
        // When / Then
        assertEquals("B 204", ScheduleTextParser.parse("Anglais salle B 204").room)
        assertEquals("12", ScheduleTextParser.parse("Musique Salle n°12").room)
        assertEquals("CDI", ScheduleTextParser.parse("Lecture\nCDI").room)
    }

    @Test
    fun `should not pick the teacher or the timetable title as the subject`() {
        // When
        val parsed =
            ScheduleTextParser.parse(
                "EMPLOI DU TEMPS CM1 - Semaine A\nLundi 8h30 - 9h25\nÉducation physique\nMme Bernard-Lefebvre\nGymnase",
            )

        // Then
        assertEquals("Éducation physique", parsed.subject)
        assertEquals("Gymnase", parsed.room)
    }

    @Test
    fun `should recognize abbreviated days only when they can only mean a day`() {
        // When / Then
        assertEquals(DayOfWeek.THURSDAY, ScheduleTextParser.parse("Jeu. 10h-11h Chant").dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, ScheduleTextParser.parse("Lun Mar Mer Jeu Ven\n9h-10h Calcul").dayOfWeek)
        assertNull(ScheduleTextParser.parse("Lecture de mon livre 9h-10h").dayOfWeek)
    }
}
