package fr.alaedine.aesh.data.reminder

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import fr.alaedine.aesh.domain.reminder.ReminderScheduler
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * [ReminderScheduler] implementation backed by WorkManager: enqueues
 * [DailyReportReminderWorker] as a unique daily periodic request, timed so
 * its first run lands on the next occurrence of [REMINDER_TIME].
 *
 * Uses [ExistingPeriodicWorkPolicy.KEEP] so calling [scheduleDaily] again
 * (e.g. on every app start, see [fr.alaedine.aesh.AeshApplication]) never
 * resets an already-pending schedule — WorkManager persists it across app
 * restarts and device reboots on its own.
 */
class WorkManagerReminderScheduler(
    private val workManager: WorkManager,
) : ReminderScheduler {

    override fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<DailyReportReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(computeInitialDelayMillis(REMINDER_TIME), TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        /** Default reminder time; not yet user-configurable (see issue #9). */
        val REMINDER_TIME: LocalTime = LocalTime.of(17, 0)
        const val UNIQUE_WORK_NAME = "daily_report_reminder"

        /** How long to wait from [now] until the next occurrence of [targetTime], in milliseconds. */
        internal fun computeInitialDelayMillis(
            targetTime: LocalTime,
            now: LocalDateTime = LocalDateTime.now(),
        ): Long {
            var nextRun = now.toLocalDate().atTime(targetTime)
            if (!nextRun.isAfter(now)) {
                nextRun = nextRun.plusDays(1)
            }
            return Duration.between(now, nextRun).toMillis()
        }
    }
}
