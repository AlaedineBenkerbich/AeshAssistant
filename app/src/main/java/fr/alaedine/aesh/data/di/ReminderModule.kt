package fr.alaedine.aesh.data.di

import androidx.work.WorkManager
import fr.alaedine.aesh.data.reminder.DailyReminderNotifier
import fr.alaedine.aesh.data.reminder.WorkManagerReminderScheduler
import fr.alaedine.aesh.domain.reminder.ReminderScheduler
import fr.alaedine.aesh.domain.usecase.HasIncompleteDailyReportsUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Provides the daily reminder notification's dependencies: the shared
 * "are reports missing?" business rule, the notification wrapper, and the
 * WorkManager-backed [ReminderScheduler].
 *
 * Registered in [fr.alaedine.aesh.di.appModules] so
 * [fr.alaedine.aesh.AeshApplication] can resolve [ReminderScheduler] to
 * (re)schedule the reminder on process start, and so
 * [fr.alaedine.aesh.data.reminder.DailyReportReminderWorker] can resolve
 * [HasIncompleteDailyReportsUseCase]/[DailyReminderNotifier] through Koin's
 * global context (see that class for why it needs that indirection).
 */
val reminderModule = module {
    single { WorkManager.getInstance(androidContext()) }
    single { HasIncompleteDailyReportsUseCase(get(), get()) }
    single { DailyReminderNotifier(androidContext()) }
    single<ReminderScheduler> { WorkManagerReminderScheduler(get()) }
}
