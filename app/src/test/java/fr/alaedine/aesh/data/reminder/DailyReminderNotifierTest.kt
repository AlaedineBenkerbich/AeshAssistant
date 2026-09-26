package fr.alaedine.aesh.data.reminder

import android.Manifest
import android.app.NotificationManager
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [DailyReminderNotifier] under Robolectric so the real
 * [NotificationManager] APIs (channel creation, posting) are available on
 * the local JVM. See [fr.alaedine.aesh.data.local.dao.ScheduleSlotDaoTest]
 * for why [Config.application] swaps out [fr.alaedine.aesh.AeshApplication].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class DailyReminderNotifierTest {

    @Test
    fun `should post a reminder notification on its own channel when permission is granted`() {
        // Given
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = DailyReminderNotifier(context)

        // When
        notifier.notifyMissingReports()

        // Then
        val notificationManager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertEquals(1, notificationManager.allNotifications.size)
        assertEquals(
            DailyReminderNotifier.CHANNEL_ID,
            notificationManager.allNotifications.single().channelId,
        )
    }

    @Test
    fun `should not post a notification when the notification permission is denied`() {
        // Given
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifier = DailyReminderNotifier(context)

        // When
        notifier.notifyMissingReports()

        // Then
        val notificationManager = shadowOf(context.getSystemService(NotificationManager::class.java))
        assertTrue(notificationManager.allNotifications.isEmpty())
    }
}

