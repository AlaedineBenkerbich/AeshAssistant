package fr.alaedine.aesh.data.reminder

import android.Manifest
import android.app.NotificationManager
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import fr.alaedine.aesh.domain.model.DailyReport
import fr.alaedine.aesh.domain.model.ScheduleSlot
import fr.alaedine.aesh.domain.repository.DailyReportRepository
import fr.alaedine.aesh.domain.repository.ScheduleSlotRepository
import fr.alaedine.aesh.domain.usecase.HasIncompleteDailyReportsUseCase
import fr.alaedine.aesh.presentation.report.FakeDailyReportRepository
import fr.alaedine.aesh.presentation.schedule.FakeScheduleSlotRepository
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [DailyReportReminderWorker] under Robolectric with a Koin
 * instance stocked with in-memory fakes — this worker resolves its
 * dependencies from Koin's *global* context (see its class doc), so tests
 * start/stop that global context directly rather than injecting via the
 * constructor. See [fr.alaedine.aesh.data.local.dao.ScheduleSlotDaoTest] for
 * why [Config.application] swaps out [fr.alaedine.aesh.AeshApplication]
 * (which would otherwise start a second, real Koin instance).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DailyReportReminderWorkerTest {
    private val aliceId = 1L
    private val today: LocalDate = LocalDate.now()
    private val aliceScheduledToday =
        ScheduleSlot(
            id = 1L,
            dayOfWeek = today.dayOfWeek,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(10, 0),
            subject = "Mathématiques",
            studentIds = listOf(aliceId),
        )

    @BeforeTest
    fun grantNotificationPermission() {
        // The worker itself only decides *whether* to notify; permission
        // handling is exercised separately by DailyReminderNotifierTest, so
        // grant it here to keep these tests focused on that decision.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @AfterTest
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun `should post a notification when a student scheduled today is missing todays report`() =
        runTest {
            // Given
            startTestKoin(scheduleSlots = listOf(aliceScheduledToday), reports = emptyList())
            val worker = TestListenableWorkerBuilder<DailyReportReminderWorker>(RuntimeEnvironment.getApplication()).build()

            // When
            val result = worker.doWork()

            // Then
            assertEquals(Result.success(), result)
            val notificationManager = shadowOf(RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java))
            assertEquals(1, notificationManager.allNotifications.size)
        }

    @Test
    fun `should not post a notification when every student scheduled today already has a report`() =
        runTest {
            // Given
            val todaysReport =
                DailyReport(id = 1L, date = today, studentId = aliceId, moodLevel = 4, focusLevel = 4, socialInteractions = 4)
            startTestKoin(scheduleSlots = listOf(aliceScheduledToday), reports = listOf(todaysReport))
            val worker = TestListenableWorkerBuilder<DailyReportReminderWorker>(RuntimeEnvironment.getApplication()).build()

            // When
            val result = worker.doWork()

            // Then
            assertEquals(Result.success(), result)
            val notificationManager = shadowOf(RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java))
            assertTrue(notificationManager.allNotifications.isEmpty())
        }

    @Test
    fun `should not post a notification when no student is scheduled today`() =
        runTest {
            // Given: an empty weekly schedule (e.g. a day off) — nobody
            // needs an observation today, so the worker must stay quiet
            // even though no report exists for anyone.
            startTestKoin(scheduleSlots = emptyList(), reports = emptyList())
            val worker = TestListenableWorkerBuilder<DailyReportReminderWorker>(RuntimeEnvironment.getApplication()).build()

            // When
            val result = worker.doWork()

            // Then
            assertEquals(Result.success(), result)
            val notificationManager = shadowOf(RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java))
            assertTrue(notificationManager.allNotifications.isEmpty())
        }

    private fun startTestKoin(
        scheduleSlots: List<ScheduleSlot>,
        reports: List<DailyReport>,
    ) {
        startKoin {
            modules(
                module {
                    single<ScheduleSlotRepository> { FakeScheduleSlotRepository(scheduleSlots) }
                    single<DailyReportRepository> { FakeDailyReportRepository(reports) }
                    single { HasIncompleteDailyReportsUseCase(get(), get()) }
                    single { DailyReminderNotifier(RuntimeEnvironment.getApplication()) }
                },
            )
        }
    }
}
