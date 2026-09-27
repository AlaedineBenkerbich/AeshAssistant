package fr.alaedine.aesh.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.alaedine.aesh.domain.usecase.HasIncompleteDailyReportsUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate

/**
 * Background chore that fires once a day (scheduled by
 * [WorkManagerReminderScheduler]) and shows a reminder notification via
 * [DailyReminderNotifier] — but only when [hasIncompleteDailyReportsUseCase]
 * finds at least one student still missing today's report, so the user
 * isn't nagged once every report is already filled out.
 *
 * Depends on Koin's global instance ([KoinComponent]) rather than
 * constructor injection: WorkManager's default `WorkerFactory` instantiates
 * workers via reflection on the `(Context, WorkerParameters)` constructor,
 * so this avoids wiring a custom `WorkerFactory`/`Configuration.Provider`
 * for a single worker.
 */
class DailyReportReminderWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters),
    KoinComponent {
    private val hasIncompleteDailyReportsUseCase: HasIncompleteDailyReportsUseCase by inject()
    private val notifier: DailyReminderNotifier by inject()

    override suspend fun doWork(): Result {
        if (hasIncompleteDailyReportsUseCase(LocalDate.now())) {
            notifier.notifyMissingReports()
        }
        return Result.success()
    }
}
