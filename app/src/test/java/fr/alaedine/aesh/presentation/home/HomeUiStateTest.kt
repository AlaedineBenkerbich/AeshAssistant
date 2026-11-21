package fr.alaedine.aesh.presentation.home

import fr.alaedine.aesh.domain.model.Student
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeUiStateTest {
    private val wednesday = LocalDate.of(2026, 11, 18)
    private val friday = LocalDate.of(2026, 11, 20)
    private val saturday = LocalDate.of(2026, 11, 21)
    private val sunday = LocalDate.of(2026, 11, 22)
    private val mondayToFriday = (16..20).map { LocalDate.of(2026, 11, it) }

    private val alice = Student(id = 1L, firstName = "Alice", className = "CE2")
    private val amir = Student(id = 2L, firstName = "Amir", className = "CM2")

    private fun lessonBlock(
        id: Long,
        vararg statuses: StudentReportStatus,
    ) = LessonBlock(
        scheduleSlotId = id,
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(10, 0),
        subject = "Mathématiques",
        room = "",
        studentStatuses = statuses.toList(),
    )

    @Test
    fun `should show Monday to Friday when a weekday is selected and nothing happens on the weekend`() {
        // Given
        val uiState = HomeUiState(date = wednesday, today = wednesday)

        // When
        val weekDates = uiState.weekDates

        // Then
        assertEquals(mondayToFriday, weekDates)
    }

    @Test
    fun `should add Saturday when a class is scheduled on Saturdays`() {
        // Given
        val uiState =
            HomeUiState(
                date = wednesday,
                today = wednesday,
                daysWithScheduledClasses = setOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY),
            )

        // When
        val weekDates = uiState.weekDates

        // Then
        assertEquals(mondayToFriday + saturday, weekDates)
    }

    @Test
    fun `should add the selected day when it falls on the weekend`() {
        // Given
        val uiState = HomeUiState(date = sunday, today = wednesday)

        // When
        val weekDates = uiState.weekDates

        // Then
        assertEquals(mondayToFriday + sunday, weekDates)
    }

    @Test
    fun `should add today when it falls on the weekend of the displayed week`() {
        // Given
        val uiState = HomeUiState(date = friday, today = saturday)

        // When
        val weekDates = uiState.weekDates

        // Then
        assertEquals(mondayToFriday + saturday, weekDates)
    }

    @Test
    fun `should not add today when it falls on the weekend of another week`() {
        // Given
        val uiState = HomeUiState(date = friday.minusWeeks(1), today = saturday)

        // When
        val weekDates = uiState.weekDates

        // Then
        assertEquals(mondayToFriday.map { it.minusWeeks(1) }, weekDates)
    }

    @Test
    fun `should count each scheduled student once when they have several classes that day`() {
        // Given
        val uiState =
            HomeUiState(
                lessonBlocks =
                    listOf(
                        lessonBlock(1L, StudentReportStatus(alice, hasReport = true), StudentReportStatus(amir, hasReport = false)),
                        lessonBlock(2L, StudentReportStatus(amir, hasReport = false)),
                    ),
                isLoading = false,
            )

        // When
        val scheduledCount = uiState.scheduledStudentCount
        val completedCount = uiState.completedReportCount

        // Then
        assertEquals(2, scheduledCount)
        assertEquals(1, completedCount)
    }
}
