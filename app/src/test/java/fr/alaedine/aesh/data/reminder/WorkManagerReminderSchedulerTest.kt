package fr.alaedine.aesh.data.reminder

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkManagerReminderSchedulerTest {

    private val targetTime = LocalTime.of(17, 0)

    @Test
    fun `should compute delay until later today when the target time has not passed yet`() {
        // Given
        val now = LocalDateTime.of(2026, 9, 26, 10, 0)

        // When
        val delay = WorkManagerReminderScheduler.computeInitialDelayMillis(targetTime, now)

        // Then
        assertEquals(Duration.ofHours(7).toMillis(), delay)
    }

    @Test
    fun `should compute delay until tomorrow when the target time has already passed today`() {
        // Given
        val now = LocalDateTime.of(2026, 9, 26, 18, 30)

        // When
        val delay = WorkManagerReminderScheduler.computeInitialDelayMillis(targetTime, now)

        // Then
        assertEquals(Duration.ofHours(22).plusMinutes(30).toMillis(), delay)
    }

    @Test
    fun `should compute delay until tomorrow when now exactly matches the target time`() {
        // Given
        val now = LocalDateTime.of(2026, 9, 26, 17, 0)

        // When
        val delay = WorkManagerReminderScheduler.computeInitialDelayMillis(targetTime, now)

        // Then
        assertEquals(Duration.ofDays(1).toMillis(), delay)
    }
}
