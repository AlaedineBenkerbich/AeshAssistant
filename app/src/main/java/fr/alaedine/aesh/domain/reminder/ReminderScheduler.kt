package fr.alaedine.aesh.domain.reminder

/**
 * Framework-agnostic contract for (re)scheduling the recurring daily
 * reminder notification that prompts the user to fill out today's reports.
 *
 * Implemented by
 * [fr.alaedine.aesh.data.reminder.WorkManagerReminderScheduler] on top of
 * WorkManager. [fr.alaedine.aesh.AeshApplication] calls [scheduleDaily] once
 * on process start so the reminder keeps firing across app restarts, device
 * reboots, and process death, without presentation-layer code needing to
 * know WorkManager is the underlying mechanism.
 */
interface ReminderScheduler {

    /**
     * Enqueues the daily reminder if it isn't already scheduled. Safe to
     * call every app start: it never resets an already-pending schedule.
     */
    fun scheduleDaily()
}
