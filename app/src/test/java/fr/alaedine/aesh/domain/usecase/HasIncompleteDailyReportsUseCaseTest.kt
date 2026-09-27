package fr.alaedine.aesh.domain.usecase

import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import fr.alaedine.aesh.presentation.schedule.FakeScheduleSlotRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HasIncompleteDailyReportsUseCaseTest {
    private val aliceId = 1L
    private val amirId = 2L
    private val today = LocalDate.now()

    /** A schedule slot today assigned to [studentIds], for exercising the schedule-based completeness rule. */
    private fun scheduledToday(vararg studentIds: Long) =
        ScheduleSlot(
            id = 1L,
            dayOfWeek = today.dayOfWeek,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
            subject = "Mathématiques",
            studentIds = studentIds.toList(),
        )

    @Test
    fun `should return false when no student has a class scheduled today`() =
        runTest {
            // Given / When
            val useCase = HasIncompleteDailyReportsUseCase(FakeDailyReportRepository(), FakeScheduleSlotRepository())

            // Then
            assertFalse(useCase(today))
        }

    @Test
    fun `should return true when a student scheduled today is missing todays report`() =
        runTest {
            // Given
            val useCase =
                HasIncompleteDailyReportsUseCase(
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(scheduledToday(aliceId, amirId))),
                )

            // When
            val result = useCase(today)

            // Then
            assertTrue(result)
        }

    @Test
    fun `should return false when every student scheduled today already has a report`() =
        runTest {
            // Given
            val todaysReports =
                listOf(
                    DailyReport(id = 1L, date = today, studentId = aliceId, moodLevel = 4, focusLevel = 4, socialInteractions = 4),
                    DailyReport(id = 2L, date = today, studentId = amirId, moodLevel = 3, focusLevel = 3, socialInteractions = 3),
                )
            val useCase =
                HasIncompleteDailyReportsUseCase(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = todaysReports),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(scheduledToday(aliceId, amirId))),
                )

            // When
            val result = useCase(today)

            // Then
            assertFalse(result)
        }

    @Test
    fun `should ignore reports from other days when checking completeness`() =
        runTest {
            // Given
            val yesterdaysReport =
                DailyReport(id = 1L, date = today.minusDays(1), studentId = aliceId, moodLevel = 4, focusLevel = 4, socialInteractions = 4)
            val useCase =
                HasIncompleteDailyReportsUseCase(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(yesterdaysReport)),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(scheduledToday(aliceId))),
                )

            // When
            val result = useCase(today)

            // Then
            assertTrue(result)
        }

    @Test
    fun `should not require an observation for a student who has no class scheduled today`() =
        runTest {
            // Given: Amir has no class scheduled today and no report — under
            // the previous "all students" rule this would trigger the
            // reminder; under the schedule-based rule it must not, since
            // observing him today isn't required. Alice is scheduled and
            // already has today's report, so the overall result should be
            // "complete".
            val aliceReportToday =
                DailyReport(id = 1L, date = today, studentId = aliceId, moodLevel = 4, focusLevel = 4, socialInteractions = 4)
            val useCase =
                HasIncompleteDailyReportsUseCase(
                    dailyReportRepository = FakeDailyReportRepository(initialReports = listOf(aliceReportToday)),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(scheduledToday(aliceId))),
                )

            // When
            val result = useCase(today)

            // Then
            assertFalse(result)
        }

    @Test
    fun `should ignore schedule slots for other days of the week`() =
        runTest {
            // Given
            val tomorrowsSlot = scheduledToday(aliceId).copy(dayOfWeek = today.dayOfWeek.plus(1))
            val useCase =
                HasIncompleteDailyReportsUseCase(
                    dailyReportRepository = FakeDailyReportRepository(),
                    scheduleSlotRepository = FakeScheduleSlotRepository(initialScheduleSlots = listOf(tomorrowsSlot)),
                )

            // When
            val result = useCase(today)

            // Then
            assertFalse(result)
        }
}
